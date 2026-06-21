<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import LocationPicker from '@/components/LocationPicker.vue'
import InlineSpinner from '@/components/InlineSpinner.vue'
import {
  mediaApi,
  type CreateEntryPayload,
  type EntryDetail,
  type UpdateEntryPayload,
} from '@/api/entries'
import { mediaUrl } from '@/api/client'
import { extractMediaIds, toDisplayHtml, toStorageHtml } from '@/composables/useRichBody'
import { useScrollLock } from '@/composables/useScrollLock'

// 모달이 떠 있는 동안 뒤 페이지 스크롤 잠금
useScrollLock()

// entry 가 있으면 '수정', 없으면 '새 일기'(newDate 날짜로 작성) 모드.
// 신규 모드에서 newLat/newLng 가 오면(아틀라스에서 찍은 위치) 위치를 미리 채운다.
const props = defineProps<{
  entry?: EntryDetail | null
  newDate?: string
  newLat?: number | null
  newLng?: number | null
  newPlaceName?: string | null
}>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save', id: string, payload: UpdateEntryPayload): void
  (e: 'create', payload: CreateEntryPayload): void
  (e: 'view-on-map', id: string): void
}>()

const isNew = computed(() => !props.entry)
// 헤더에 보일 날짜: 수정이면 그 일기 날짜, 신규면 선택 날짜(없으면 오늘).
const headerDate = computed(
  () => props.entry?.entryDate ?? props.newDate ?? new Date().toISOString().slice(0, 10),
)

const editor = ref<HTMLDivElement | null>(null)
const empty = ref(true)
const uploading = ref(false)
const saving = ref(false)
const locationBusy = ref(false)
const error = ref<string | null>(null)
const location = ref<{ lat: number; lng: number; placeName?: string | null } | null>(null)

let imageSeq = 0
const pendingImages = new Map<string, { file: File | Blob; objectUrl: string }>()

watch(
  () => props.entry,
  async (entry) => {
    if (entry && entry.lat != null && entry.lng != null) {
      location.value = { lat: entry.lat, lng: entry.lng, placeName: entry.placeName }
    } else if (!entry && props.newLat != null && props.newLng != null) {
      // 새 일기 + 아틀라스에서 찍은 위치 → 미리 채움
      location.value = { lat: props.newLat, lng: props.newLng, placeName: props.newPlaceName }
    } else {
      location.value = null
    }
    await nextTick()
    if (editor.value) {
      editor.value.innerHTML = entry ? toDisplayHtml(entry.html) : ''
      onInput()
    }
  },
  { immediate: true },
)

function onInput() {
  const el = editor.value
  empty.value = !el || ((el.textContent?.trim() ?? '') === '' && !el.querySelector('img'))
}

function insertNode(node: Node) {
  const el = editor.value
  if (!el) return
  el.focus()
  const sel = window.getSelection()
  if (sel && sel.rangeCount > 0 && el.contains(sel.anchorNode)) {
    const range = sel.getRangeAt(0)
    range.deleteContents()
    range.insertNode(node)
    range.setStartAfter(node)
    range.collapse(true)
    sel.removeAllRanges()
    sel.addRange(range)
  } else {
    el.appendChild(node)
  }
}

function insertPendingImage(file: File | Blob) {
  const id = `edit-pending-${++imageSeq}`
  const objectUrl = URL.createObjectURL(file)
  pendingImages.set(id, { file, objectUrl })

  const img = document.createElement('img')
  img.setAttribute('data-pending-upload', id)
  img.setAttribute('src', objectUrl)
  img.style.maxWidth = '100%'
  insertNode(img)
  onInput()
}

async function uploadPendingImages(el: HTMLElement) {
  const imgs = Array.from(el.querySelectorAll<HTMLImageElement>('img[data-pending-upload]'))
  if (!imgs.length) return

  uploading.value = true
  error.value = null
  try {
    for (const img of imgs) {
      const id = img.getAttribute('data-pending-upload')
      const pending = id ? pendingImages.get(id) : null
      if (!id || !pending) continue

      const media = await mediaApi.uploadImage(pending.file)
      img.setAttribute('data-ref', media.id)
      img.setAttribute('src', mediaUrl(media.id))
      img.removeAttribute('data-pending-upload')
      URL.revokeObjectURL(pending.objectUrl)
      pendingImages.delete(id)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '이미지 업로드에 실패했습니다.'
    throw e
  } finally {
    uploading.value = false
  }
}

function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) insertPendingImage(file)
  input.value = ''
}

async function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const file = item.getAsFile()
      if (file) insertPendingImage(file)
      return
    }
  }
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
}

function clearLocation() {
  location.value = null
}

// 위치 칩을 누르면 아틀라스에서 이 기록의 위치를 보여준다 (부모가 라우팅 처리).
// 아직 저장 전인 새 일기(entry 없음)는 지도로 보낼 수 없으므로 동작 안 함.
function viewOnMap() {
  if (!location.value || !props.entry) return
  emit('view-on-map', props.entry.id)
}

function cleanupPendingImages() {
  for (const pending of pendingImages.values()) {
    URL.revokeObjectURL(pending.objectUrl)
  }
  pendingImages.clear()
}

function close() {
  cleanupPendingImages()
  emit('close')
}

async function save() {
  const el = editor.value
  if (!el || saving.value || uploading.value || locationBusy.value) return

  const draft = new DOMParser().parseFromString(el.innerHTML, 'text/html')
  const hasText = (draft.body.textContent ?? '').trim() !== ''
  const hasImg = !!draft.querySelector('img')
  if (!hasText && !hasImg) return

  saving.value = true
  try {
    await uploadPendingImages(el)
    const html = toStorageHtml(el.innerHTML)
    const mediaIds = extractMediaIds(html)
    const lat = location.value?.lat ?? null
    const lng = location.value?.lng ?? null
    const placeName = location.value?.placeName ?? null
    if (props.entry) {
      emit('save', props.entry.id, { html, mediaIds, lat, lng, placeName })
    } else {
      // 새 일기: 선택한 날짜로 작성
      emit('create', { html, entryDate: props.newDate, mediaIds, lat, lng, placeName })
    }
    cleanupPendingImages()
  } catch {
    // error state is set where the failure occurs
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <!-- body 로 텔레포트: 부모(예: 캘린더 .cal 의 backdrop-filter)가 fixed 컨테이닝 블록이 되어
       모달 높이 제한(dvh)이 깨지는 걸 막는다. 어느 화면에서 열든 항상 뷰포트 기준. -->
  <Teleport to="body">
    <div class="backdrop" @click.self="close">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="isNew ? '새 기록 작성' : '기록 수정'"
      >
        <button class="close" aria-label="닫기" @click="close">
          <span class="mi">close</span>
        </button>

        <div class="date">{{ headerDate }}<span v-if="isNew" class="new-tag">새 기록</span></div>
        <div class="editor-wrap">
          <div
            ref="editor"
            class="editor"
            contenteditable="true"
            role="textbox"
            aria-multiline="true"
            aria-label="기록 내용 수정"
            @input="onInput"
            @paste="onPaste"
          ></div>
          <span v-if="empty" class="placeholder">내용을 입력하거나 이미지를 추가하세요</span>
        </div>

        <div v-if="location" class="loc-chip">
          <!-- 수정 모드: 위치 칩을 누르면 아틀라스로. 새 일기는 아직 지도로 못 보내므로 일반 텍스트. -->
          <button
            v-if="!isNew"
            class="loc-go"
            :title="(location.placeName || '위치 기록됨') + ' · 지도에서 보기'"
            @click="viewOnMap"
          >
            <span class="mi">place</span>
            <span>{{ location.placeName || '위치 기록됨' }}</span>
            <span class="mi go">arrow_outward</span>
          </button>
          <span v-else class="loc-go static">
            <span class="mi">place</span>
            <span>{{ location.placeName || '위치 기록됨' }}</span>
          </span>
          <button class="loc-x" aria-label="위치 제거" @click="clearLocation">
            <span class="mi">close</span>
          </button>
        </div>

        <p v-if="error" class="err">{{ error }}</p>

        <footer class="tools">
          <label class="tool" title="사진 추가">
            <span class="mi">image</span>
            <input type="file" accept="image/*" hidden @change="onPickImage" />
          </label>
          <LocationPicker
            v-model="location"
            placement="above"
            :disabled="uploading || saving"
            @busy-change="locationBusy = $event"
            @error="error = $event"
          />
          <span v-if="locationBusy" class="status">
            <InlineSpinner :size="13" />위치 처리 중...
          </span>
          <span v-else-if="uploading" class="status">
            <InlineSpinner :size="13" />업로드 중...
          </span>
          <button class="save" :disabled="saving || uploading || locationBusy" @click="save">
            <InlineSpinner v-if="saving || uploading" light />
            {{ uploading ? '업로드 중...' : saving ? '저장 중...' : '저장' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  /* flex 중앙 정렬: 내용이 길어도 모달 높이를 뷰포트 안으로 가두므로 상/하단이 안 잘린다. */
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(24px + var(--safe-top)) 16px calc(24px + var(--safe-bottom));
  overscroll-behavior: contain; /* 모달 안 스크롤이 끝나도 뒤 페이지로 번지지 않게 */
  background: rgba(42, 41, 38, 0.36);
}
.modal {
  width: min(640px, 100%);
  /* 뷰포트 안에 확실히 들어오도록 높이 제한 + 내부 3단(헤더/본문/푸터) 레이아웃.
     화면 높이의 88% 또는 720px 중 작은 값 — 위아래 여백을 넉넉히 둬 저장 버튼이 항상 보이게. */
  max-height: min(88dvh, 720px);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: visible; /* 푸터의 위치 선택 패널이 모달 경계에서 잘리지 않도록 둔다. */
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.22);
  position: relative;
}
.close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  /* 본문 첫 줄이 큰 이미지여도 가려지지 않도록 항상 위에 + 반투명 배경 */
  z-index: 2;
  background: color-mix(in srgb, var(--card) 78%, transparent);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  color: var(--ink-soft);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.close:hover,
.tool:hover,
.loc-x:hover {
  background: var(--hover);
}
.date {
  flex-shrink: 0; /* 헤더 고정 */
  padding: 16px 54px 0 18px;
  font-size: 12px;
  color: var(--ink-faint);
}
.new-tag {
  margin-left: 8px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--seal-soft);
  color: var(--seal);
  font-weight: 600;
}
.editor-wrap {
  position: relative;
  /* 헤더/푸터를 뺀 남은 공간을 채우고, 본문이 길면 이 영역만 스크롤.
     min-height:0 이라야 자식(.editor)이 콘텐츠 크기에 밀려 안 줄어드는 걸 막아 스크롤이 생긴다. */
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
}
.editor {
  /* flex-basis 로 기본 높이(빈 본문 시)를 주되, 길면 grow 후 overflow 로 스크롤.
     min-height:0 이라야 긴 본문일 때 넘치지 않고 스크롤된다 */
  flex: 1 1 200px;
  min-height: 0;
  overflow-y: auto;
  outline: none;
  padding: 14px 18px 8px;
  color: var(--ink);
  font-size: 15.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  /* 스크롤바도 테마 토큰을 따르게 (다크모드면 어둡게) — Firefox */
  scrollbar-width: thin;
  scrollbar-color: var(--line) transparent;
}
/* 스크롤바 — Chrome/Edge/Safari. 토큰 기반이라 밤엔 자동으로 어두워진다. */
.editor::-webkit-scrollbar {
  width: 10px;
}
.editor::-webkit-scrollbar-thumb {
  background: var(--line);
  border-radius: 8px;
  border: 2px solid var(--card);
}
.editor::-webkit-scrollbar-thumb:hover {
  background: var(--ink-faint);
}
.editor::-webkit-scrollbar-track {
  background: transparent;
}
.editor :deep(img) {
  display: block;
  max-width: 100%;
  border-radius: 8px;
  margin: 8px 0;
}
.placeholder {
  position: absolute;
  top: 14px;
  left: 18px;
  pointer-events: none;
  color: var(--ink-faint);
  font-size: 15px;
}
.loc-chip {
  flex-shrink: 0; /* 본문 스크롤 영역과 달리 줄어들지 않게 */
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin: 0 18px 8px;
  padding: 3px 5px 3px 6px;
  border-radius: 999px;
  background: var(--seal-soft);
  color: var(--seal);
  font-size: 12px;
}
.loc-chip .mi {
  font-size: 15px;
}
/* 위치명 본문 — 누르면 아틀라스로 이동 */
.loc-go {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  border-radius: 999px;
  padding: 3px 8px;
}
.loc-go:hover {
  background: color-mix(in srgb, var(--seal) 14%, transparent);
}
/* 새 일기: 지도 이동 없는 정적 위치 표시 */
.loc-go.static {
  cursor: default;
}
.loc-go.static:hover {
  background: none;
}
.loc-go .go {
  font-size: 14px;
  opacity: 0.7;
}
/* 위치 제거(X) */
.loc-x {
  border: none;
  background: none;
  color: inherit;
  border-radius: 50%;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 2px;
}
.err {
  flex-shrink: 0;
  padding: 0 18px 8px;
  color: var(--seal);
  font-size: 12.5px;
}
.tools {
  flex-shrink: 0; /* 푸터(툴바·저장) 항상 고정 표시 */
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 12px;
  border-top: 1px solid var(--line-soft);
}
.tool {
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
.tool:disabled {
  opacity: 0.55;
  cursor: wait;
}
/* 저장 버튼은 스피너로 진행을 보여주므로 살짝만 흐리게 (스피너가 또렷이 보이게) */
.save:disabled {
  opacity: 0.85;
  cursor: wait;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-faint);
}
.save {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  border-radius: 8px;
  background: var(--seal);
  color: #fff;
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  padding: 8px 18px;
}
.save:hover:not(:disabled) {
  filter: brightness(0.96);
}
</style>
