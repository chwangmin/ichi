<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { settingsApi, type StorageStatus } from '@/api/settings'
import { entriesApi } from '@/api/entries'
import { useHomeLocation } from '@/composables/useHomeLocation'
import { searchPlaces, type PlaceResult } from '@/composables/usePlaceSearch'
import { useWeatherBg } from '@/composables/useWeatherBg'
import { useTheme, type ThemeMode } from '@/composables/useTheme'
import LoadingState from '@/components/LoadingState.vue'
import InlineSpinner from '@/components/InlineSpinner.vue'

const auth = useAuthStore()
const router = useRouter()

const status = ref<StorageStatus | null>(null)
const loadingStatus = ref(true)
const statusError = ref<string | null>(null)

onMounted(async () => {
  try {
    status.value = await settingsApi.storage()
  } catch (e) {
    statusError.value = e instanceof Error ? e.message : '저장소 상태를 불러오지 못했습니다.'
  } finally {
    loadingStatus.value = false
  }
})

function fmtBytes(n: number | null): string {
  if (n == null) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(v >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}

const usagePercent = computed(() => {
  const s = status.value
  if (!s || !s.usageBytes || !s.limitBytes) return 0
  return Math.min(100, Math.round((s.usageBytes / s.limitBytes) * 100))
})

const avatarInitial = computed(() => (auth.user?.name ?? auth.user?.email ?? '나').trim().charAt(0))

// Drive 복원: DB 에 없는데 Drive 에 남아있는 일기를 다시 메타로 가져온다.
const restoreBusy = ref(false)
const restoreMsg = ref<string | null>(null)
const restoreError = ref<string | null>(null)

async function onRestore() {
  if (restoreBusy.value) return
  restoreBusy.value = true
  restoreMsg.value = null
  restoreError.value = null
  try {
    // 1) 미리보기: 복원 대상이 몇 건인지 먼저 확인
    const preview = await entriesApi.restorePreview()
    if (preview.restored === 0) {
      restoreMsg.value = `복원할 일기가 없어요. (Drive 일기 ${preview.scanned}개 모두 이미 있어요.)`
      return
    }
    // 2) 실행
    const result = await entriesApi.restore()
    restoreMsg.value = `${result.restored}개의 일기를 복원했어요.`
    // 일기 수 갱신 (가능하면 새로 조회)
    try {
      status.value = await settingsApi.storage()
    } catch {
      /* 상태 갱신 실패는 무시 */
    }
  } catch (e) {
    restoreError.value = e instanceof Error ? e.message : '복원에 실패했습니다.'
  } finally {
    restoreBusy.value = false
  }
}

// 날씨 배경 on/off
const { enabled: weatherBgOn, toggle: toggleWeatherBg } = useWeatherBg()

// 화면 테마 (자동 / 라이트 / 다크). 다크/라이트는 날씨보다 우선.
const { mode: themeMode, setMode: setThemeMode } = useTheme()
const themeOptions: { value: ThemeMode; label: string; icon: string }[] = [
  { value: 'auto', label: '자동', icon: 'brightness_auto' },
  { value: 'light', label: '라이트', icon: 'light_mode' },
  { value: 'dark', label: '다크', icon: 'dark_mode' },
]

// 날씨용 '내 장소'
const {
  home,
  saving: locSaving,
  error: locError,
  captureCurrent,
  setHome,
  clearHome,
} = useHomeLocation()

// 장소 검색
const searchQuery = ref('')
const searchResults = ref<PlaceResult[]>([])
const searching = ref(false)
const searched = ref(false)

async function runSearch() {
  const q = searchQuery.value.trim()
  if (q.length < 2) {
    searchResults.value = []
    searched.value = false
    return
  }
  searching.value = true
  try {
    searchResults.value = await searchPlaces(q)
  } finally {
    searching.value = false
    searched.value = true
  }
}

function chooseResult(r: PlaceResult) {
  setHome({ lat: r.lat, lng: r.lng, placeName: r.label })
  searchQuery.value = ''
  searchResults.value = []
  searched.value = false
}

async function onLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="settings">
    <h1>설정</h1>

    <section class="block">
      <h2>계정</h2>
      <div class="account">
        <div class="avatar">{{ avatarInitial }}</div>
        <div class="account-info">
          <div class="name">{{ auth.user?.name ?? '이름 없음' }}</div>
          <div class="email">{{ auth.user?.email ?? '—' }}</div>
        </div>
        <button class="logout" @click="onLogout">로그아웃</button>
      </div>
    </section>

    <section class="block">
      <h2>저장소</h2>

      <LoadingState v-if="loadingStatus" compact />
      <div v-else-if="statusError" class="muted">{{ statusError }}</div>

      <template v-else-if="status">
        <div class="row">
          <span class="label">연결된 Drive</span>
          <span class="value status" :class="{ ok: status.connected }">
            <span class="mi">{{ status.connected ? 'cloud_done' : 'cloud_off' }}</span>
            {{ status.connected ? '연결됨' : '연결 안 됨' }}
          </span>
        </div>

        <div class="row">
          <span class="label">기록한 일기</span>
          <span class="value">{{ status.entryCount }}개</span>
        </div>

        <template v-if="status.connected && status.limitBytes">
          <div class="row">
            <span class="label">Drive 사용량</span>
            <span class="value"
              >{{ fmtBytes(status.usageBytes) }} / {{ fmtBytes(status.limitBytes) }}</span
            >
          </div>
          <div class="usage-bar" :aria-label="`${usagePercent}% 사용`">
            <div class="usage-fill" :style="{ width: usagePercent + '%' }"></div>
          </div>
        </template>

        <template v-if="status.connected">
          <div class="row restore-row">
            <span class="label">
              일기 복원
              <span class="sublabel">Drive 에만 남은 일기를 다시 불러와요</span>
            </span>
            <button class="restore-btn" :disabled="restoreBusy" @click="onRestore">
              <InlineSpinner v-if="restoreBusy" :size="13" />
              {{ restoreBusy ? '복원 중…' : '복원' }}
            </button>
          </div>
          <p v-if="restoreMsg" class="hint ok">{{ restoreMsg }}</p>
          <p v-if="restoreError" class="hint err">{{ restoreError }}</p>
        </template>

        <p v-if="!status.connected" class="hint">
          Drive 권한이 없어요. 다시 로그인하면 일기 저장소가 연결됩니다.
        </p>
      </template>
    </section>

    <section class="block">
      <h2>화면 테마</h2>
      <div class="row">
        <span class="label">
          테마
          <span class="sublabel">자동은 밤 날씨일 때 어두워져요</span>
        </span>
        <div class="seg" role="radiogroup" aria-label="화면 테마">
          <button
            v-for="opt in themeOptions"
            :key="opt.value"
            class="seg-btn"
            role="radio"
            :aria-checked="themeMode === opt.value"
            :class="{ on: themeMode === opt.value }"
            @click="setThemeMode(opt.value)"
          >
            <span class="mi">{{ opt.icon }}</span>
            {{ opt.label }}
          </button>
        </div>
      </div>
    </section>

    <section class="block">
      <h2>날씨 배경</h2>
      <div class="row">
        <span class="label">
          움직이는 날씨 배경
          <span class="sublabel">비·눈·구름·해/달 효과</span>
        </span>
        <button
          class="switch"
          role="switch"
          :aria-checked="weatherBgOn"
          :class="{ on: weatherBgOn }"
          @click="toggleWeatherBg"
        >
          <span class="knob"></span>
        </button>
      </div>
    </section>

    <section class="block">
      <h2>날씨 위치</h2>
      <p class="hint" style="margin-top: 0; margin-bottom: 12px">
        상단바 날씨에 쓸 위치예요. 설정하면 실시간 위치 대신 이 장소를 사용합니다.
      </p>

      <div class="row">
        <span class="label">현재 설정</span>
        <span class="value loc-value">
          <span class="mi">place</span>
          {{ home ? home.placeName || '좌표만 저장됨' : '설정 안 됨 (실시간 위치 / 서울)' }}
        </span>
      </div>

      <p v-if="locError" class="hint err">{{ locError }}</p>

      <div class="loc-search">
        <label class="search-box">
          <span class="mi">search</span>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="도로명 또는 지번 주소 (예: 서울시 강남구 봉은사로 524)"
            aria-label="날씨 위치 검색"
            @keydown.enter.prevent="runSearch"
          />
          <span v-if="searching" class="dot" aria-hidden="true"></span>
        </label>
        <button
          class="search-btn"
          :disabled="searching || searchQuery.trim().length < 2"
          @click="runSearch"
        >
          검색
        </button>

        <ul v-if="searchResults.length" class="results" role="listbox">
          <li v-for="(r, i) in searchResults" :key="i">
            <button class="result" @click="chooseResult(r)">
              <span class="mi">place</span>
              <span class="result-label">{{ r.label }}</span>
            </button>
          </li>
        </ul>
        <p v-else-if="searched && !searching" class="hint">
          검색 결과가 없어요. 도로명 주소나 지번 주소를 조금 더 정확히 입력해 주세요.
        </p>
      </div>

      <div class="loc-actions">
        <button class="loc-btn" :disabled="locSaving" @click="captureCurrent">
          <InlineSpinner v-if="locSaving" :size="15" />
          <span v-else class="mi">my_location</span>
          {{ locSaving ? '가져오는 중…' : '현재 위치로 설정' }}
        </button>
        <button v-if="home" class="loc-btn ghost" :disabled="locSaving" @click="clearHome">
          지우기
        </button>
      </div>
    </section>

    <!-- §6-7: 비활성 placeholder -->
    <section class="block future">
      <h2>클라우드 서비스 <span class="tag">예정</span></h2>
      <p class="hint">나중에 종단간(E2E) 암호화와 저장소 옵션이 여기에 들어갑니다.</p>
    </section>
  </div>
</template>

<style scoped>
.settings {
  max-width: 640px;
  margin: 0 auto;
}
h1 {
  font-family: 'Nanum Myeongjo', serif;
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 24px;
}
.block {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 20px;
  margin-bottom: 16px;
}
h2 {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--ink-faint);
  text-transform: uppercase;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tag {
  font-size: 10px;
  background: var(--line);
  color: var(--ink-soft);
  padding: 2px 7px;
  border-radius: 20px;
  font-weight: 500;
  letter-spacing: 0;
}
.account {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7c9cbf, #5a7a9e);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 19px;
  flex-shrink: 0;
}
.account-info .name {
  font-size: 15px;
  font-weight: 500;
  color: var(--ink);
}
.account-info .email {
  font-size: 13px;
  color: var(--ink-faint);
  margin-top: 2px;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
}
.label {
  color: var(--ink-soft);
}
.sublabel {
  display: block;
  font-size: 11.5px;
  color: var(--ink-faint);
  margin-top: 2px;
}
.switch {
  flex-shrink: 0;
  width: 46px;
  height: 26px;
  border: none;
  border-radius: 999px;
  background: var(--line);
  cursor: pointer;
  padding: 0;
  position: relative;
  transition: background 0.18s;
}
.switch.on {
  background: var(--seal);
}
.switch .knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  transition: transform 0.18s;
}
.switch.on .knob {
  transform: translateX(20px);
}
/* 화면 테마 세그먼트 (자동/라이트/다크) */
.seg {
  flex-shrink: 0;
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: var(--line);
  border-radius: 10px;
}
.seg-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: none;
  cursor: pointer;
  padding: 6px 11px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--ink-soft);
  transition:
    background 0.15s,
    color 0.15s;
}
.seg-btn .mi {
  font-size: 16px;
}
.seg-btn.on {
  background: var(--card);
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}
.value {
  color: var(--ink);
  font-weight: 500;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-soft);
}
.status.ok {
  color: #3a7d44;
}
.status .mi {
  font-size: 17px;
}
.usage-bar {
  height: 7px;
  background: var(--line-soft);
  border-radius: 6px;
  overflow: hidden;
  margin-top: 6px;
}
.usage-fill {
  height: 100%;
  background: var(--seal);
  border-radius: 6px;
  transition: width 0.3s;
}
.muted {
  color: var(--ink-faint);
  font-size: 13.5px;
  padding: 4px 0;
}
.hint {
  font-size: 12.5px;
  color: var(--ink-faint);
  line-height: 1.6;
  margin-top: 4px;
}
.future {
  opacity: 0.75;
}
.logout {
  margin-left: auto;
  flex-shrink: 0;
  border: 1px solid var(--line);
  background: none;
  color: var(--seal);
  padding: 9px 16px;
  border-radius: 9px;
  cursor: pointer;
  font-size: 13.5px;
}
.logout:hover {
  background: var(--seal-soft);
}

.loc-value {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 400;
  color: var(--ink-soft);
  text-align: right;
}
.loc-value .mi {
  font-size: 17px;
  color: var(--seal);
}
.hint.err {
  color: var(--seal);
}
.hint.ok {
  color: #3a7d44;
}
.restore-row {
  align-items: flex-start;
}
.restore-btn {
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: none;
  color: var(--ink-soft);
  padding: 9px 16px;
  border-radius: 9px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13.5px;
}
.restore-btn:hover:not(:disabled) {
  background: var(--hover);
  border-color: var(--seal);
  color: var(--seal);
}
.restore-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}
.loc-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.loc-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: none;
  color: var(--ink);
  padding: 9px 16px;
  border-radius: 9px;
  cursor: pointer;
  font-size: 13.5px;
  font-family: inherit;
}
.loc-btn .mi {
  font-size: 18px;
  color: var(--seal);
}
.loc-btn:hover:not(:disabled) {
  background: var(--hover);
}
.loc-btn.ghost {
  color: var(--ink-soft);
}
.loc-btn.ghost .mi {
  color: inherit;
}
.loc-btn:disabled {
  opacity: 0.55;
  cursor: wait;
}

.loc-search {
  margin-top: 12px;
}
.search-box {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 14px;
  background: var(--sidebar);
  border-radius: 10px;
  color: var(--ink-faint);
}
.loc-search {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
.loc-search .hint,
.loc-search .results {
  grid-column: 1 / -1;
}
.search-btn {
  border: 1px solid var(--line);
  background: var(--seal);
  color: #fff;
  border-radius: 10px;
  padding: 0 16px;
  font-family: inherit;
  font-size: 13.5px;
  cursor: pointer;
}
.search-btn:disabled {
  opacity: 0.5;
  cursor: wait;
}
.search-box .mi {
  font-size: 20px;
}
.search-box input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-family: inherit;
  font-size: 14px;
  color: var(--ink);
}
.search-box input::placeholder {
  color: var(--ink-faint);
}
.search-box .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--seal);
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .search-box .dot {
    animation: none;
  }
}
.results {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.results li + li {
  border-top: 1px solid var(--line-soft);
}
.result {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: none;
  background: none;
  cursor: pointer;
  padding: 10px 12px;
  font-family: inherit;
  font-size: 13.5px;
  color: var(--ink);
}
.result:hover {
  background: var(--hover);
}
.result .mi {
  font-size: 17px;
  color: var(--seal);
  flex-shrink: 0;
}
.result-label {
  line-height: 1.4;
}
</style>
