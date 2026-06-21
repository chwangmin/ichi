<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EditNoteModal from '@/components/EditNoteModal.vue'
import LoadingState from '@/components/LoadingState.vue'
import NoteComposer from '@/components/NoteComposer.vue'
import NoteCard from '@/components/NoteCard.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  entriesApi,
  type EntryDetail,
  type EntryListItem,
  type UpdateEntryPayload,
} from '@/api/entries'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { randomColor } from '@/composables/useRandomColor'
import type { CardColor, NoteEntry } from '@/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const highlightId = ref<string | null>(null)

const entries = ref<NoteEntry[]>([])
const loading = ref(true)
const saving = ref(false) // 완료 → 서버 저장(Drive 업로드) 진행 중
const error = ref<string | null>(null)
const driveError = ref(false) // Drive 권한 부족 → 재로그인 안내
const editing = ref<EntryDetail | null>(null)
const editLoading = ref(false)
const savingEntry = ref(false) // 모달 닫힌 뒤에도 Drive 저장 끝까지 로딩 표시
const pendingDeleteId = ref<string | null>(null)

function toNote(e: EntryListItem): NoteEntry {
  return {
    id: e.id,
    entryDate: e.entryDate,
    preview: e.preview,
    pinned: e.pinned,
    color: e.color,
    lat: e.lat,
    lng: e.lng,
    placeName: e.placeName,
    thumbMediaId: e.thumbMediaId,
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    entries.value = (await entriesApi.list()).map(toNote)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 갤러리에서 ?entry=<id> 로 들어오면 해당 카드로 스크롤 + 잠깐 강조
async function focusEntry(id: string | undefined) {
  if (!id) return
  // 목록 로딩이 끝날 때까지 대기
  if (loading.value) await load()
  // 대상이 아직 점진 렌더 범위 밖이면, 보이도록 표시 개수를 늘린다.
  const idx = others.value.findIndex((e) => e.id === id)
  if (idx >= visibleCount.value) {
    visibleCount.value = Math.ceil((idx + 1) / PAGE) * PAGE
  }
  await nextTick()
  const el = document.querySelector(`[data-entry-id="${id}"]`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    highlightId.value = id
    setTimeout(() => (highlightId.value = null), 1600)
  }
}
watch(
  () => route.query.entry,
  (v) => focusEntry(v as string | undefined),
  { immediate: true },
)

// 모달 위치 칩 → 아틀라스에서 그 위치 보기
function openOnMap(id: string) {
  editing.value = null
  router.push({ name: 'atlas', query: { entry: id } })
}

async function addEntry(payload: {
  html: string
  mediaIds: string[]
  lat?: number
  lng?: number
  placeName?: string
}) {
  error.value = null
  saving.value = true
  try {
    const created = await entriesApi.create({
      html: payload.html,
      mediaIds: payload.mediaIds,
      color: randomColor(),
      lat: payload.lat,
      lng: payload.lng,
      placeName: payload.placeName,
    })
    entries.value.unshift(toNote(created))
  } catch (e) {
    if (e instanceof ApiError && e.code === 'drive_scope_missing') {
      driveError.value = true
    }
    error.value = e instanceof Error ? e.message : '저장에 실패했습니다.'
  } finally {
    saving.value = false
  }
}

async function openEntry(id: string) {
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

async function updateEntry(id: string, payload: UpdateEntryPayload) {
  error.value = null
  // 모달은 켜둔 채로 그 위에 '저장 중…' 오버레이 표시 → 성공해야 닫는다.
  savingEntry.value = true
  try {
    const updated = toNote(await entriesApi.updateContent(id, payload))
    const idx = entries.value.findIndex((e) => e.id === id)
    if (idx >= 0) entries.value.splice(idx, 1, updated)
    editing.value = null
  } catch (e) {
    if (e instanceof ApiError && e.code === 'drive_scope_missing') {
      driveError.value = true
    }
    error.value = e instanceof Error ? e.message : '수정에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}

async function reLogin() {
  await auth.logout()
  router.replace({ name: 'login' })
}

async function togglePin(id: string) {
  const e = entries.value.find((x) => x.id === id)
  if (!e) return
  const prev = e.pinned
  e.pinned = !e.pinned // 낙관적
  try {
    await entriesApi.togglePin(id)
  } catch {
    e.pinned = prev
  }
}

async function changeColor(id: string, color: CardColor) {
  const e = entries.value.find((x) => x.id === id)
  if (!e) return
  const prev = e.color
  e.color = color
  try {
    await entriesApi.changeColor(id, color)
  } catch {
    e.color = prev
  }
}

// 삭제는 비가역 → 먼저 확인 모달을 띄운다.
function requestDelete(id: string) {
  pendingDeleteId.value = id
}

function cancelDelete() {
  pendingDeleteId.value = null
}

async function confirmDelete() {
  const id = pendingDeleteId.value
  pendingDeleteId.value = null
  if (!id) return
  const idx = entries.value.findIndex((x) => x.id === id)
  if (idx < 0) return
  const [removed] = entries.value.splice(idx, 1)
  try {
    await entriesApi.remove(id)
  } catch (e) {
    entries.value.splice(idx, 0, removed) // 롤백
    error.value = e instanceof Error ? e.message : '삭제에 실패했습니다.'
  }
}

const pinned = computed(() => entries.value.filter((e) => e.pinned))
const others = computed(() => entries.value.filter((e) => !e.pinned))
const hasAny = computed(() => entries.value.length > 0)

// ── 점진 렌더 (Google Keep 스타일 무한 스크롤) ──────────────────
// 핀이 아닌 '최근 기록' 목록을 한 번에 다 그리지 않고, 바닥에 닿을 때마다
// PAGE 만큼 더 보여준다. 백엔드는 전체를 주지만 DOM/이미지 과다 렌더를 막는다.
const PAGE = 40
const visibleCount = ref(PAGE)
const visibleOthers = computed(() => others.value.slice(0, visibleCount.value))
const hasMore = computed(() => visibleCount.value < others.value.length)

// 목록이 줄어들면(삭제·재로딩) 표시 개수도 함께 줄여 빈 sentinel 노출을 막는다.
watch(
  () => others.value.length,
  (len) => {
    if (visibleCount.value > Math.max(len, PAGE)) {
      visibleCount.value = Math.max(len, PAGE)
    }
  },
)

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting && hasMore.value) {
        visibleCount.value += PAGE
      }
    },
    { rootMargin: '600px' }, // 바닥에 닿기 전에 미리 다음 묶음 로드
  )
  // sentinel 은 hasMore 일 때만 렌더되므로, 등장/소멸에 맞춰 관찰을 붙였다 뗀다.
  watch(
    sentinel,
    (el, _prev, onCleanup) => {
      if (!el || !observer) return
      observer.observe(el)
      onCleanup(() => observer?.unobserve(el))
    },
    { immediate: true },
  )
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="canvas">
    <NoteComposer @submit="addEntry" />

    <div v-if="driveError" class="banner drive">
      <span>{{ error }}</span>
      <button class="relogin" @click="reLogin">다시 로그인</button>
    </div>
    <p v-else-if="error" class="banner">{{ error }}</p>

    <LoadingState v-if="loading" />

    <template v-else-if="hasAny || saving">
      <template v-if="pinned.length">
        <div class="section-label">고정됨</div>
        <div class="cards">
          <NoteCard
            v-for="e in pinned"
            :key="e.id"
            :entry="e"
            :data-entry-id="e.id"
            :highlight="highlightId === e.id"
            @open="openEntry"
            @toggle-pin="togglePin"
            @change-color="changeColor"
            @remove="requestDelete"
          />
        </div>
      </template>

      <div class="section-label">{{ '최근 기록' }}</div>
      <div class="cards">
        <!-- 완료 직후 서버 저장 동안 보여 줄 자리(스켈레톤). 새 카드가 들어올 위치에 표시. -->
        <div v-if="saving" class="skel-card" aria-live="polite">
          <div class="skel-spinner" aria-hidden="true"></div>
          <span class="skel-text">기록을 저장하는 중…</span>
          <div class="skel-line" aria-hidden="true"></div>
          <div class="skel-line short" aria-hidden="true"></div>
        </div>
        <NoteCard
          v-for="e in visibleOthers"
          :key="e.id"
          :entry="e"
          :data-entry-id="e.id"
          :highlight="highlightId === e.id"
          @open="openEntry"
          @toggle-pin="togglePin"
          @change-color="changeColor"
          @remove="requestDelete"
        />
      </div>
      <!-- 무한 스크롤 감지점: 바닥 근처에 닿으면 다음 묶음을 더 보여준다 -->
      <div v-if="hasMore" ref="sentinel" class="load-more" aria-hidden="true">
        <div class="load-spinner"></div>
      </div>
    </template>

    <div v-else class="empty">
      <span class="mi">edit_note</span>
      <p class="empty-title">아직 기록이 없어요</p>
      <p class="empty-sub">위 입력창에 오늘 하루를 한 줄 끄적여보세요.</p>
    </div>

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
      @save="updateEntry"
      @view-on-map="openOnMap"
    />

    <ConfirmDialog
      v-if="pendingDeleteId"
      title="이 기록을 삭제할까요?"
      message="삭제하면 Drive에 저장된 본문과 사진도 함께 지워지며 되돌릴 수 없어요."
      confirm-label="삭제"
      cancel-label="취소"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />
  </div>
</template>

<style scoped>
.canvas {
  max-width: 1080px;
  margin: 0 auto;
}
.banner {
  max-width: 600px;
  margin: 0 auto 16px;
  color: var(--seal);
  background: var(--seal-soft);
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}
.banner.drive {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
.relogin {
  flex-shrink: 0;
  border: 1px solid var(--seal);
  background: #fff;
  color: var(--seal);
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  cursor: pointer;
}
.relogin:hover {
  background: var(--seal);
  color: #fff;
}
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
  animation: skel-spin 0.7s linear infinite;
}
@media (prefers-reduced-motion: reduce) {
  .ms-spinner {
    animation: none;
  }
}
.section-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--ink-faint);
  text-transform: uppercase;
  margin: 0 4px 14px;
}
.cards {
  column-gap: 16px;
  margin-bottom: 18px;
}
@media (min-width: 680px) {
  .cards {
    column-count: 2;
  }
}
@media (min-width: 980px) {
  .cards {
    column-count: 3;
  }
}
@media (min-width: 1280px) {
  .cards {
    column-count: 4;
  }
}

/* 저장 중 스켈레톤 카드 (완료 → Drive 업로드 동안) */
.skel-card {
  break-inside: avoid;
  background: var(--card);
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  margin-bottom: 16px;
  padding: 16px 17px 18px;
}
.skel-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  display: inline-block;
  vertical-align: middle;
  animation: skel-spin 0.7s linear infinite;
}
.skel-text {
  margin-left: 8px;
  font-size: 12.5px;
  color: var(--ink-soft);
  vertical-align: middle;
}
.skel-line {
  height: 10px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--line-soft) 25%, var(--hover) 37%, var(--line-soft) 63%);
  background-size: 400% 100%;
  animation: skel-shimmer 1.3s ease infinite;
  margin-top: 14px;
}
.skel-line.short {
  width: 62%;
  margin-top: 9px;
}
@keyframes skel-spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes skel-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}
/* 무한 스크롤 로딩 감지점 */
.load-more {
  display: flex;
  justify-content: center;
  padding: 8px 0 28px;
}
.load-spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: skel-spin 0.7s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .skel-spinner {
    animation: none;
  }
  .skel-line {
    animation: none;
  }
  .load-spinner {
    animation: none;
  }
}

.empty {
  text-align: center;
  color: var(--ink-faint);
  padding: 60px 20px;
}
.empty .mi {
  font-size: 48px;
  opacity: 0.5;
}
.empty-title {
  margin-top: 14px;
  font-size: 16px;
  color: var(--ink-soft);
  font-weight: 500;
}
.empty-sub {
  margin-top: 6px;
  font-size: 13.5px;
}
</style>
