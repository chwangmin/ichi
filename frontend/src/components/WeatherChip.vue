<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useWeather } from '@/composables/useWeather'
import { useHomeLocation } from '@/composables/useHomeLocation'

const { state, weather, load } = useWeather()
const { home } = useHomeLocation()

onMounted(load)
// 설정에서 '내 장소'를 바꾸면 날씨를 다시 불러온다.
watch(home, () => load())
</script>

<template>
  <!-- 상단바 날씨 칩 (Open-Meteo). 상태별로 조용히 동작. -->
  <div class="weather" :class="state">
    <template v-if="state === 'ready' && weather">
      <span class="mi">{{ weather.icon }}</span>
      <span class="temp">{{ weather.tempC }}°</span>
      <span class="label">{{ weather.label }}</span>
      <span v-if="weather.fallback" class="label fb" title="위치를 못 받아 서울 기준으로 표시 중"
        >서울</span
      >
    </template>

    <template v-else-if="state === 'loading'">
      <span class="dot" aria-hidden="true"></span>
      <span class="muted">날씨…</span>
    </template>

    <button v-else-if="state === 'error'" class="retry" title="날씨를 다시 불러오기" @click="load">
      <span class="mi">cloud_off</span>
      <span class="muted">날씨 보기</span>
    </button>
  </div>
</template>

<style scoped>
.weather {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--sidebar);
  color: var(--ink-soft);
  font-size: 13px;
  white-space: nowrap;
  user-select: none;
}
.weather .mi {
  font-size: 19px;
}
.temp {
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}
.label {
  color: var(--ink-faint);
  font-size: 12px;
}
.label.fb {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--line-soft);
}
.muted {
  color: var(--ink-faint);
  font-size: 12px;
}
.retry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}
.retry .mi {
  font-size: 18px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--line);
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .dot {
    animation: none;
  }
}
/* 좁은 화면에선 설명 텍스트 숨기고 아이콘+기온만 */
@media (max-width: 560px) {
  .weather .label {
    display: none;
  }
}
</style>
