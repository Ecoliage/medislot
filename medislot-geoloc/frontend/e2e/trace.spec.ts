import {test, expect} from '@playwright/test';

const JAEGER = process.env.JAEGER_URL || 'http://localhost:16686';

interface SpanJaeger {
  operationName: string;
  processID: string;
  spanID: string;
  references: {refType: string; spanID: string}[];
}
interface TraceJaeger {
  spans: SpanJaeger[];
  processes: Record<string, {serviceName: string}>;
}

test('une recherche produit une trace front puis back', async ({page, request}) => {
  await page.goto('/');
  await expect(page.getByText(/praticiens? à découvrir/)).toBeVisible();
  const requete = page.waitForRequest(r => r.url().includes('/api/professionals?') && r.url().includes('q=Martin'));
  await page.getByLabel('Nom du praticien').fill('Martin');
  await page.getByRole('button', {name: /Rechercher/}).click();
  const traceparent = (await requete).headers()['traceparent'];
  expect(traceparent, 'traceparent absent').toBeDefined();
  expect(traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
  const [, traceId, spanIdNavigateur] = traceparent.split('-');

  const lireTrace = async (): Promise<TraceJaeger | null> => {
    const reponse = await request.get(`${JAEGER}/api/traces/${traceId}`);
    if (!reponse.ok()) return null;
    const corps = await reponse.json();
    return corps.data?.[0] ?? null;
  };
  const services = (t: TraceJaeger | null) => Object.values(t?.processes ?? {}).map(p => p.serviceName);

  await expect
    .poll(async () => services(await lireTrace()), {timeout: 30000, intervals: [1000]})
    .toEqual(expect.arrayContaining(['medislot-web', 'medislot-api']));

  const trace = (await lireTrace())!;
  const spanBack = trace.spans.find(
    s => trace.processes[s.processID].serviceName === 'medislot-api' && s.operationName.startsWith('GET'),
  );
  expect(spanBack, 'aucun span GET du back dans la trace').toBeDefined();
  expect(spanBack!.references.some(ref => ref.refType === 'CHILD_OF' && ref.spanID === spanIdNavigateur)).toBe(true);
});
