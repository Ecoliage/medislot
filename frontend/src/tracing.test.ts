import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {InMemorySpanExporter, SimpleSpanProcessor} from '@opentelemetry/sdk-trace';
import {demarrerTraces, type Tracing} from './tracing';

let exporter: InMemorySpanExporter;
let tracing: Tracing;
let fetchMock: Mock;

async function finishedSpan() {
  await vi.waitFor(() => expect(exporter.getFinishedSpans().length).toBeGreaterThan(0), {timeout: 3000});
  return exporter.getFinishedSpans()[0];
}

describe('Traçage', () => {
  beforeEach(() => {
    exporter = new InMemorySpanExporter();
    fetchMock = vi.fn().mockResolvedValue(new Response('{}', {status: 200}));
    vi.stubGlobal('fetch', fetchMock);
    tracing = demarrerTraces({processor: new SimpleSpanProcessor({exporter: exporter})});
  });
  afterEach(async () => {
    await tracing.stop();
    vi.unstubAllGlobals();
  });

  it('nomme le service medislot-web', async () => {
    await fetch('/api/professionals');
    const span = await finishedSpan();
    expect(span.resource.attributes['service.name']).toBe('medislot-web');
  });

  it('envoie le header traceparent', async () => {
    await fetch('/api/professionals?q=martin');
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const traceparent = new Headers(init.headers).get('traceparent');
    expect(traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
    const span = await finishedSpan();
    expect(traceparent!.split('-')[1]).toBe(span.spanContext().traceId);
  });

  it('retire la query string de url.full', async () => {
    await fetch('/api/professionals?q=dupont&city=Lyon');
    const span = await finishedSpan();
    const url = String(span.attributes['url.full']);
    expect(url).not.toContain('dupont');
    expect(url).not.toContain('Lyon');
    expect(url).toContain('/api/professionals');
  });
});
