<script setup lang="ts">
import { ref, computed } from 'vue'
import type { CardColor, NoteEntry } from '@/types'
import { thumbUrl } from '@/api/client'

const props = defineProps<{ entry: NoteEntry; highlight?: boolean }>()

// 위치 정보가 있으면 Notes 카드에서는 아이콘만 표시한다.
const hasLocation = computed(
  () => !!props.entry.placeName || (props.entry.lat != null && props.entry.lng != null),
)
const emit = defineEmits<{
  (e: 'open', id: string): void
  (e: 'toggle-pin', id: string): void
  (e: 'change-color', id: string, color: CardColor): void
  (e: 'remove', id: string): void
}>()

const wd = ['일', '월', '화', '수', '목', '금', '토']
const palette = ref(false)

const colors: { key: CardColor; label: string }[] = [
  { key: null, label: '기본' },
  { key: 'y', label: '노랑' },
  { key: 'g', label: '초록' },
  { key: 'b', label: '파랑' },
  { key: 'p', label: '보라' },
]

function dateLabel(iso: string): { d: string; day: string } {
  const dt = new Date(iso)
  return {
    d: `${dt.getMonth() + 1}월 ${dt.getDate()}일`,
    day: wd[dt.getDay()] + '요일',
  }
}

function pick(color: CardColor) {
  emit('change-color', props.entry.id, color)
  palette.value = false
}

const colorClass = () => (props.entry.color ? `c-${props.entry.color}` : '')
</script>

<template>
  <div
    class="card"
    :class="[colorClass(), { pinned: entry.pinned, highlight }]"
    @click="emit('open', entry.id)"
  >
    <button
      class="pin"
      :aria-label="entry.pinned ? '핀 해제' : '핀 고정'"
      @click.stop="emit('toggle-pin', entry.id)"
    >
      <span class="mi">keep</span>
    </button>
    <img v-if="entry.thumbMediaId" class="thumb" :src="thumbUrl(entry.thumbMediaId)" alt="" />
    <div class="meta">
      <span class="d">{{ dateLabel(entry.entryDate).d }}</span>
      <span>· {{ dateLabel(entry.entryDate).day }}</span>
      <span v-if="hasLocation" class="loc icon-only" title="위치 기록됨">
        <span class="mi">place</span>
      </span>
    </div>
    <div v-if="entry.preview" class="body">{{ entry.preview }}</div>

    <div class="foot">
      <div class="palette-wrap">
        <button class="fbtn" aria-label="색 변경" @click.stop="palette = !palette">
          <span class="mi">palette</span>
        </button>
        <div v-if="palette" class="palette" role="menu">
          <button
            v-for="c in colors"
            :key="c.key ?? 'none'"
            class="swatch"
            :class="c.key ? `s-${c.key}` : 's-none'"
            :title="c.label"
            @click.stop="pick(c.key)"
          ></button>
        </div>
      </div>
      <button class="fbtn" aria-label="삭제" @click.stop="emit('remove', entry.id)">
        <span class="mi">delete</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.card {
  break-inside: avoid;
  /* 반투명 + 살짝 흐림 → 뒤 날씨 배경이 은은하게 비침 */
  background: color-mix(in srgb, var(--card) 78%, transparent);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  margin-bottom: 16px;
  padding: 15px 17px 13px;
  position: relative;
  transition:
    box-shadow 0.16s,
    border-color 0.16s;
  cursor: pointer;
}
.card:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.09);
}
.card.highlight {
  outline: 2px solid var(--seal);
  outline-offset: 2px;
  animation: pulse 1.6s ease;
}
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 var(--seal-soft);
  }
  40% {
    box-shadow: 0 0 0 6px var(--seal-soft);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(0, 0, 0, 0);
  }
}
.card.c-y {
  background: color-mix(in srgb, var(--y) 78%, transparent);
  border-color: var(--y-b);
}
.card.c-g {
  background: color-mix(in srgb, var(--g) 78%, transparent);
  border-color: var(--g-b);
}
.card.c-b {
  background: color-mix(in srgb, var(--b) 78%, transparent);
  border-color: var(--b-b);
}
.card.c-p {
  background: color-mix(in srgb, var(--p) 78%, transparent);
  border-color: var(--p-b);
}
/* 색 카드는 밤에도 밝은 파스텔을 유지하므로, 그 위 글자는 항상 어둡게 고정해 대비 확보 */
.card.c-y,
.card.c-g,
.card.c-b,
.card.c-p {
  --ink: var(--ink-on-color);
  --ink-soft: color-mix(in srgb, var(--ink-on-color) 72%, transparent);
  --ink-faint: color-mix(in srgb, var(--ink-on-color) 52%, transparent);
}

.thumb {
  display: block;
  width: calc(100% + 34px);
  max-height: 260px;
  object-fit: cover;
  margin: -15px -17px 12px;
  border-radius: var(--radius) var(--radius) 8px 8px;
  background: var(--line-soft);
}

.pin {
  position: absolute;
  top: 11px;
  right: 11px;
  width: 30px;
  height: 30px;
  border: none;
  background: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--ink-faint);
  opacity: 0;
  transition: opacity 0.14s;
  cursor: pointer;
}
.card:hover .pin {
  opacity: 1;
}
.pin:hover {
  background: rgba(0, 0, 0, 0.05);
}
.pin .mi {
  font-size: 18px;
}
.card.pinned .pin {
  opacity: 1;
  color: var(--seal);
}

.meta {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  color: var(--ink-faint);
  margin-bottom: 7px;
  /* 오른쪽 위 핀 버튼과 겹치지 않도록 여백 확보 */
  padding-right: 34px;
}
.meta .d {
  font-family: 'Nanum Myeongjo', serif;
  font-weight: 700;
  color: var(--ink-soft);
  font-size: 12.5px;
}
.body {
  font-size: 14px;
  line-height: 1.62;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
}
/* 위치: 날짜·요일 줄의 요일 오른쪽에 아이콘만 표시. */
.loc {
  display: inline-flex;
  align-items: center;
  margin-left: 1px;
  color: var(--ink-soft);
  background: rgba(0, 0, 0, 0.05);
}
.loc.icon-only {
  padding: 5px;
  border-radius: 50%;
}
.loc .mi {
  font-size: 14px;
}

.foot {
  display: flex;
  gap: 2px;
  margin-top: 10px;
  opacity: 0;
  transition: opacity 0.14s;
}
.card:hover .foot,
.card:focus-within .foot {
  opacity: 1;
}
.fbtn {
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--ink-soft);
}
.fbtn:hover {
  background: rgba(0, 0, 0, 0.06);
}
.fbtn .mi {
  font-size: 17px;
}

.palette-wrap {
  position: relative;
}
.palette {
  position: absolute;
  bottom: 38px;
  left: 0;
  display: flex;
  gap: 6px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 6px 8px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  z-index: 5;
}
.swatch {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid var(--line);
  cursor: pointer;
}
.s-none {
  background: var(--card);
}
.s-y {
  background: var(--y);
  border-color: var(--y-b);
}
.s-g {
  background: var(--g);
  border-color: var(--g-b);
}
.s-b {
  background: var(--b);
  border-color: var(--b-b);
}
.s-p {
  background: var(--p);
  border-color: var(--p-b);
}
</style>
