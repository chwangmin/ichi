<template>
  <!-- 텍스트 옆/버튼 안에 들어가는 작은 로딩 스피너. LoadingState 와 같은 모양으로 통일. -->
  <span class="inline-spinner" :class="{ light }" :style="sizeStyle" aria-hidden="true"></span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 스피너 지름(px). */
    size?: number
    /** 색 있는 버튼 안(흰 글씨)일 때 true → 흰색 스피너. */
    light?: boolean
  }>(),
  { size: 14, light: false },
)

const sizeStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderWidth: `${Math.max(2, Math.round(props.size / 8))}px`,
}))
</script>

<style scoped>
.inline-spinner {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
  border-style: solid;
  border-color: var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: inline-spin 0.7s linear infinite;
}
.inline-spinner.light {
  border-color: rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
}
@keyframes inline-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .inline-spinner {
    animation: none;
  }
}
</style>
