import {afterEach, describe, expect, it} from 'vitest';
import {createApp, h, nextTick, reactive, type App} from 'vue';
import BaseModal from '../../../../src/components/ui/BaseModal.vue';

const mounted: App[] = [];

function mount(component: Parameters<typeof createApp>[0]) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(component);
  mounted.push(app);
  app.mount(host);
}

function button(name: string, root: ParentNode) {
  const found = [...root.querySelectorAll<HTMLButtonElement>('button')]
    .find(element => element.textContent?.trim() === name || element.getAttribute('aria-label') === name);
  if (!found) throw new Error(`Bouton absent : ${name}`);
  return found;
}

function dialog() {
  const found = document.querySelector<HTMLElement>('[role="dialog"]');
  if (!found) throw new Error('Dialogue absent');
  return found;
}

afterEach(() => {
  mounted.splice(0).forEach(app => app.unmount());
  document.body.replaceChildren();
});

describe('BaseModal', () => {
  function openModal(busy = false) {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    trigger.focus();
    const state = reactive({open: true, busy});
    mount({
      setup: () => () => state.open ? h(BaseModal, {
        busy: state.busy,
        onClose: () => { state.open = false; },
      }, {
        default: () => [h('h2', {id: 'modal-title'}, 'Titre'), h('input'), h('button', 'Dernière action')],
      }) : null,
    });
    return {trigger, state};
  }

  it('contient Tab et Maj+Tab puis restaure le focus après Échap', async () => {
    const {trigger} = openModal();
    await nextTick();
    await nextTick();
    const first = button('Fermer', dialog());
    const last = button('Dernière action', dialog());
    expect(document.activeElement).toBe(first);
    first.dispatchEvent(new KeyboardEvent('keydown', {key: 'Tab', shiftKey: true, bubbles: true}));
    expect(document.activeElement).toBe(last);
    last.dispatchEvent(new KeyboardEvent('keydown', {key: 'Tab', bubbles: true}));
    expect(document.activeElement).toBe(first);
    first.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    await nextTick();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('bloque la fermeture pendant une opération en cours', async () => {
    const {state} = openModal(true);
    await nextTick();
    document.querySelector<HTMLElement>('.overlay')!.click();
    dialog().dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    await nextTick();
    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    expect(button('Fermer', dialog()).disabled).toBe(true);
    state.busy = false;
    await nextTick();
    dialog().dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    await nextTick();
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
});
