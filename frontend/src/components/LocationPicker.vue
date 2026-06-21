<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import InlineSpinner from '@/components/InlineSpinner.vue'
import { resolvePlaceName } from '@/composables/usePlaceName'
import { searchPlaces, type PlaceResult } from '@/composables/usePlaceSearch'

export interface EntryLocation {
  lat: number
  lng: number
  placeName?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: EntryLocation | null
  disabled?: boolean
  placement?: 'above' | 'below'
}>(), {
  placement: 'below',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: EntryLocation | null): void
  (e: 'busy-change', value: boolean): void
  (e: 'error', value: string | null): void
}>()

const open = ref(false)
const locating = ref(false)
const searching = ref(false)
const searched = ref(false)
const query = ref('')
const results = ref<PlaceResult[]>([])
const localError = ref<string | null>(null)

const busy = computed(() => locating.value || searching.value)

watch(busy, (value) => emit('busy-change', value), { immediate: true })

function clearError() {
  localError.value = null
  emit('error', null)
}

function setError(message: string) {
  localError.value = message
  emit('error', message)
}

function togglePanel() {
  if (props.disabled || busy.value) return
  open.value = !open.value
  if (open.value) clearError()
}

function clearLocation() {
  emit('update:modelValue', null)
  open.value = false
  clearError()
}

function useCurrentLocation() {
  if (!navigator.geolocation) {
    setError('이 기기에서 위치를 사용할 수 없습니다.')
    return
  }

  locating.value = true
  clearError()
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      const lat = pos.coords.latitude
      const lng = pos.coords.longitude
      try {
        const placeName = await resolvePlaceName(lat, lng)
        emit('update:modelValue', { lat, lng, placeName })
        open.value = false
      } catch {
        emit('update:modelValue', { lat, lng, placeName: null })
        open.value = false
      } finally {
        locating.value = false
      }
    },
    () => {
      setError('위치 권한이 거부되었어요.')
      locating.value = false
    },
    { enableHighAccuracy: true, timeout: 8000 },
  )
}

async function runSearch() {
  const q = query.value.trim()
  if (q.length < 2) {
    results.value = []
    searched.value = false
    return
  }

  searching.value = true
  clearError()
  try {
    results.value = await searchPlaces(q)
  } finally {
    searching.value = false
    searched.value = true
  }
}

function chooseResult(result: PlaceResult) {
  emit('update:modelValue', {
    lat: result.lat,
    lng: result.lng,
    placeName: result.label,
  })
  query.value = result.label
  results.value = []
  searched.value = false
  open.value = false
}
</script>

<template>
  <div class="location-picker">
    <button
      class="lp-tool"
      :class="{ on: modelValue || open }"
      :disabled="disabled || busy"
      title="위치 선택"
      type="button"
      @click="togglePanel"
    >
      <span class="mi">location_on</span>
    </button>

    <div v-if="open" :class="['lp-panel', `is-${placement}`]">
      <button class="lp-action" type="button" :disabled="busy" @click="useCurrentLocation">
        <InlineSpinner v-if="locating" :size="14" />
        <span v-else class="mi">my_location</span>
        <span>{{ locating ? '위치 확인 중...' : '현재 위치 사용' }}</span>
      </button>

      <form class="lp-search" @submit.prevent="runSearch">
        <label>
          <span class="mi">search</span>
          <input
            v-model="query"
            type="text"
            placeholder="주소 검색"
            aria-label="기록 위치 주소 검색"
          />
        </label>
        <button type="submit" :disabled="searching || query.trim().length < 2">
          <InlineSpinner v-if="searching" :size="13" light />
          {{ searching ? '검색 중...' : '검색' }}
        </button>
      </form>

      <ul v-if="results.length" class="lp-results">
        <li v-for="(result, i) in results" :key="i">
          <button type="button" @click="chooseResult(result)">
            <span class="mi">place</span>
            <span>{{ result.label }}</span>
          </button>
        </li>
      </ul>
      <p v-else-if="searched && !searching" class="lp-empty">
        검색 결과가 없어요. 도로명 주소나 지번 주소를 조금 더 정확히 입력해 주세요.
      </p>

      <button v-if="modelValue" class="lp-remove" type="button" @click="clearLocation">
        <span class="mi">location_off</span>
        <span>위치 제거</span>
      </button>
      <p v-if="localError" class="lp-error">{{ localError }}</p>
    </div>
  </div>
</template>

<style scoped>
.location-picker {
  position: relative;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
}
.lp-tool {
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
.lp-tool:hover:not(:disabled),
.lp-tool.on {
  background: var(--seal-soft);
  color: var(--seal);
}
.lp-tool:disabled {
  opacity: 0.55;
  cursor: wait;
}
.lp-tool .mi {
  font-size: 20px;
}
.lp-panel {
  position: absolute;
  left: 0;
  z-index: 20;
  width: min(320px, calc(100vw - 32px));
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);
}
.lp-panel.is-below {
  top: 46px;
}
.lp-panel.is-above {
  bottom: 46px;
}
.lp-action,
.lp-remove,
.lp-results button {
  width: 100%;
  border: none;
  background: none;
  color: var(--ink);
  font: inherit;
  cursor: pointer;
  text-align: left;
}
.lp-action,
.lp-remove {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 9px;
}
.lp-action {
  border: 1px solid var(--line-soft);
  background: var(--seal-soft);
  color: var(--seal);
  font-weight: 600;
}
.lp-action:hover:not(:disabled),
.lp-remove:hover,
.lp-results button:hover {
  background: var(--hover);
}
.lp-action:hover:not(:disabled) {
  background: color-mix(in srgb, var(--seal-soft) 72%, var(--seal));
}
.lp-action:disabled {
  opacity: 0.6;
  cursor: wait;
}
.lp-action .mi,
.lp-remove .mi,
.lp-results .mi {
  flex-shrink: 0;
  color: var(--seal);
  font-size: 17px;
}
:global(.night .location-picker .lp-action),
:global(.night .location-picker .lp-action .mi) {
  color: color-mix(in srgb, var(--seal) 52%, var(--ink-on-color));
}
.lp-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 8px;
}
.lp-search label {
  min-width: 0;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 0 10px;
  color: var(--ink-faint);
}
.lp-search input {
  min-width: 0;
  flex: 1;
  border: none;
  outline: none;
  background: none;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}
.lp-search button {
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: 9px;
  background: var(--seal);
  color: #fff;
  padding: 0 12px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.lp-search button:disabled {
  opacity: 0.55;
  cursor: wait;
}
.lp-results {
  list-style: none;
  margin: 9px 0 0;
  padding: 0;
  border: 1px solid var(--line-soft);
  border-radius: 9px;
  overflow: hidden;
}
.lp-results li + li {
  border-top: 1px solid var(--line-soft);
}
.lp-results button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  font-size: 13px;
}
.lp-results button span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lp-empty,
.lp-error {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
}
.lp-empty {
  color: var(--ink-faint);
}
.lp-error {
  color: var(--seal);
}
.lp-remove {
  margin-top: 8px;
  color: var(--seal);
}
</style>
