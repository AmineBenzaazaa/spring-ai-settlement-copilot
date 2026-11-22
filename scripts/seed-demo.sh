#!/bin/sh
# Puts the settlement platform in a "lived-in" state for the copilot demo:
#   1. atlas uploads the sample Speedex remittance CSV (October 2026), which gets reconciled
#   2. atlas opens disputes on the short payment and on the parcel missing from the file
# Idempotent: the platform deduplicates the CSV by SHA-256, and disputes are only opened once.
# POSIX sh + curl + grep/sed only, so it runs in the tiny curlimages/curl container.
set -eu

API="${SETTLEMENT_API:-http://localhost:18080}"
CSV="${SAMPLE_CSV:-$(dirname "$0")/../fixtures/sample_remittance.csv}"
USER_NAME="${SEED_USER:-atlas}"
PASSWORD="${SEED_PASSWORD:-demo-pass-123}"

echo "seed: waiting for $API"
i=0
until curl -fsS "$API/actuator/health" >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -gt 90 ] && { echo "seed: API not up"; exit 1; }; sleep 2
done

TOKEN=$(curl -fsS -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "seed: login failed"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

UPLOAD=$(curl -fsS -X POST "$API/api/statements" -H "$AUTH" -F "file=@$CSV" -F courierId=1 \
  -F periodStart=2026-10-01 -F periodEnd=2026-10-31)
STATEMENT_ID=$(echo "$UPLOAD" | sed -n 's/.*"statement":{"id":\([0-9]*\).*/\1/p')
echo "seed: statement #$STATEMENT_ID"

i=0
until curl -fsS "$API/api/statements/$STATEMENT_ID" -H "$AUTH" | grep -q '"status":"RECONCILED"'; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "seed: statement not reconciled"; exit 1; }; sleep 1
done

if curl -fsS "$API/api/disputes" -H "$AUTH" | grep -q '"totalElements":0'; then
  REPORT=$(curl -fsS "$API/api/statements/$STATEMENT_ID/reconciliation" -H "$AUTH")
  MISMATCH=$(echo "$REPORT" | grep -o '"id":[0-9]*,"outcome":"AMOUNT_MISMATCH"' | head -1 | sed 's/"id":\([0-9]*\).*/\1/')
  MISSING=$(echo "$REPORT" | grep -o '"id":[0-9]*,"outcome":"MISSING_FROM_STATEMENT"' | head -1 | sed 's/"id":\([0-9]*\).*/\1/')
  curl -fsS -X POST "$API/api/disputes" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"resultId\":$MISMATCH,\"reason\":\"Courier short-paid 50 MAD on this parcel\"}" >/dev/null
  curl -fsS -X POST "$API/api/disputes" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"resultId\":$MISSING,\"reason\":\"Delivered parcel missing from the October statement\"}" >/dev/null
  echo "seed: opened disputes on results $MISMATCH and $MISSING"
else
  echo "seed: disputes already exist, skipping"
fi
echo "seed: done"
