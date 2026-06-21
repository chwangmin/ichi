<template>
  <div class="loading-state" :class="{ compact }" role="status" aria-live="polite">
    <div class="loading-spinner" aria-hidden="true"></div>
    <span>{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string
    compact?: boolean
  }>(),
  {
    label: '로딩 중…',
    compact: false,
  },
)
</script>

<style scoped>
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
  color: var(--ink-faint);
  padding: 40px;
  font-size: 14px;
}
.loading-state.compact {
  justify-content: flex-start;
  padding: 8px 0;
  font-size: 13.5px;
}
.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: loading-spin 0.7s linear infinite;
}
.loading-state.compact .loading-spinner {
  width: 16px;
  height: 16px;
}
@keyframes loading-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .loading-spinner {
    animation: none;
  }
}
</style>
