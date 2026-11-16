<script setup lang="ts">
import {ref} from 'vue';
import {ConvocationError, FALLBACK, openConvocation, saveConvocation} from '../convocation';

type Action = (id: number) => Promise<void>;
type Choice = 'open' | 'save';

const props = withDefaults(defineProps<{id: number; open?: Action; save?: Action}>(), {
  open: (id: number) => openConvocation(id),
  save: (id: number) => saveConvocation(id),
});

const state = ref<'idle' | 'loading' | 'error'>('idle');
const message = ref('');
const choice = ref<Choice>('open');

async function run(which: Choice) {
  choice.value = which;
  state.value = 'loading';
  message.value = '';
  try {
    await (which === 'open' ? props.open : props.save)(props.id);
    state.value = 'idle';
  } catch (e) {
    message.value = e instanceof ConvocationError ? e.message : FALLBACK;
    state.value = 'error';
  }
}
</script>

<template>
  <div class="convocation">
    <button type="button" class="text-button" :disabled="state === 'loading'" @click="run('open')">
      {{ state === 'loading' && choice === 'open' ? 'Préparation…' : 'Ma convocation (PDF)' }}
    </button>
    <button type="button" class="text-button" :disabled="state === 'loading'" @click="run('save')">
      {{ state === 'loading' && choice === 'save' ? 'Préparation…' : 'Enregistrer' }}
    </button>
    <p v-if="state === 'error'" role="alert" class="error-text">{{ message }}</p>
  </div>
</template>
