<script setup lang="ts">
import { ref } from 'vue'
import LocationPicker from '@/components/LocationPicker.vue'
import InlineSpinner from '@/components/InlineSpinner.vue'
import { mediaApi } from '@/api/entries'
import { mediaUrl } from '@/api/client'
import { toStorageHtml, extractMediaIds } from '@/composables/useRichBody'

const emit = defineEmits<{
  (
    e: 'submit',
    payload: {
      html: string
      mediaIds: string[]
      lat?: number
      lng?: number
      placeName?: string
    },
  ): void
}>()

const editor = ref<HTMLDivElement | null>(null)
const empty = ref(true)
const uploading = ref(false)
const error = ref<string | null>(null)
const dragOver = ref(false)
let imageSeq = 0
const pendingImages = new Map<string, { file: File | Blob; objectUrl: string }>()

// 이미지 한 장당 최대 용량(백엔드 multipart max-file-size 와 동일하게 25MB).
// 넘으면 서버에 보내기 전에 프론트에서 막고 안내한다.
const MAX_IMAGE_BYTES = 25 * 1024 * 1024

// 위치 첨부
const location = ref<{
  lat: number
  lng: number
  placeName?: string | null
} | null>(null)
const locationBusy = ref(false)

const now = new Date()
const todayLabel = `${now.getMonth() + 1}월 ${now.getDate()}일`

function onInput() {
  const el = editor.value
  empty.value = !el || (el.textContent?.trim() === '' && !el.querySelector('img'))
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
  // 이미지가 아니면: 서식 없는 텍스트로 붙여넣기 (XSS/잡 HTML 방지)
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
}

function hasImageDrag(e: DragEvent): boolean {
  // 드래그 중에는 파일 내용을 못 읽으니 type 으로만 판단.
  return Array.from(e.dataTransfer?.items ?? []).some(
    (it) => it.kind === 'file' && it.type.startsWith('image/'),
  )
}

function onDragOver(e: DragEvent) {
  if (!hasImageDrag(e)) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
  dragOver.value = true
}

function onDragLeave(e: DragEvent) {
  // 자식 요소로 이동하는 경우는 무시(에디터 밖으로 나갈 때만 해제).
  if (e.currentTarget instanceof Node && e.relatedTarget instanceof Node) {
    if (e.currentTarget.contains(e.relatedTarget)) return
  }
  dragOver.value = false
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  const files = Array.from(e.dataTransfer?.files ?? []).filter((f) => f.type.startsWith('image/'))
  if (!files.length) return // 이미지가 아니면 기본 동작에 맡김(텍스트 등)
  e.preventDefault()
  for (const file of files) insertPendingImage(file)
}

function insertPendingImage(file: File | Blob) {
  if (file.size > MAX_IMAGE_BYTES) {
    error.value = '이미지가 너무 큽니다. 25MB 이하만 넣을 수 있어요.'
    return
  }
  error.value = null

  const id = `pending-${++imageSeq}`
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

function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) insertPendingImage(file)
  input.value = ''
}

async function commit() {
  const el = editor.value
  if (!el || uploading.value || locationBusy.value) return

  // 텍스트도 이미지도 없으면 무시
  const draft = new DOMParser().parseFromString(el.innerHTML, 'text/html')
  const hasText = (draft.body.textContent ?? '').trim() !== ''
  const hasImg = !!draft.querySelector('img')
  if (!hasText && !hasImg) return

  try {
    await uploadPendingImages(el)
  } catch {
    return
  }

  const html = toStorageHtml(el.innerHTML)
  const tmp = new DOMParser().parseFromString(html, 'text/html')
  const hasStoredText = (tmp.body.textContent ?? '').trim() !== ''
  const hasStoredImg = !!tmp.querySelector('img[data-ref]')
  if (!hasStoredText && !hasStoredImg) return

  emit('submit', {
    html,
    mediaIds: extractMediaIds(html),
    lat: location.value?.lat,
    lng: location.value?.lng,
    placeName: location.value?.placeName ?? undefined,
  })
  el.innerHTML = ''
  empty.value = true
  location.value = null
}

function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    commit()
  }
}
</script>

<template>
  <div class="composer">
    <div class="date-chip">
      <span class="mi" style="font-size: 15px">edit_calendar</span>
      <b>{{ todayLabel }}</b> · 오늘 하루를 한 줄 남겨보세요
    </div>

    <div
      class="editor-wrap"
      :class="{ 'drag-over': dragOver }"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <div
        ref="editor"
        class="editor"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        aria-label="오늘의 일기"
        @input="onInput"
        @paste="onPaste"
        @keydown="onKeydown"
      ></div>
      <span v-if="empty" class="placeholder"
        >끄적여보기… (아래 이미지·지도로 사진과 위치를 더해봐요)</span
      >
      <div v-if="dragOver" class="drop-hint">여기에 사진을 놓아주세요</div>
    </div>

    <div v-if="location" class="loc-chip">
      <span class="mi check">check_circle</span>
      <span class="loc-text">위치 첨부됨 · {{ location.placeName || '위치 기록됨' }}</span>
      <button class="loc-x" aria-label="위치 제거" @click="location = null">
        <span class="mi">close</span>
      </button>
    </div>

    <p v-if="error" class="err">{{ error }}</p>

    <div class="tools">
      <label class="tool" title="사진">
        <span class="mi">image</span>
        <input type="file" accept="image/*" hidden @change="onPickImage" />
      </label>
      <LocationPicker
        v-model="location"
        placement="below"
        :disabled="uploading"
        @busy-change="locationBusy = $event"
        @error="error = $event"
      />
      <span v-if="locationBusy" class="uploading"> <InlineSpinner :size="13" />위치 처리 중… </span>
      <span v-else-if="uploading" class="uploading"> <InlineSpinner :size="13" />업로드 중… </span>
      <button class="done" :disabled="uploading || locationBusy" @click="commit">
        <InlineSpinner v-if="uploading" :size="13" />
        {{ uploading ? '업로드 중…' : '완료' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  max-width: 600px;
  margin: 0 auto 34px;
  /* 반투명 + 살짝 흐림 → 뒤 날씨 배경(구름 등)이 은은하게 비침.
     너무 투명하면 글자가 묻혀서, 가독성 위해 불투명도를 올림. */
  background: color-mix(in srgb, var(--card) 82%, transparent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid color-mix(in srgb, var(--line) 85%, transparent);
  border-radius: var(--radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.18s;
}
.composer:focus-within {
  /* 입력 중엔 거의 불투명하게 (가독성 우선) */
  background: color-mix(in srgb, var(--card) 94%, transparent);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}
.date-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 13px 18px 0;
  font-size: 12px;
  color: var(--ink-faint);
}
.date-chip b {
  color: var(--seal);
  font-family: 'Nanum Myeongjo', serif;
  font-weight: 700;
  font-size: 13px;
}
.editor-wrap {
  position: relative;
  border-radius: 10px;
  transition: box-shadow 0.15s;
}
.editor-wrap.drag-over {
  /* 드래그 중인 이미지를 받을 영역임을 시각적으로 표시 */
  box-shadow: inset 0 0 0 2px var(--seal);
}
.editor-wrap.drag-over .editor {
  /* 드롭존 위에 오버레이가 떠 있을 때 본문 입력은 가린다 */
  opacity: 0.4;
}
.drop-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  pointer-events: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--seal);
  background: color-mix(in srgb, var(--seal-soft) 70%, transparent);
  border-radius: 10px;
}
.editor {
  width: 100%;
  outline: none;
  font-family: inherit;
  font-size: 15.5px;
  line-height: 1.65;
  color: var(--ink);
  padding: 10px 18px 4px;
  min-height: 30px;
  white-space: pre-wrap;
  word-break: break-word;
}
.editor :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 6px 0;
}
.placeholder {
  position: absolute;
  top: 10px;
  left: 18px;
  color: var(--ink-faint);
  font-size: 15.5px;
  pointer-events: none;
}
.err {
  padding: 0 18px;
  color: var(--seal);
  font-size: 12.5px;
}
.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 10px;
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
.tool:hover {
  background: var(--hover);
}
.tool:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tool.on {
  color: var(--seal);
  background: var(--seal-soft);
}
.tool .mi {
  font-size: 20px;
}
.loc-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0 18px 4px;
  font-size: 12px;
  color: var(--seal);
  background: var(--seal-soft);
  padding: 4px 6px 4px 10px;
  border-radius: 20px;
}
.loc-chip .mi {
  font-size: 15px;
}
.loc-chip .check {
  color: #3a7d44;
  font-size: 16px;
}
.loc-text {
  font-variant-numeric: tabular-nums;
}
.loc-x {
  border: none;
  background: none;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--seal);
  padding: 2px;
  border-radius: 50%;
}
.loc-x .mi {
  font-size: 14px;
}
.uploading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-faint);
}
.done {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  background: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
  padding: 8px 18px;
  border-radius: 8px;
}
.done:hover {
  background: var(--hover);
}
.done:disabled {
  color: var(--ink-faint);
  cursor: wait;
}
</style>
