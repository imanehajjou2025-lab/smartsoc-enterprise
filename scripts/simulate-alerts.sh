#!/usr/bin/env bash
# ============================================================
# Injecte un jeu d'alertes realistes multi-sources dans SmartSOC
# (demo sans infrastructure SOC, ADR-005).
# Usage :
#   SMARTSOC_URL=http://localhost:8080 \
#   SMARTSOC_INGEST_API_KEY=... \
#   ./scripts/simulate-alerts.sh
# ============================================================
set -euo pipefail

URL="${SMARTSOC_URL:-http://localhost:8080}"
KEY="${SMARTSOC_INGEST_API_KEY:?SMARTSOC_INGEST_API_KEY must be set}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RUN_ID="$(date +%s)"

send() {
  local body="$1"
  code=$(curl -s -o /tmp/smartsoc-ingest-resp.json -w "%{http_code}" \
    -X POST "$URL/api/v1/ingest/alerts" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $KEY" \
    -d "$body")
  title=$(echo "$body" | grep -o '"title": *"[^"]*"' | head -1)
  echo "HTTP $code  $title"
}

echo "== SmartSOC alert simulation (run $RUN_ID) vers $URL =="

send '{
  "source": "wazuh", "externalId": "sim-'"$RUN_ID"'-1",
  "title": "sshd: brute force trying to get access to the system",
  "description": "Multiple authentication failures on srv-web-01 (root)",
  "severity": "HIGH", "detectedAt": "'"$NOW"'",
  "hostname": "srv-web-01", "ruleId": "5712",
  "mitreTechniques": ["T1110"],
  "rawPayload": {"rule": {"id": "5712", "level": 10}, "agent": {"name": "srv-web-01"}, "data": {"srcip": "203.0.113.42"}}
}'

send '{
  "source": "suricata", "externalId": "sim-'"$RUN_ID"'-2",
  "title": "ET SCAN Nmap TCP scan detected",
  "description": "Port scan from 198.51.100.7 against DMZ segment",
  "severity": "MEDIUM", "detectedAt": "'"$NOW"'",
  "hostname": "fw-dmz-01", "ruleId": "2009582",
  "mitreTechniques": ["T1046"],
  "rawPayload": {"alert": {"signature_id": 2009582, "category": "Attempted Information Leak"}, "src_ip": "198.51.100.7"}
}'

send '{
  "source": "wazuh", "externalId": "sim-'"$RUN_ID"'-3",
  "title": "Windows: multiple failed logons followed by success",
  "description": "Possible credential stuffing on WIN-DC-01 (user: svc-backup)",
  "severity": "CRITICAL", "detectedAt": "'"$NOW"'",
  "hostname": "win-dc-01", "ruleId": "60204",
  "mitreTechniques": ["T1110", "T1078"],
  "rawPayload": {"rule": {"id": "60204", "level": 12}, "win": {"eventID": "4624"}}
}'

send '{
  "source": "shuffle", "externalId": "sim-'"$RUN_ID"'-4",
  "title": "VirusTotal: malicious hash detected on endpoint",
  "description": "SHA256 flagged 47/70 by VT during automated enrichment",
  "severity": "HIGH", "detectedAt": "'"$NOW"'",
  "hostname": "lt-hr-007", "ruleId": "vt-enrich",
  "mitreTechniques": ["T1204"],
  "rawPayload": {"vt_score": "47/70", "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}
}'

send '{
  "source": "suricata", "externalId": "sim-'"$RUN_ID"'-5",
  "title": "SURICATA HTTP unable to match response to request",
  "severity": "INFO", "detectedAt": "'"$NOW"'",
  "hostname": "fw-dmz-01", "ruleId": "2221010",
  "rawPayload": {"alert": {"signature_id": 2221010}}
}'

# Replay volontaire du premier evenement : doit renvoyer 200 (idempotence)
echo "-- replay du premier evenement (attendu: HTTP 200, pas de doublon) --"
send '{
  "source": "wazuh", "externalId": "sim-'"$RUN_ID"'-1",
  "title": "sshd: brute force trying to get access to the system",
  "severity": "HIGH", "detectedAt": "'"$NOW"'",
  "rawPayload": {}
}'

echo "== Termine =="
