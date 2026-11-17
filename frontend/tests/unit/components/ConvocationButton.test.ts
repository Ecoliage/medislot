import {describe, expect, it} from 'vitest';
import {createApp, nextTick} from 'vue';
import ConvocationButton from '../../../src/components/ConvocationButton.vue';
import {ConvocationError, MESSAGES} from '../../../src/convocation';

describe('ConvocationButton', () => {
  function mountButton(open: (id: number) => Promise<void>, save: (id: number) => Promise<void> = async () => {}) {
    const host = document.createElement('div');
    document.body.appendChild(host);
    createApp(ConvocationButton, {id: 12, open, save}).mount(host);
    const buttons = () => Array.from(host.querySelectorAll('button'));
    return {host, buttons};
  }
  const flush = async () => {
    await Promise.resolve();
    await nextTick();
  };

  it('désactive les boutons pendant l’attente', async () => {
    const {buttons} = mountButton(() => new Promise<void>(() => {}));
    buttons()[0].click();
    await nextTick();
    expect(buttons().every(b => b.disabled)).toBe(true);
    expect(buttons()[0].textContent).toContain('Préparation');
  });
  it('affiche l’erreur', async () => {
    const {host, buttons} = mountButton(() => Promise.reject(new ConvocationError(MESSAGES[409], 409)));
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')?.textContent).toContain(MESSAGES[409]);
    expect(buttons().every(b => !b.disabled)).toBe(true);
  });
  it('efface l’erreur après un succès', async () => {
    let attempt = 0;
    const open = () => (attempt++ === 0 ? Promise.reject(new ConvocationError(MESSAGES[500], 500)) : Promise.resolve());
    const {host, buttons} = mountButton(open);
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')).not.toBeNull();
    buttons()[0].click();
    await flush();
    await flush();
    expect(host.querySelector('[role=alert]')).toBeNull();
    expect(buttons().every(b => !b.disabled)).toBe(true);
  });
});
