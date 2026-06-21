<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'

withDefaults(
  defineProps<{
    title: string
    message?: string
    confirmLabel?: string
    cancelLabel?: string
    danger?: boolean
  }>(),
  {
    message: '',
    confirmLabel: '확인',
    cancelLabel: '취소',
    danger: false,
  },
)

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const confirmBtn = ref<HTMLButtonElement | null>(null)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('cancel')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  // 위험한 동작이므로 기본 포커스는 '취소'가 아닌 확인 버튼에 두되, 실수 클릭을 막으려 포커스만.
  confirmBtn.value?.focus()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="backdrop" @click.self="emit('cancel')">
    <section class="dialog" role="alertdialog" aria-modal="true" :aria-label="title">
      <h2 class="title">{{ title }}</h2>
      <p v-if="message" class="message">{{ message }}</p>
      <div class="actions">
        <button class="btn cancel" @click="emit('cancel')">{{ cancelLabel }}</button>
        <button ref="confirmBtn" class="btn confirm" :class="{ danger }" @click="emit('confirm')">
          {{ confirmLabel }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(42, 41, 38, 0.36);
}
.dialog {
  width: min(380px, 100%);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.22);
  padding: 22px 22px 18px;
}
.title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}
.message {
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--ink-soft);
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 22px;
}
.btn {
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 600;
  padding: 8px 16px;
}
.cancel {
  background: none;
  color: var(--ink-soft);
}
.cancel:hover {
  background: var(--hover);
}
.confirm {
  background: var(--ink);
  color: #fff;
}
.confirm.danger {
  background: var(--seal);
}
.confirm:hover {
  filter: brightness(0.96);
}
</style>
