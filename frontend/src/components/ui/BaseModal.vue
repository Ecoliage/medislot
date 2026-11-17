<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';

const props = withDefaults(defineProps<{busy?: boolean; focusKey?: string}>(), {busy: false});
const emit = defineEmits<{close: []}>();
const modal = ref<HTMLElement | null>(null);
let priorFocus: HTMLElement | null = null;

function focusableElements() {
  return [...(modal.value?.querySelectorAll<HTMLElement>(
    'button:not([disabled]),input:not([disabled]),textarea:not([disabled]),select:not([disabled]),a[href],summary,[tabindex]:not([tabindex="-1"])',
  ) || [])].filter(element => !element.closest('[hidden],[inert]') && element.getAttribute('aria-hidden') !== 'true');
}

async function focusFirst() {
  await nextTick();
  (focusableElements()[0] || modal.value)?.focus();
}

function close() {
  if (!props.busy) emit('close');
}

function trap(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    close();
  }
  if (event.key !== 'Tab') return;
  const elements = focusableElements();
  const first = elements[0];
  const last = elements[elements.length - 1];
  if (!first) {
    event.preventDefault();
    modal.value?.focus();
  } else if (event.shiftKey && (document.activeElement === first || document.activeElement === modal.value)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === modal.value)) {
    event.preventDefault();
    first.focus();
  }
}

onMounted(() => {
  priorFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  focusFirst();
});
watch(() => props.focusKey, focusFirst);
onBeforeUnmount(() => {
  if (priorFocus?.isConnected) priorFocus.focus();
});
</script>

<template>
  <div class="overlay" @click.self="close">
    <section
      ref="modal"
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      tabindex="-1"
      @keydown="trap"
    >
      <button type="button" class="close" aria-label="Fermer" :disabled="busy" @click="close">×</button>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: #102e4a88;
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 10;
}
.modal {
  position: relative;
  background: white;
  border-radius: 17px;
  width: 520px;
  max-height: 94vh;
  overflow: auto;
  padding: 36px;
}
.modal :deep(h2) {
  font-size: 26px;
  line-height: 1.3;
  margin: 10px 0 18px;
}
.modal :deep(h3) {
  font-size: 18px;
}
.modal > :deep(p) {
  font-size: 15px;
}
.close {
  position: absolute;
  right: 16px;
  top: 13px;
  background: none;
  color: #436073;
  font-size: 29px;
}
@media (max-width: 650px) {
  .modal {
    padding: 29px 23px;
  }
}
</style>
