<script setup lang="ts">
defineProps<{ icon: string; title: string; milestone: string; desc: string }>()
</script>

<template>
  <div class="placeholder">
    <span class="mi">{{ icon }}</span>
    <p class="title">{{ title }}</p>
    <p class="desc">{{ desc }}</p>
    <span class="badge">{{ milestone }}에서 구현 예정</span>
  </div>
</template>

<style scoped>
.placeholder {
  max-width: 520px;
  margin: 40px auto;
  text-align: center;
  color: var(--ink-faint);
  padding: 40px 20px;
}
.placeholder .mi {
  font-size: 52px;
  opacity: 0.5;
}
.title {
  margin-top: 16px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ink-soft);
  font-family: 'Nanum Myeongjo', serif;
}
.desc {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
}
.badge {
  display: inline-block;
  margin-top: 18px;
  font-size: 12px;
  background: var(--seal-soft);
  color: var(--seal);
  padding: 5px 12px;
  border-radius: 20px;
  font-weight: 500;
}
</style>
