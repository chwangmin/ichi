<script setup lang="ts">
import { computed, ref, onMounted, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EditNoteModal from '@/components/EditNoteModal.vue'
import LoadingState from '@/components/LoadingState.vue'
import InlineSpinner from '@/components/InlineSpinner.vue'
import {
  entriesApi,
  type AtlasPin,
  type CreateEntryPayload,
  type EntryDetail,
  type UpdateEntryPayload,
} from '@/api/entries'
import { thumbUrl } from '@/api/client'
import { clusterAtlasPins, type AtlasCluster } from '@/composables/useAtlasClusters'
import { loadGoogleMaps } from '@/composables/useGoogleMaps'
import { resolvePlaceName } from '@/composables/usePlaceName'
import { searchPlaces, type PlaceResult } from '@/composables/usePlaceSearch'
import { randomColor } from '@/composables/useRandomColor'

const route = useRoute()
const router = useRouter()

// 좌표를 못 구할 때(핀 없음/위치 실패 등) 쓰는 기본 위치: 문정역.
const DEFAULT_CENTER = { lat: 37.4853, lng: 127.1222 }

const mapEl = ref<HTMLDivElement | null>(null)
const status = ref<'loading' | 'ready' | 'no-key' | 'error' | 'empty'>('loading')
const errorMsg = ref('')
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const map = shallowRef<any>(null)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const markerCtor = shallowRef<any>(null)
const atlasPins = ref<AtlasPin[]>([])
const selectedCluster = ref<AtlasCluster | null>(null)
const previewPoint = ref<{ x: number; y: number } | null>(null)
const previewStyle = computed(() =>
  previewPoint.value
    ? {
        left: `${previewPoint.value.x}px`,
        top: `${previewPoint.value.y}px`,
      }
    : undefined,
)

const atlasSearchQuery = ref('')
const atlasSearchResults = ref<PlaceResult[]>([])
const atlasSearching = ref(false)
const atlasSearched = ref(false)

// 위치 이동 모드: 선택한 개별 핀을 드래그해 위치를 옮긴다.
// 클러스터(여러 핀)여도 목록에서 한 핀을 골라 그 핀만 옮긴다.
const moveMode = ref(false)
const movingPin = ref<AtlasPin | null>(null) // 지금 옮기는 개별 핀
const movingSaving = ref(false)
const moveError = ref<string | null>(null)
// 드래그로 옮긴 새 좌표(저장 전 임시)
const movedPos = ref<{ lat: number; lng: number } | null>(null)
const movePreviewPos = ref<{ lat: number; lng: number } | null>(null)
// cluster id → AdvancedMarkerElement 매핑 (이동/갱신 시 마커 직접 조작)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const markers = new Map<string, any>()
// 위치 이동 중 임시로 띄우는 단독 드래그 마커 (클러스터 마커와 별개)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const dragMarker = shallowRef<any>(null)
// 주소 검색 결과 위치를 보여주는 단독 마커
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const searchMarker = shallowRef<any>(null)
// 선택 미리보기를 핀 좌표 위에 붙이기 위한 지도 좌표 → 화면 픽셀 변환기
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let previewProjection: any = null

onMounted(async () => {
  let pins: AtlasPin[]
  try {
    pins = await entriesApi.atlas()
  } catch (e) {
    status.value = 'error'
    errorMsg.value = e instanceof Error ? e.message : '불러오지 못했습니다.'
    return
  }

  let g: typeof google
  try {
    g = await loadGoogleMaps()
  } catch (e) {
    status.value = (e as Error).message === 'NO_KEY' ? 'no-key' : 'error'
    if (status.value === 'error') errorMsg.value = '지도를 불러오지 못했습니다.'
    return
  }

  status.value = 'ready'
  await renderMap(g, pins)
  if (pins.length === 0) status.value = 'empty'

  // 다른 화면(모달 위치 칩 등)에서 ?entry=<id> 로 들어오면 그 기록 위치로 이동.
  focusEntry(route.query.entry as string | undefined)
})

// 이미 아틀라스에 머무는 동안 ?entry 가 바뀌면(예: 이 화면 모달에서 위치 칩 클릭) 그 위치로 이동.
watch(
  () => route.query.entry,
  (id) => {
    if (status.value === 'ready') focusEntry(id as string | undefined)
  },
)

// 해당 기록이 속한 클러스터로 지도를 옮기고 미리보기를 연다.
function focusEntry(id: string | undefined) {
  if (!id || !map.value) return
  const cluster = clusterAtlasPins(atlasPins.value).find((c) =>
    c.items.some((item) => item.id === id),
  )
  if (!cluster) return
  // 핀을 지도 한가운데로.
  map.value.panTo({ lat: cluster.lat, lng: cluster.lng })
  // 너무 멀리 잡혀 있으면 끌어당겨 확대 (이미 더 가까우면 그대로 둠)
  if ((map.value.getZoom?.() ?? 0) < 14) map.value.setZoom(15)
  selectCluster(cluster)
}

async function runAtlasSearch() {
  const q = atlasSearchQuery.value.trim()
  if (q.length < 2) {
    atlasSearchResults.value = []
    atlasSearched.value = false
    return
  }
  atlasSearching.value = true
  try {
    atlasSearchResults.value = await searchPlaces(q)
  } finally {
    atlasSearching.value = false
    atlasSearched.value = true
  }
}

function chooseAtlasSearchResult(result: PlaceResult) {
  if (!map.value) return
  if (moveMode.value) cancelMove()
  selectedCluster.value = null
  previewPoint.value = null
  atlasSearchQuery.value = result.label
  atlasSearchResults.value = []
  atlasSearched.value = false
  showSearchMarker(result)
  map.value.panTo({ lat: result.lat, lng: result.lng })
  map.value.setZoom(18)
}

function showSearchMarker(result: PlaceResult) {
  if (!map.value || !markerCtor.value) return
  clearSearchMarker()

  const el = document.createElement('div')
  el.className = 'search-pin'
  const dot = document.createElement('span')
  dot.className = 'search-pin-dot'
  el.appendChild(dot)

  searchMarker.value = new markerCtor.value({
    map: map.value,
    position: { lat: result.lat, lng: result.lng },
    content: el,
    title: result.label,
    zIndex: 10000,
  })
}

function clearSearchMarker() {
  if (searchMarker.value) {
    searchMarker.value.map = null
    searchMarker.value = null
  }
}

async function renderMap(g: typeof google, pins: AtlasPin[]) {
  if (!mapEl.value) return
  const { Map } = (await g.maps.importLibrary('maps')) as any
  const { AdvancedMarkerElement } = (await g.maps.importLibrary('marker')) as any
  markerCtor.value = AdvancedMarkerElement
  atlasPins.value = pins

  const center = pins.length ? { lat: pins[0].lat, lng: pins[0].lng } : DEFAULT_CENTER // 기본: 문정역

  map.value = new Map(mapEl.value, {
    center,
    zoom: pins.length ? 11 : 14,
    mapId: 'DEMO_MAP_ID',
    disableDefaultUI: false,
    mapTypeControl: false,
    streetViewControl: false,
  })
  setupPreviewProjection(g)

  await renderClusters()
  const bounds = new g.maps.LatLngBounds()
  for (const pin of pins) bounds.extend({ lat: pin.lat, lng: pin.lng })
  if (pins.length > 1) map.value.fitBounds(bounds, 64)
}

async function renderClusters() {
  if (!map.value || !markerCtor.value) return
  for (const marker of markers.values()) marker.map = null
  markers.clear()

  const clusters = clusterAtlasPins(atlasPins.value)
  for (const cluster of clusters) {
    const marker = new markerCtor.value({
      map: map.value,
      position: { lat: cluster.lat, lng: cluster.lng },
      content: buildClusterPinElement(cluster),
      title: cluster.items[0].placeName ?? cluster.items[0].preview,
    })
    marker.addListener('click', () => selectCluster(cluster))
    markers.set(cluster.id, marker)
  }
}

function selectCluster(cluster: AtlasCluster) {
  // 다른 핀을 고르면 진행 중이던 이동 모드는 취소
  if (moveMode.value) cancelMove()
  selectedCluster.value = cluster
  updatePreviewPoint(cluster)
}

function setupPreviewProjection(g: typeof google) {
  if (!map.value) return
  previewProjection = new g.maps.OverlayView()
  previewProjection.onAdd = () => {}
  previewProjection.onRemove = () => {}
  previewProjection.draw = () => updatePreviewPoint()
  previewProjection.setMap(map.value)
}

function updatePreviewPoint(cluster = selectedCluster.value) {
  const projection = previewProjection?.getProjection?.()
  const latLng =
    moveMode.value && movePreviewPos.value
      ? movePreviewPos.value
      : cluster
        ? { lat: cluster.lat, lng: cluster.lng }
        : null
  if (!latLng || !projection) {
    previewPoint.value = null
    return
  }
  const point = projection.fromLatLngToContainerPixel(new google.maps.LatLng(latLng.lat, latLng.lng))
  previewPoint.value = point ? { x: point.x, y: point.y } : null
}

function readMarkerPosition(value: unknown) {
  if (!value || typeof value !== 'object') return null
  const candidate = value as { lat?: number | (() => number); lng?: number | (() => number) }
  const lat = typeof candidate.lat === 'function' ? candidate.lat() : candidate.lat
  const lng = typeof candidate.lng === 'function' ? candidate.lng() : candidate.lng
  return typeof lat === 'number' && typeof lng === 'number' ? { lat, lng } : null
}

function updateMovePreviewPosition(latLng: unknown) {
  const next = readMarkerPosition(latLng)
  if (!next) return null
  movePreviewPos.value = next
  updatePreviewPoint()
  return next
}

// '위치 이동' 시작: 고른 개별 핀 위에 임시 드래그 마커를 띄운다.
// 클러스터로 묶여 있어도 그 핀만 단독으로 옮길 수 있다.
function startMove(pin: AtlasPin) {
  if (!map.value || !markerCtor.value) return
  clearDragMarker()
  movingPin.value = pin
  moveMode.value = true
  movedPos.value = null
  movePreviewPos.value = { lat: pin.lat, lng: pin.lng }
  moveError.value = null

  // 원래 자리(선택된 클러스터) 마커는 반투명으로 흐리게 → "여기서 옮기는 중" 표시.
  setOriginDimmed(true)

  // 묶인 핀들이 한 점에 겹쳐 보이므로, 옮길 핀만 드래그 가능한 단독 마커로 표시.
  const marker = new markerCtor.value({
    map: map.value,
    position: { lat: pin.lat, lng: pin.lng },
    content: buildPinElement(pin),
    gmpDraggable: true,
    zIndex: 9999,
  })
  updatePreviewPoint()
  marker.addListener('drag', () => updateMovePreviewPosition(marker.position))
  marker.addListener('dragend', () => {
    const next = updateMovePreviewPosition(marker.position)
    if (next) movedPos.value = next
  })
  dragMarker.value = marker
}

function clearDragMarker() {
  if (dragMarker.value) {
    dragMarker.value.map = null
    dragMarker.value = null
  }
}

// 선택된 클러스터의 원래 마커를 흐리게/원래대로.
function setOriginDimmed(dimmed: boolean) {
  const marker = selectedCluster.value ? markers.get(selectedCluster.value.id) : null
  const el = marker?.content as HTMLElement | undefined
  if (el) el.classList.toggle('dimmed', dimmed)
}

function cancelMove() {
  setOriginDimmed(false)
  clearDragMarker()
  moveMode.value = false
  movingPin.value = null
  movedPos.value = null
  movePreviewPos.value = null
  moveError.value = null
}

async function saveMove() {
  const pin = movingPin.value
  const pos = movedPos.value
  if (!pin || !pos || movingSaving.value) return

  movingSaving.value = true
  moveError.value = null
  try {
    // 본문(html)은 atlas 핀에 없으므로 상세를 불러와 그대로 다시 저장(위치만 교체).
    const detail = await entriesApi.get(pin.id)
    const placeName = await resolvePlaceName(pos.lat, pos.lng)
    await entriesApi.updateContent(pin.id, {
      html: detail.html,
      lat: pos.lat,
      lng: pos.lng,
      placeName,
    })

    const nextPin = { ...pin, lat: pos.lat, lng: pos.lng, placeName }
    const idx = atlasPins.value.findIndex((candidate) => candidate.id === pin.id)
    if (idx >= 0) {
      atlasPins.value.splice(idx, 1, nextPin)
    }
    clearDragMarker()
    moveMode.value = false
    movingPin.value = null
    movedPos.value = null
    movePreviewPos.value = null
    await renderClusters()
    // 옮긴 핀이 속한 클러스터로 미리보기 갱신
    selectedCluster.value =
      clusterAtlasPins(atlasPins.value).find((next) =>
        next.items.some((item) => item.id === pin.id),
      ) ?? null
    updatePreviewPoint()
  } catch (e) {
    moveError.value = e instanceof Error ? e.message : '위치 저장에 실패했어요.'
  } finally {
    movingSaving.value = false
  }
}

function closePreview() {
  if (moveMode.value) cancelMove()
  selectedCluster.value = null
  previewPoint.value = null
}

// 사진 썸네일 핀 (없으면 기본 점)
function buildPinElement(pin: AtlasPin): HTMLElement {
  const wrap = document.createElement('div')
  wrap.className = 'atlas-pin'
  if (pin.thumbMediaId) {
    const img = document.createElement('img')
    img.src = thumbUrl(pin.thumbMediaId)
    img.alt = ''
    wrap.appendChild(img)
  } else {
    wrap.classList.add('no-photo')
  }
  return wrap
}

function buildClusterPinElement(cluster: AtlasCluster): HTMLElement {
  const wrap = buildPinElement(cluster.items[0])
  if (cluster.items.length > 1) {
    wrap.classList.add('clustered')
    // 개수를 원 한가운데에 표시 (사진 위엔 어둡게 덮어 가독성 확보)
    const count = document.createElement('span')
    count.className = 'cluster-count'
    count.textContent = String(cluster.items.length)
    wrap.appendChild(count)
  }
  return wrap
}

// 핀 선택 → 노트 화면 이동 대신 이 화면에서 일기를 모달로 연다.
const editing = ref<EntryDetail | null>(null)
const editLoading = ref(false)
const savingEntry = ref(false) // 모달 닫힌 뒤에도 Drive 저장 끝까지 로딩 표시
const editError = ref<string | null>(null)

async function openNote(id: string) {
  editLoading.value = true
  editError.value = null
  try {
    editing.value = await entriesApi.get(id)
  } catch (e) {
    editError.value = e instanceof Error ? e.message : '기록을 불러오지 못했습니다.'
  } finally {
    editLoading.value = false
  }
}

async function saveEdit(id: string, payload: UpdateEntryPayload) {
  editError.value = null
  // 모달은 켜둔 채로 그 위에 '저장 중…' 오버레이 표시 → 성공해야 닫는다.
  savingEntry.value = true
  try {
    await entriesApi.updateContent(id, payload)
    editing.value = null
    // 본문 발췌/썸네일이 바뀌었을 수 있으나 지도 마커 재구성은 비용이 커,
    // 다음 진입 시 최신화한다. (위치 변경은 별도 '이동' 기능 사용)
  } catch (e) {
    editError.value = e instanceof Error ? e.message : '수정에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}

function previewLabel(pin: AtlasPin) {
  return pin.preview || (pin.thumbMediaId ? '사진 기록' : '(빈 기록)')
}

// 아틀라스에서 연 모달의 위치 칩 클릭: 모달을 닫고 그 위치로 이동.
// (이미 같은 화면이라 라우팅 대신 직접 focus. 쿼리도 맞춰 둔다.)
function openOnMap(id: string) {
  editing.value = null
  if (route.query.entry !== id) {
    router.replace({ name: 'atlas', query: { entry: id } })
  }
  focusEntry(id)
}

// ── 새 핀 추가: 핀은 화면 중앙에 고정, 지도를 움직여 위치를 정한다 ──────────
// (드래그 대신 지도를 이동시키는 방식 — 카카오/우버 스타일. 핀은 HTML 오버레이로 항상 정중앙.)
const placing = ref(false)
const placePos = ref<{ lat: number; lng: number } | null>(null)
// 지도 중심 추적 리스너 (모드 종료 시 해제)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let placeCenterListener: any = null

function syncCenter() {
  const c = map.value?.getCenter?.()
  // 중심을 못 구하면 기본 위치(문정역)로.
  placePos.value = c ? { lat: c.lat(), lng: c.lng() } : { ...DEFAULT_CENTER }
}

function startPlace() {
  if (!map.value || placing.value) return
  // 다른 상호작용 정리
  if (moveMode.value) cancelMove()
  selectedCluster.value = null
  previewPoint.value = null
  clearSearchMarker()

  placing.value = true
  syncCenter() // 현재 지도 중심으로 시작
  // 지도가 멈출 때마다 중심 좌표를 위치로 반영.
  placeCenterListener = map.value.addListener('idle', syncCenter)
}

function stopPlace() {
  if (placeCenterListener) {
    placeCenterListener.remove?.()
    placeCenterListener = null
  }
  placing.value = false
}

function cancelPlace() {
  stopPlace()
  placePos.value = null
}

// 지도 중심(고정 핀 아래) 위치로 새 일기 작성 모달 열기.
async function writeHere() {
  syncCenter() // 최신 중심 반영
  if (!placePos.value) return
  const pos = placePos.value
  stopPlace()

  newPos.value = pos
  newPlaceName.value = await resolvePlaceName(pos.lat, pos.lng)
  creating.value = true
  placePos.value = null
}

// 새 일기 작성 모달 상태
const creating = ref(false)
const newPos = ref<{ lat: number; lng: number } | null>(null)
const newPlaceName = ref<string | null>(null)
const todayIso = new Date().toISOString().slice(0, 10)

function closeCreateModal() {
  creating.value = false
  newPos.value = null
  newPlaceName.value = null
}

async function createEntry(payload: CreateEntryPayload) {
  editError.value = null
  savingEntry.value = true
  try {
    // 노트 작성처럼 카드 색을 랜덤 부여 (이미 색이 있으면 유지)
    await entriesApi.create({ color: randomColor() ?? undefined, ...payload })
    closeCreateModal()
    // 새 핀이 지도에 보이도록 핀 목록을 다시 불러와 마커 재구성.
    atlasPins.value = await entriesApi.atlas()
    await renderClusters()
    status.value = atlasPins.value.length === 0 ? 'empty' : 'ready'
  } catch (e) {
    editError.value = e instanceof Error ? e.message : '작성에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}
</script>

<template>
  <div class="atlas">
    <div ref="mapEl" class="map"></div>

    <div v-if="status === 'ready' || status === 'empty'" class="atlas-search">
      <label class="as-box">
        <span class="mi">search</span>
        <input
          v-model="atlasSearchQuery"
          type="text"
          placeholder="주소 검색"
          aria-label="아틀라스 주소 검색"
          @keydown.enter.prevent="runAtlasSearch"
        />
        <span v-if="atlasSearching" class="as-dot" aria-hidden="true"></span>
      </label>
      <button
        class="as-button"
        :disabled="atlasSearching || atlasSearchQuery.trim().length < 2"
        @click="runAtlasSearch"
      >
        검색
      </button>
      <ul v-if="atlasSearchResults.length" class="as-results" role="listbox">
        <li v-for="(result, i) in atlasSearchResults" :key="i">
          <button class="as-result" @click="chooseAtlasSearchResult(result)">
            <span class="mi">place</span>
            <span>{{ result.label }}</span>
          </button>
        </li>
      </ul>
      <p v-else-if="atlasSearched && !atlasSearching" class="as-empty">
        검색 결과가 없어요. 도로명 주소나 지번 주소를 조금 더 정확히 입력해 주세요.
      </p>
    </div>

    <!-- 핀 놓기 모드: 화면 중앙에 고정된 핀 (지도를 움직여 위치를 맞춘다) -->
    <div v-if="placing" class="center-pin" aria-hidden="true">
      <div class="cp-pin"></div>
      <div class="cp-shadow"></div>
    </div>

    <!-- 상태 오버레이 -->
    <div v-if="status !== 'ready' && status !== 'empty'" class="overlay">
      <template v-if="status === 'loading'">
        <LoadingState />
      </template>
      <template v-else-if="status === 'no-key'">
        <span class="mi">map</span>
        <p class="t">지도 키가 필요해요</p>
        <p class="s">
          <code>.env</code>에 <code>VITE_GOOGLE_MAPS_API_KEY</code>를 설정하세요. (README 참고)
        </p>
      </template>
      <template v-else>
        <span class="mi">error</span>
        <p class="t">{{ errorMsg }}</p>
      </template>
    </div>

    <div v-if="status === 'empty'" class="overlay light">
      <span class="mi">location_off</span>
      <p class="t">위치가 기록된 일기가 없어요</p>
      <p class="s">
        아래 <span class="mi inline">add_location_alt</span> 버튼으로 첫 일기를 지도에 남겨보세요.
      </p>
    </div>

    <!-- 새 일기 추가 버튼 (FAB). 핀 놓기 모드/작성 모달 중엔 숨김. -->
    <button
      v-if="(status === 'ready' || status === 'empty') && !placing && !creating"
      class="fab"
      title="이 지도에 새 일기 쓰기"
      @click="startPlace"
    >
      <span class="mi">add_location_alt</span>
    </button>

    <!-- 핀 놓기 모드 안내 패널 -->
    <div v-if="placing" class="place-panel">
      <p class="pp-title"><span class="mi">add_location_alt</span> 새 일기 위치 정하기</p>
      <p class="pp-hint">
        <span class="mi">drag_pan</span> 지도를 움직여 가운데 핀을 원하는 위치에 맞추세요.
      </p>
      <div class="pp-actions">
        <button class="pp-cancel" @click="cancelPlace">취소</button>
        <button class="pp-write" :disabled="!placePos" @click="writeHere">
          <span class="mi">edit</span> 여기에 쓰기
        </button>
      </div>
    </div>

    <!-- 선택된 핀 미리보기 -->
    <div v-if="selectedCluster && previewStyle" class="preview" :style="previewStyle">
      <button class="px" aria-label="닫기" @click="closePreview">
        <span class="mi">close</span>
      </button>
      <img
        v-if="selectedCluster.items[0].thumbMediaId"
        :src="thumbUrl(selectedCluster.items[0].thumbMediaId)"
        class="pv-img"
        alt=""
      />
      <div class="pv-body" :class="{ 'no-img': !selectedCluster.items[0].thumbMediaId }">
        <div v-if="selectedCluster.items[0].placeName" class="pv-place">
          <span class="mi">place</span>{{ selectedCluster.items[0].placeName }}
        </div>
        <div v-if="selectedCluster.items.length > 1" class="cluster-title">
          이 위치의 기록 {{ selectedCluster.items.length }}개
        </div>
        <!-- 위치 이동 모드: 어느 핀을 옮기는 중인지 안내 + 저장/취소 -->
        <template v-if="moveMode">
          <p class="pv-moving">
            <span class="mi">edit_location</span>
            「{{ movingPin ? previewLabel(movingPin) : '' }}」 위치 이동 중
          </p>
          <p class="pv-hint">
            <span class="mi">drag_pan</span>
            지도에서 핀을 끌어 옮긴 뒤 저장하세요.
          </p>
          <p v-if="moveError" class="pv-err">{{ moveError }}</p>
          <div class="pv-actions">
            <button class="pv-cancel" :disabled="movingSaving" @click="cancelMove">취소</button>
            <button class="pv-save" :disabled="!movedPos || movingSaving" @click="saveMove">
              <InlineSpinner v-if="movingSaving" :size="13" light />
              {{ movingSaving ? '저장 중…' : '여기로 저장' }}
            </button>
          </div>
        </template>

        <!-- 기본 모드: 각 핀마다 [일기 보기] + [이동] -->
        <ul v-else class="cluster-list">
          <li v-for="pin in selectedCluster.items" :key="pin.id">
            <button class="cluster-item" @click="openNote(pin.id)">
              <img v-if="pin.thumbMediaId" :src="thumbUrl(pin.thumbMediaId)" alt="" />
              <span>{{ previewLabel(pin) }}</span>
            </button>
            <button class="cluster-move" title="이 기록의 위치 이동" @click="startMove(pin)">
              <span class="mi">edit_location</span>
            </button>
          </li>
        </ul>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="editLoading || savingEntry || editError"
        class="modal-state"
        :class="{ 'over-modal': savingEntry || (editError && editing) }"
        role="status"
        aria-live="polite"
        @click.self="editError = null"
      >
        <div class="ms-card">
          <template v-if="editLoading || savingEntry">
            <div class="ms-spinner" aria-hidden="true"></div>
            <span>{{ savingEntry ? '저장 중…' : '로딩 중…' }}</span>
          </template>
          <template v-else>
            <span class="mi" aria-hidden="true">error</span>
            <span>{{ editError }}</span>
            <button class="ms-close" @click="editError = null">닫기</button>
          </template>
        </div>
      </div>
    </Teleport>
    <EditNoteModal
      v-if="editing"
      :entry="editing"
      @close="editing = null"
      @save="saveEdit"
      @view-on-map="openOnMap"
    />
    <EditNoteModal
      v-else-if="creating"
      :new-date="todayIso"
      :new-lat="newPos?.lat ?? null"
      :new-lng="newPos?.lng ?? null"
      :new-place-name="newPlaceName"
      @close="closeCreateModal"
      @create="createEntry"
    />
  </div>
</template>

<style scoped>
.atlas {
  position: relative;
  height: calc(100vh - 64px - var(--safe-top));
  margin: -30px -22px calc(-60px - var(--safe-bottom));
}
.map {
  width: 100%;
  height: 100%;
}
.overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  text-align: center;
  background: var(--paper);
  color: var(--ink-soft);
  padding: 20px;
}
.overlay.light {
  background: rgba(251, 250, 246, 0.82);
  /* 안내 텍스트만 있는 오버레이 — FAB/지도 클릭이 통과되도록 */
  pointer-events: none;
}
.overlay .mi {
  font-size: 44px;
  opacity: 0.5;
}
.overlay .t {
  font-size: 16px;
  font-weight: 500;
}
.overlay .s {
  font-size: 13px;
  color: var(--ink-faint);
  line-height: 1.6;
}
.overlay .s code {
  background: var(--sidebar);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}
.mi.inline {
  font-size: 15px;
  vertical-align: middle;
}
.atlas-search {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 6;
  display: grid;
  grid-template-columns: minmax(180px, 320px) auto;
  gap: 8px;
  width: min(460px, calc(100% - 32px));
}
.as-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 13px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.14);
  color: var(--ink-faint);
}
.as-box .mi {
  font-size: 20px;
}
.as-box input {
  min-width: 0;
  flex: 1;
  border: none;
  outline: none;
  background: none;
  color: var(--ink);
  font-family: inherit;
  font-size: 14px;
}
.as-box input::placeholder {
  color: var(--ink-faint);
}
.as-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--seal);
  animation: as-pulse 1s ease-in-out infinite;
}
.as-button {
  height: 42px;
  border: none;
  border-radius: 10px;
  background: var(--seal);
  color: #fff;
  padding: 0 16px;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.14);
  cursor: pointer;
}
.as-button:disabled {
  opacity: 0.55;
  cursor: wait;
}
.as-results,
.as-empty {
  grid-column: 1 / -1;
  margin: 0;
}
.as-results {
  list-style: none;
  padding: 0;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.16);
  overflow: hidden;
}
.as-results li + li {
  border-top: 1px solid var(--line-soft);
}
.as-result {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: none;
  color: var(--ink);
  padding: 10px 12px;
  text-align: left;
  font-family: inherit;
  font-size: 13.5px;
  cursor: pointer;
}
.as-result:hover {
  background: var(--hover);
}
.as-result .mi {
  flex-shrink: 0;
  color: var(--seal);
  font-size: 17px;
}
.as-empty {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 10px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.16);
  padding: 10px 12px;
  color: var(--ink-faint);
  font-size: 12.5px;
}
@keyframes as-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .as-dot {
    animation: none;
  }
}
@media (max-width: 520px) {
  .atlas-search {
    grid-template-columns: 1fr;
    right: 16px;
  }
  .as-button {
    width: 100%;
  }
}
/* ── 새 일기 추가 FAB (오른쪽 아래) ───────────────────────── */
.fab {
  position: absolute;
  right: 18px;
  bottom: 18px;
  z-index: 5;
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 50%;
  background: var(--seal);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.28);
  transition:
    transform 0.14s,
    box-shadow 0.14s;
}
.fab:hover {
  transform: scale(1.06);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.32);
}
.fab .mi {
  font-size: 26px;
}

/* ── 중앙 고정 핀 (핀 놓기 모드) ─────────────────────────── */
/* .center-pin 의 (left:50%, top:50%) = 지도 정중앙 = 핀이 가리키는 지점.
   그림자는 그 지점 지면에, 핀은 그보다 위에 끝을 살짝 띄워 둔다. */
.center-pin {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 4;
  pointer-events: none; /* 지도 드래그를 막지 않게 */
}
/* 지면 그림자: 정중앙에 넓고 흐린 타원으로 깔린다 (핀 끝과 떨어져 보이게) */
.cp-shadow {
  position: absolute;
  left: 50%;
  top: 0;
  width: 18px;
  height: 6px;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.22);
  border-radius: 50%;
  filter: blur(2px);
  animation: cp-shadow 1.6s ease-in-out infinite;
}
/* 물방울 핀: 끝이 정중앙을 가리키되, 지면(그림자)에서 살짝 떠 있게 위로 6px 올림 */
.cp-pin {
  position: absolute;
  left: 50%;
  top: 0;
  width: 24px;
  height: 24px;
  /* 끝(회전 후 아래 꼭짓점)을 정중앙 위 6px 에 두기: 폭의 절반만큼 왼쪽, 핀 높이+간격만큼 위로 */
  transform: translate(-50%, calc(-100% - 6px)) rotate(-45deg);
  transform-origin: center;
  background: var(--seal);
  border: 3px solid #fff;
  border-radius: 50% 50% 50% 0;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.3);
  animation: cp-bounce 1.6s ease-in-out infinite;
}
/* 핀은 살짝 떠올랐다 내려오고, 그림자는 그에 맞춰 커졌다 작아진다 */
@keyframes cp-bounce {
  0%,
  100% {
    transform: translate(-50%, calc(-100% - 6px)) rotate(-45deg);
  }
  50% {
    transform: translate(-50%, calc(-100% - 12px)) rotate(-45deg);
  }
}
@keyframes cp-shadow {
  0%,
  100% {
    width: 18px;
    opacity: 0.9;
  }
  50% {
    width: 13px;
    opacity: 0.6;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cp-pin {
    animation: none;
    transform: translate(-50%, calc(-100% - 6px)) rotate(-45deg);
  }
  .cp-shadow {
    animation: none;
  }
}

.place-panel {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  width: min(360px, calc(100% - 32px));
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.18);
  padding: 14px 16px;
}
.pp-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}
.pp-title .mi {
  font-size: 18px;
  color: var(--seal);
}
.pp-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ink-soft);
}
.pp-hint .mi {
  font-size: 16px;
}
.pp-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.pp-cancel {
  border: 1px solid var(--line);
  background: none;
  color: var(--ink-soft);
  padding: 8px 14px;
  border-radius: 9px;
  font-size: 13px;
  cursor: pointer;
}
.pp-cancel:hover {
  background: var(--hover);
}
.pp-write {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: var(--seal);
  color: #fff;
  padding: 8px 16px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.pp-write .mi {
  font-size: 16px;
}
.pp-write:hover:not(:disabled) {
  filter: brightness(0.96);
}
.pp-write:disabled {
  opacity: 0.55;
  cursor: default;
}

.preview {
  --preview-gap: 44px;
  position: absolute;
  z-index: 7;
  width: min(320px, calc(100% - 32px));
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.16);
  transform: translate(-50%, calc(-100% - var(--preview-gap)));
}
.preview::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -8px;
  width: 14px;
  height: 14px;
  background: var(--card);
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  transform: translateX(-50%) rotate(45deg);
}
.px {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
}
.pv-img {
  width: 100%;
  height: 150px;
  object-fit: cover;
  display: block;
  border-radius: 14px 14px 0 0;
}
.pv-body {
  padding: 12px 14px 14px;
}
/* 사진이 없으면 X 버튼이 본문 위에 뜨므로, 위/오른쪽에 자리를 비워 안 겹치게 */
.pv-body.no-img {
  padding-top: 44px;
}
.pv-place {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ink-soft);
  margin-bottom: 6px;
}
.pv-place .mi {
  font-size: 15px;
}
.pv-text {
  font-size: 14px;
  color: var(--ink);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.cluster-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-soft);
  margin-bottom: 8px;
}
.cluster-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}
.cluster-list li {
  display: flex;
  align-items: stretch;
  gap: 6px;
}
.cluster-item {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line-soft);
  background: var(--sidebar);
  border-radius: 8px;
  padding: 7px 8px;
  color: var(--ink);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  text-align: left;
}
.cluster-item:hover {
  border-color: var(--seal);
}
.cluster-item img {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}
.cluster-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cluster-move {
  flex-shrink: 0;
  width: 38px;
  border: 1px solid var(--line-soft);
  background: var(--sidebar);
  border-radius: 8px;
  color: var(--ink-soft);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.cluster-move:hover {
  border-color: var(--seal);
  color: var(--seal);
}
.cluster-move .mi {
  font-size: 18px;
}
.pv-moving {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-soft);
  margin-bottom: 4px;
}
.pv-moving .mi {
  font-size: 16px;
  color: var(--seal);
}
.pv-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.pv-open,
.pv-move,
.pv-cancel {
  border: 1px solid var(--line);
  background: none;
  color: var(--seal);
  padding: 8px 14px;
  border-radius: 9px;
  font-size: 13px;
  cursor: pointer;
}
.pv-move {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-soft);
}
.pv-move .mi {
  font-size: 16px;
}
.pv-cancel {
  color: var(--ink-soft);
}
.pv-open:hover,
.pv-move:hover,
.pv-cancel:hover:not(:disabled) {
  background: var(--seal-soft);
}
.pv-save {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: var(--seal);
  color: #fff;
  padding: 8px 14px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.pv-save:hover:not(:disabled) {
  filter: brightness(0.96);
}
.pv-save:disabled,
.pv-cancel:disabled {
  opacity: 0.55;
  cursor: wait;
}
.pv-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--ink-soft);
}
.pv-hint .mi {
  font-size: 16px;
}
.pv-err {
  margin-top: 8px;
  font-size: 12px;
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
  animation: spin 0.8s linear infinite;
}
.ms-card .mi {
  color: var(--seal);
  font-size: 18px;
}
.ms-close {
  margin-left: 6px;
  border: 1px solid var(--line);
  background: none;
  color: var(--ink-soft);
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  cursor: pointer;
}
.ms-close:hover {
  background: var(--hover);
}
@media (prefers-reduced-motion: reduce) {
  .ms-spinner {
    animation: none;
  }
}
</style>

<!-- 핀 엘리먼트는 동적으로 map 컨테이너에 들어가므로 전역 스타일로 -->
<style>
.atlas-pin {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  background: var(--seal);
  cursor: pointer;
  transform: translateY(-6px);
}
.atlas-pin img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.atlas-pin.no-photo {
  width: 22px;
  height: 22px;
  background: var(--seal);
}
.atlas-pin.clustered {
  position: relative;
}
/* 위치 이동 중: 원래 자리 마커는 흐리게 */
.atlas-pin.dimmed {
  opacity: 0.35;
  filter: grayscale(0.4);
  transition:
    opacity 0.2s,
    filter 0.2s;
}
/* 개수를 원 한가운데에 — 사진 위엔 반투명 검정으로 덮어 흰 숫자가 또렷하게 */
.cluster-count {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.42);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  line-height: 1;
}
/* 사진 없는 클러스터는 빨간 원에 숫자만 */
.atlas-pin.clustered.no-photo .cluster-count {
  background: transparent;
}
.search-pin {
  width: 22px;
  height: 22px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: var(--seal);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
  display: grid;
  place-items: center;
  transform: translateY(-4px);
}
.search-pin-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
}
</style>
