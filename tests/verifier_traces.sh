#!/usr/bin/env bash
# Vérifie la chaîne de traces (navigateur, Nginx, collecteur, Jaeger). La pile compose doit tourner.
set -u
cd "$(dirname "$0")/.."
APP="${APP_URL:-http://localhost:8088}"
JAEGER="${JAEGER_URL:-http://localhost:16686}"
ok=0
echec=0

# point LIBELLÉ CODE : affiche OK si CODE vaut 0 (le $? de la commande juste avant)
point() {
  if [ "$2" = "0" ]; then echo "  OK     $1"; ok=$((ok + 1)); else echo "  ÉCHEC  $1"; echec=$((echec + 1)); fi
}

echo "1. La route /otlp/ de Nginx relaie le navigateur vers le collecteur"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' "$APP/otlp/v1/traces")
[ "$code" = "200" ]
point "POST $APP/otlp/v1/traces répond $code (200 attendu)" $?

echo "2. La JVM du back a chargé l'agent OpenTelemetry"
docker compose logs backend 2>/dev/null | grep -q "opentelemetry-javaagent - version"
point "docker compose logs backend contient la version de l'agent" $?

# Le collecteur groupe les spans par lots de 2 s, d'où l'attente avant d'interroger Jaeger
echo "3. Une requête au back arrive dans Jaeger sous medislot-api"
curl -s -o /dev/null -H "X-Clinic: ${CABINET:-cabinet-a}" "$APP/api/professionals"
sleep 7
curl -s "$JAEGER/api/v3/services" | grep -q '"medislot-api"'
point "Jaeger propose le service medislot-api" $?

echo "Bilan : $ok OK, $echec ÉCHEC"
[ "$echec" = "0" ]
