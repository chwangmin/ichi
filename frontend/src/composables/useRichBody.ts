import DOMPurify from 'dompurify'
import { mediaUrl } from '@/api/client'

// 저장용(HTML with data-ref) ↔ 표시용(HTML with src) 변환 + sanitize.
// 본문 안 이미지는 data-ref(미디어 id)로 저장하고, 표시할 때 실제 src 로 바꾼다.

const ALLOWED_TAGS = [
  'p',
  'br',
  'b',
  'strong',
  'i',
  'em',
  'u',
  's',
  'ul',
  'ol',
  'li',
  'blockquote',
  'h1',
  'h2',
  'h3',
  'div',
  'span',
  'img',
]

/** 에디터/표시 DOM(src 포함) → 저장용 HTML(data-ref). sanitize 포함. */
export function toStorageHtml(displayHtml: string): string {
  const doc = new DOMParser().parseFromString(displayHtml, 'text/html')
  doc.querySelectorAll('img').forEach((img) => {
    const ref = img.getAttribute('data-ref')
    img.removeAttribute('src')
    if (ref) img.setAttribute('data-ref', ref)
  })
  const cleaned = DOMPurify.sanitize(doc.body.innerHTML, {
    ALLOWED_TAGS,
    ALLOWED_ATTR: ['data-ref'],
  })
  return cleaned
}

/** 저장된 HTML(data-ref) → 표시용 HTML(src 채움). sanitize 포함. */
export function toDisplayHtml(storageHtml: string): string {
  const safe = DOMPurify.sanitize(storageHtml, {
    ALLOWED_TAGS,
    ALLOWED_ATTR: ['data-ref'],
  })
  const doc = new DOMParser().parseFromString(safe, 'text/html')
  doc.querySelectorAll('img').forEach((img) => {
    const ref = img.getAttribute('data-ref')
    if (ref) img.setAttribute('src', mediaUrl(ref))
  })
  return doc.body.innerHTML
}

/** 표시 HTML 에서 참조된 미디어 id 들 추출 (작성 시 entry 에 연결용). */
export function extractMediaIds(html: string): string[] {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return Array.from(doc.querySelectorAll('img[data-ref]'))
    .map((img) => img.getAttribute('data-ref'))
    .filter((x): x is string => !!x)
}
