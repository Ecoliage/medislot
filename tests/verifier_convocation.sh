#!/usr/bin/env bash
# Vérifie la convocation PDF sur la pile compose démarrée (front sur 8088 par défaut).
set -u
cd "$(dirname "$0")/.."
APP="${APP_URL:-http://localhost:8088}"
MDP="${DEMO_PASSWORD:-MediSlot-demo-2026!}"
PATIENT1="${PATIENT1:-patient@medislot.test}"
PATIENT2="${PATIENT2:-autre@medislot.test}"
CABINET_HEADER="X-Clinic: ${CABINET:-cabinet-a}"
SORTIE="$(mktemp -d)"
JAR1="$SORTIE/patient1.cookies"
JAR2="$SORTIE/patient2.cookies"

# json CHEMIN < fichier : lit une valeur du JSON, chemin à points (items.0.id).
# Échoue si le chemin n'existe pas.
if command -v jq > /dev/null; then
  json() { jq -er --arg p "$1" 'getpath($p | split(".") | map(tonumber? // .))'; }
else
  json() {
    python3 -c '
import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)' "$1"
  }
fi

csrf() {
  curl -s -H "$CABINET_HEADER" -c "$1" -b "$1" "$APP/api/csrf" > "$SORTIE/csrf.json"
  CSRF_HEADER="$(json headerName < "$SORTIE/csrf.json")"
  CSRF_TOKEN="$(json token < "$SORTIE/csrf.json")"
}

connexion() {
  local jar="$1" email="$2" code
  csrf "$jar"
  code=$(curl -s -H "$CABINET_HEADER" -o /dev/null -w '%{http_code}' -c "$jar" -b "$jar" -H "$CSRF_HEADER: $CSRF_TOKEN" \
    --data-urlencode "username=$email" --data-urlencode "password=$MDP" "$APP/api/login")
  echo "Connexion de $email : $code"
  csrf "$jar"
}

# Réserve le premier créneau libre parmi les dix premiers du premier praticien
# et affiche l'id du rendez-vous.
reserver() {
  local jar="$1" praticien id code
  csrf "$jar"
  praticien=$(curl -s -H "$CABINET_HEADER" "$APP/api/professionals?page=0&size=6" | json items.0.id)
  curl -s -H "$CABINET_HEADER" "$APP/api/professionals/$praticien/slots" > "$SORTIE/slots.json"
  for i in 0 1 2 3 4 5 6 7 8 9; do
    id=$(json "$i.id" < "$SORTIE/slots.json" 2> /dev/null) || break
    code=$(curl -s -H "$CABINET_HEADER" -o "$SORTIE/rdv.json" -w '%{http_code}' -c "$jar" -b "$jar" -H "$CSRF_HEADER: $CSRF_TOKEN" \
      -H 'Content-Type: application/json' -d "{\"slotId\":$id}" "$APP/api/appointments")
    if [ "$code" = "201" ]; then json id < "$SORTIE/rdv.json"; return 0; fi
  done
  return 1
}

entetes() {
  curl -s -H "$CABINET_HEADER" -D - -o /dev/null -b "$1" "$2" | tr -d '\r' \
    | grep -i -E "^HTTP/|^content-type|^content-disposition|^cache-control|^content-length"
}

corps() {
  curl -s -H "$CABINET_HEADER" -b "$1" "$2"
  echo
}

echo "== Connexions"
connexion "$JAR1" "$PATIENT1"
connexion "$JAR2" "$PATIENT2"

echo
echo "== Réservation par $PATIENT1"
RDV=$(reserver "$JAR1") || { echo "Aucun créneau réservable"; exit 1; }
echo "Rendez-vous n° $RDV"
URL="$APP/api/appointments/$RDV/convocation.pdf"

echo
echo "== 1. Propriétaire, affichage (inline)"
entetes "$JAR1" "$URL"

echo
echo "== 1 bis. Propriétaire, enregistrement (download=true), nom de fichier choisi par le serveur"
entetes "$JAR1" "$URL?download=true"
( cd "$SORTIE" && curl -s -H "$CABINET_HEADER" -OJ -b "$JAR1" "$URL?download=true" && ls -la convocation-*.pdf && file convocation-*.pdf )

echo
echo "== 2. Second patient, même numéro de rendez-vous"
entetes "$JAR2" "$URL"
corps "$JAR2" "$URL"

echo
echo "== 3. Annulation puis nouvelle demande"
csrf "$JAR1"
curl -s -H "$CABINET_HEADER" -o /dev/null -w 'Annulation : %{http_code}\n' -b "$JAR1" -c "$JAR1" -H "$CSRF_HEADER: $CSRF_TOKEN" -X POST "$APP/api/appointments/$RDV/cancel"
entetes "$JAR1" "$URL"
corps "$JAR1" "$URL"

echo
echo "== 4. Sans session"
curl -s -H "$CABINET_HEADER" -D - -o /dev/null "$URL" | tr -d '\r' | grep -i -E "^HTTP/|^content-type"
curl -s -H "$CABINET_HEADER" "$URL"
echo

echo
echo "Fichiers temporaires : $SORTIE"
