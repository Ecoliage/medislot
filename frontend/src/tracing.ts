import {context, propagation, trace} from '@opentelemetry/api';
import {W3CTraceContextPropagator} from '@opentelemetry/core';
import {resourceFromAttributes} from '@opentelemetry/resources';
import {ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION, ATTR_URL_FULL} from '@opentelemetry/semantic-conventions';
import {
  BatchSpanProcessor,
  StackContextManager,
  TracerProvider,
  type Span,
  type SpanProcessor,
} from '@opentelemetry/sdk-trace';
import {OTLPTraceExporter} from '@opentelemetry/exporter-trace-otlp-http';
import {registerInstrumentations} from '@opentelemetry/instrumentation';
import {FetchInstrumentation} from '@opentelemetry/instrumentation-fetch';

export interface TracingOptions {
  processor?: SpanProcessor;
  version?: string;
}
export interface Tracing {
  provider: TracerProvider;
  stop(): Promise<void>;
}

// la saisie de recherche est une donnée personnelle : on retire la query string avant l’export
export class QueryStripper implements SpanProcessor {
  onStart(span: Span): void {
    const url = span.attributes[ATTR_URL_FULL];
    if (typeof url === 'string' && url.includes('?')) span.setAttribute(ATTR_URL_FULL, url.slice(0, url.indexOf('?')));
  }
  onEnd(): void {}
  forceFlush(): Promise<void> {
    return Promise.resolve();
  }
  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}

export function demarrerTraces(options: TracingOptions = {}): Tracing {
  const resource = resourceFromAttributes({
    [ATTR_SERVICE_NAME]: 'medislot-web',
    [ATTR_SERVICE_VERSION]: options.version ?? '1.0.0',
  });
  const exportProcessor =
    options.processor ??
    new BatchSpanProcessor({
      exporter: new OTLPTraceExporter({url: `${location.origin}/otlp/v1/traces`}),
      scheduledDelayMillis: 5000,
    });
  const provider = new TracerProvider({resource, spanProcessors: [new QueryStripper(), exportProcessor]});
  trace.setGlobalTracerProvider(provider);
  context.setGlobalContextManager(new StackContextManager().enable());
  propagation.setGlobalPropagator(new W3CTraceContextPropagator());
  const unregister = registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        // pas de trace sur l’export des traces, les flux d’événements ni le géocodage externe
        ignoreUrls: [/\/otlp\//, /\/api\/events/, /\/notifications\/events/, /data\.geopf\.fr/],
      }),
    ],
  });
  return {
    provider,
    async stop() {
      unregister();
      await provider.shutdown();
      trace.disable();
      context.disable();
      propagation.disable();
    },
  };
}
