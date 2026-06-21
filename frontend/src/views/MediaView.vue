<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import EditNoteModal from '@/components/EditNoteModal.vue'
import LoadingState from '@/components/LoadingState.vue'
import {
  mediaApi,
  entriesApi,
  type MediaItem,
  type EntryDetail,
  type UpdateEntryPayload,
} from '@/api/entries'
import { thumbUrl, mediaUrl } from '@/api/client'
import { useViewOnMap } from '@/composables/useViewOnMap'

const { viewOnMap } = useViewOnMap()

const items = ref<MediaItem[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    items.value = await mediaApi.gallery()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ── 점진 렌더 (무한 스크롤) — 셀이 많아도 보이는 만큼만 그린다 ──────
const PAGE = 60
const visibleCount = ref(PAGE)
const visibleItems = computed(() => items.value.slice(0, visibleCount.value))
const hasMore = computed(() => visibleCount.value < items.value.length)

watch(
  () => items.value.length,
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
      if (entries[0]?.isIntersecting && hasMore.value) visibleCount.value += PAGE
    },
    { rootMargin: '600px' },
  )
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

// 미디어 선택 → 노트 화면 이동 대신 이 화면에서 연결된 일기를 모달로 연다.
const editing = ref<EntryDetail | null>(null)
const editLoading = ref(false)
const savingEntry = ref(false) // 모달 닫힌 뒤에도 Drive 저장 끝까지 로딩 표시

async function openItem(m: MediaItem) {
  if (!m.entryId) return
  editLoading.value = true
  error.value = null
  try {
    editing.value = await entriesApi.get(m.entryId)
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
    await entriesApi.updateContent(id, payload)
    editing.value = null
    // 본문 수정으로 사진이 추가/삭제됐을 수 있으니 갤러리를 다시 불러온다.
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '수정에 실패했습니다.'
  } finally {
    savingEntry.value = false
  }
}
</script>

<template>
  <div class="media">
    <h1>미디어</h1>

    <p v-if="error" class="banner">{{ error }}</p>
    <LoadingState v-if="loading" />

    <template v-else-if="items.length">
      <div class="grid">
        <button
          v-for="m in visibleItems"
          :key="m.id"
          class="cell"
          :class="{ linked: m.entryId }"
          :title="m.entryId ? '연결된 일기 보기' : ''"
          @click="openItem(m)"
        >
          <img v-if="m.type === 'image'" :src="thumbUrl(m.id)" loading="lazy" alt="" />
          <video v-else :src="mediaUrl(m.id)" preload="metadata" muted></video>
          <span v-if="m.type === 'video'" class="badge"><span class="mi">play_arrow</span></span>
        </button>
      </div>
      <!-- 무한 스크롤 감지점 -->
      <div v-if="hasMore" ref="sentinel" class="load-more" aria-hidden="true">
        <div class="load-spinner"></div>
      </div>
    </template>

    <div v-else class="empty">
      <span class="mi">photo_library</span>
      <p class="empty-title">아직 미디어가 없어요</p>
      <p class="empty-sub">노트에 사진을 붙여넣으면 여기에 모여요.</p>
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
      @save="saveEdit"
      @view-on-map="
        (id) => {
          editing = null
          viewOnMap(id)
        }
      "
    />
  </div>
</template>

<style scoped>
.media {
  max-width: 1080px;
  margin: 0 auto;
}
h1 {
  /* 명조체 대신 본문 글꼴(Noto Sans KR) — '고정됨'(.section-label) 등과 같은 산세리프 톤 */
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 20px;
  color: var(--ink);
}
.banner {
  color: var(--seal);
  background: var(--seal-soft);
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.cell {
  position: relative;
  aspect-ratio: 1;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--sidebar);
  cursor: default;
  padding: 0;
}
.cell.linked {
  cursor: pointer;
}
.cell img,
.cell video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cell.linked:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.14);
}
.badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: grid;
  place-items: center;
}
.badge .mi {
  font-size: 18px;
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

/* 무한 스크롤 로딩 감지점 */
.load-more {
  display: flex;
  justify-content: center;
  padding: 18px 0 28px;
}
.load-spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: media-spin 0.7s linear infinite;
}
@keyframes media-spin {
  to {
    transform: rotate(360deg);
  }
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
  animation: media-spin 0.7s linear infinite;
}
@media (prefers-reduced-motion: reduce) {
  .load-spinner {
    animation: none;
  }
  .ms-spinner {
    animation: none;
  }
}
</style>
