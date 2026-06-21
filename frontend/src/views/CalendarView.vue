<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import EditNoteModal from '@/components/EditNoteModal.vue'
import LoadingState from '@/components/LoadingState.vue'
import {
  entriesApi,
  type CreateEntryPayload,
  type EntryDetail,
  type EntryListItem,
  type UpdateEntryPayload,
} from '@/api/entries'
import { useViewOnMap } from '@/composables/useViewOnMap'
import { randomColor } from '@/composables/useRandomColor'

const { viewOnMap } = useViewOnMap()

const WD = ['일', '월', '화', '수', '목', '금', '토']

const today = new Date()
const cursor = ref({ year: today.getFullYear(), month: today.getMonth() }) // month: 0-11
const selected = ref<string | null>(isoOf(today)) // YYYY-MM-DD
const entries = ref<EntryListItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function pad(n: number) {
  return String(n).padStart(2, '0')
}
function isoOf(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function isoOfYMD(y: number, m: number, day: number) {
  return `${y}-${pad(m + 1)}-${pad(day)}`
}

const monthLabel = computed(() => `${cursor.value.year}년 ${cursor.value.month + 1}월`)

// 달력 셀 (앞쪽 빈칸 + 날짜들)
const cells = computed(() => {
  const { year, month } = cursor.value
  const firstDay = new Date(year, month, 1).getDay() // 0=일
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const arr: ({ day: number; iso: string } | null)[] = []
  for (let i = 0; i < firstDay; i++) arr.push(null)
  for (let d = 1; d <= daysInMonth; d++) arr.push({ day: d, iso: isoOfYMD(year, month, d) })
  return arr
})

// 일기 있는 날짜 → 개수
const countByDate = computed(() => {
  const map: Record<string, number> = {}
  for (const e of entries.value) map[e.entryDate] = (map[e.entryDate] ?? 0) + 1
  return map
})

// 날짜별 일기 목록(색 바 표시용). Google 캘린더처럼 색 막대 + 내용으로 보여준다.
const eventsByDate = computed(() => {
  const map: Record<string, EntryListItem[]> = {}
  for (const e of entries.value) {
    ;(map[e.entryDate] ??= []).push(e)
  }
  return map
})

const selectedEntries = computed(() =>
  selected.value ? entries.value.filter((e) => e.entryDate === selected.value) : [],
)

async function loadMonth() {
  const { year, month } = cursor.value
  const from = isoOfYMD(year, month, 1)
  const to = isoOfYMD(year, month, new Date(year, month + 1, 0).getDate())
  loading.value = true
  error.value = null
  try {
    entries.value = await entriesApi.listBetween(from, to)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(loadMonth)
watch(cursor, loadMonth, { deep: true })

function prevMonth() {
  const m = cursor.value.month - 1
  cursor.value =
    m < 0 ? { year: cursor.value.year - 1, month: 11 } : { year: cursor.value.year, month: m }
}
function nextMonth() {
  const m = cursor.value.month + 1
  cursor.value =
    m > 11 ? { year: cursor.value.year + 1, month: 0 } : { year: cursor.value.year, month: m }
}
function goToday() {
  cursor.value = { year: today.getFullYear(), month: today.getMonth() }
  selected.value = isoOf(today)
}

const todayIso = isoOf(today)

function selectedLabel() {
  if (!selected.value) return ''
  const [y, m, d] = selected.value.split('-').map(Number)
  const wd = WD[new Date(y, m - 1, d).getDay()]
  return `${m}월 ${d}일 (${wd})`
}

function previewLabel(entry: EntryListItem) {
  return entry.preview || (entry.thumbMediaId ? '사진 기록' : '(빈 기록)')
}

function hasLocation(entry: EntryListItem) {
  return entry.lat != null && entry.lng != null
}

// 일기 선택 → 노트 화면으로 이동하지 않고 이 화면에서 모달로 연다 (노트와 동일 UX).
const editing = ref<EntryDetail | null>(null)
const editLoading = ref(false)
const savingEntry = ref(false) // 모달 닫힌 뒤에도 Drive 저장 끝까지 로딩 표시
// 선택한 날짜로 새 일기 작성 모드 (작성할 날짜 YYYY-MM-DD, null이면 작성 안 함)
const creatingDate = ref<string | null>(null)

async function openNote(id: string) {
  editLoading.value = true
  error.value = null
  try {
    editing.value = await entriesApi.get(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '기록을 불러오지 못했습니다.'
  } finally {
    editLoading.value = false
  }
}

async function saveEdit(id: string, payload: UpdateEntryPayload) {
  error.value = null
  // 모달은 켜둔 채로 그 위에 '저장 중…' 오버레이 표시 → 성공해야 닫는다.
  savingEntry.value = true
  try {
    const updated = await entriesApi.updateContent(id, payload)
    // 목록의 해당 항목을 갱신해 캘린더 점/패널 발췌가 최신화되도록 한다.
    const idx = entries.value.findIndex((e) => e.id === id)
    if (idx >= 0) entries.value.splice(idx, 1, updated)
    editing.value = null
  } catch (e) {
    error.value = e instanceof Error ? e.message : '수정에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}

// 선택한 날짜로 새 일기 작성 시작.
function startCreate() {
  if (!selected.value) return
  creatingDate.value = selected.value
}

// 새 일기 저장 (선택 날짜로 생성).
async function createEntry(payload: CreateEntryPayload) {
  error.value = null
  savingEntry.value = true
  try {
    // 노트 작성처럼 카드 색을 랜덤 부여 (이미 색이 있으면 유지)
    await entriesApi.create({ color: randomColor() ?? undefined, ...payload })
    creatingDate.value = null
    // 점/패널 발췌가 정확히 반영되도록 그 달을 다시 불러온다.
    await loadMonth()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '작성에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}
</script>

<template>
  <div class="cal">
    <header class="bar">
      <h1>{{ monthLabel }}</h1>
      <div class="nav">
        <button class="navbtn" aria-label="이전 달" @click="prevMonth">
          <span class="mi">chevron_left</span>
        </button>
        <button class="today" @click="goToday">오늘</button>
        <button class="navbtn" aria-label="다음 달" @click="nextMonth">
          <span class="mi">chevron_right</span>
        </button>
      </div>
    </header>

    <p v-if="error" class="banner">{{ error }}</p>

    <div class="weekdays">
      <span v-for="(w, i) in WD" :key="w" :class="{ sun: i === 0, sat: i === 6 }">{{ w }}</span>
    </div>

    <div class="grid">
      <template v-for="(cell, i) in cells" :key="i">
        <div v-if="!cell" class="cell empty"></div>
        <button
          v-else
          class="cell"
          :class="{
            today: cell.iso === todayIso,
            selected: cell.iso === selected,
            has: countByDate[cell.iso],
          }"
          @click="selected = cell.iso"
        >
          <span class="num">{{ cell.day }}</span>
          <span
            v-for="ev in (eventsByDate[cell.iso] || []).slice(0, 3)"
            :key="ev.id"
            class="event"
            :class="ev.color ? `c-${ev.color}` : 'c-default'"
            >{{ previewLabel(ev) }}</span
          >
          <span v-if="(eventsByDate[cell.iso] || []).length > 3" class="more">
            +{{ eventsByDate[cell.iso].length - 3 }}개 더
          </span>
        </button>
      </template>
    </div>

    <section class="day-panel">
      <div class="day-head">
        <h2>{{ selectedLabel() }}</h2>
        <button v-if="selected" class="add-btn" title="이 날에 일기 쓰기" @click="startCreate">
          <span class="mi">add</span>일기 쓰기
        </button>
      </div>
      <LoadingState v-if="loading" compact />
      <ul v-else-if="selectedEntries.length" class="list">
        <li v-for="e in selectedEntries" :key="e.id">
          <button class="item" @click="openNote(e.id)">
            <span v-if="e.color" class="chip" :class="`c-${e.color}`"></span>
            <span class="text">{{ previewLabel(e) }}</span>
            <span
              v-if="hasLocation(e)"
              class="loc"
              :title="e.placeName || '위치 기록됨'"
              aria-label="위치 있음"
            >
              <span class="mi">place</span>
            </span>
            <span v-if="e.pinned" class="mi pin">keep</span>
          </button>
        </li>
      </ul>
      <p v-else class="state muted">이 날의 기록이 없어요.</p>
    </section>

    <Teleport to="body">
      <div
        v-if="editLoading || savingEntry"
        class="modal-state"
        :class="{ 'over-modal': savingEntry }"
        role="status"
        aria-live="polite"
      >
        <div class="ms-card">
          <div class="ms-spinner" aria-hidden="true"></div>
          <span>{{ savingEntry ? '저장 중…' : '로딩 중…' }}</span>
        </div>
      </div>
    </Teleport>
    <EditNoteModal
      v-if="editing"
      :entry="editing"
      @close="editing = null"
      @save="saveEdit"
      @view-on-map="
        (id) => {
          editing = null
          viewOnMap(id)
        }
      "
    />
    <EditNoteModal
      v-else-if="creatingDate"
      :new-date="creatingDate"
      @close="creatingDate = null"
      @create="createEntry"
    />
  </div>
</template>

<style scoped>
.cal {
  max-width: 760px;
  margin: 0 auto;
  /* 날씨 배경 위에서 읽히도록 반투명 카드 패널로 감쌈 (노트 카드와 톤 맞춤) */
  background: color-mix(in srgb, var(--card) 78%, transparent);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 22px 24px;
}
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
h1 {
  font-family: 'Nanum Myeongjo', serif;
  font-size: 22px;
  font-weight: 800;
  /* body 상속색이 아니라 (밤이면 밝아지는) --ink 토큰을 따르게 명시 */
  color: var(--ink);
}
.nav {
  display: flex;
  align-items: center;
  gap: 6px;
}
.navbtn {
  width: 38px;
  height: 38px;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--ink-soft);
}
.navbtn:hover {
  background: var(--hover);
}
.today {
  border: 1px solid var(--line);
  background: none;
  color: var(--ink-soft);
  padding: 7px 14px;
  border-radius: 9px;
  font-size: 13px;
  cursor: pointer;
}
.today:hover {
  background: var(--hover);
}
.banner {
  color: var(--seal);
  background: var(--seal-soft);
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 14px;
}
.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  color: var(--ink-faint);
  margin-bottom: 6px;
}
.weekdays .sun {
  color: #c0584a;
}
.weekdays .sat {
  color: #5a7a9e;
}
.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.cell {
  min-height: 74px;
  border: 1px solid transparent;
  background: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 3px;
  padding: 6px 7px;
  position: relative;
  overflow: hidden;
  color: var(--ink);
  font-family: inherit;
  text-align: left;
  width: 100%;
}
.cell.empty {
  cursor: default;
}
.cell:not(.empty):hover {
  background: var(--hover);
}
.cell .num {
  font-size: 14px;
  line-height: 1;
}
.cell.today .num {
  width: 24px;
  height: 24px;
  margin: -3px 0 0 -3px;
  border-radius: 50%;
  background: var(--seal);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
}
.cell.selected {
  border-color: var(--seal);
}
/* Google 캘린더식 색 막대: 일기 하나 = 막대 하나 (색 + 내용 발췌) */
.event {
  width: 100%;
  font-size: 10.5px;
  line-height: 1.3;
  padding: 1px 5px;
  border-radius: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--ink);
}
.event.c-default {
  background: var(--seal-soft);
  color: var(--seal);
}
/* 색 칩은 밤에도 밝은 파스텔 유지 → 글자는 항상 어둡게 고정해 대비 확보 */
.event.c-y {
  background: var(--y);
  border: 1px solid var(--y-b);
  color: var(--ink-on-color);
}
.event.c-g {
  background: var(--g);
  border: 1px solid var(--g-b);
  color: var(--ink-on-color);
}
.event.c-b {
  background: var(--b);
  border: 1px solid var(--b-b);
  color: var(--ink-on-color);
}
.event.c-p {
  background: var(--p);
  border: 1px solid var(--p-b);
  color: var(--ink-on-color);
}
.more {
  font-size: 10px;
  color: var(--ink-faint);
  padding-left: 4px;
}
.day-panel {
  margin-top: 26px;
  border-top: 1px solid var(--line);
  padding-top: 18px;
}
.day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.day-panel h2 {
  font-family: 'Nanum Myeongjo', serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-soft);
}
.add-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: var(--seal);
  color: #fff;
  padding: 7px 13px 7px 10px;
  border-radius: 9px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.add-btn:hover {
  filter: brightness(0.96);
}
.add-btn .mi {
  font-size: 18px;
}
.state {
  color: var(--ink-faint);
  font-size: 13.5px;
  padding: 8px 0;
}
.state.muted {
  color: var(--ink-faint);
}
.list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  border: 1px solid var(--line);
  background: var(--card);
  border-radius: 10px;
  padding: 11px 14px;
  cursor: pointer;
  font-family: inherit;
}
.item:hover {
  border-color: var(--seal);
}
.chip {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.chip.c-y {
  background: var(--y-b);
}
.chip.c-g {
  background: var(--g-b);
}
.chip.c-b {
  background: var(--b-b);
}
.chip.c-p {
  background: var(--p-b);
}
.text {
  flex: 1;
  font-size: 14px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 노트 카드(NoteCard)의 위치 마크와 동일한 모양: 동그란 아이콘 칩 + 'place' 아이콘 */
.loc {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  padding: 5px;
  border-radius: 50%;
  color: var(--ink-soft);
  background: rgba(0, 0, 0, 0.05);
}
.loc .mi {
  font-size: 14px;
}
.pin {
  font-size: 16px;
  color: var(--seal);
}

/* 모달 여는 동안 로딩 표시 (노트 화면과 동일) */
.modal-state {
  position: fixed;
  inset: 0;
  z-index: 79;
  display: grid;
  place-items: center;
  background: rgba(42, 41, 38, 0.36);
}
/* 저장 중에는 모달(z-index 80) 위에 떠야 한다 */
.modal-state.over-modal {
  z-index: 90;
}
.ms-card {
  display: flex;
  align-items: center;
  gap: 11px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 10px 36px rgba(0, 0, 0, 0.2);
  padding: 16px 20px;
  font-size: 13.5px;
  color: var(--ink-soft);
}
.ms-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: cal-spin 0.7s linear infinite;
}
@keyframes cal-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .ms-spinner {
    animation: none;
  }
}
</style>
