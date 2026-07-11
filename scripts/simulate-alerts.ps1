# ============================================================
# Injecte un jeu d'alertes realistes dans SmartSOC (Windows).
# Usage :
#   $env:SMARTSOC_INGEST_API_KEY = "..."
#   .\scripts\simulate-alerts.ps1
# ============================================================
$ErrorActionPreference = "Stop"

$Url = if ($env:SMARTSOC_URL) { $env:SMARTSOC_URL } else { "http://localhost:8080" }
$Key = $env:SMARTSOC_INGEST_API_KEY
if (-not $Key) { throw "SMARTSOC_INGEST_API_KEY must be set" }

$Now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$Headers = @{ "X-API-Key" = $Key; "Content-Type" = "application/json" }

function Send-Alert($Body) {
    try {
        $resp = Invoke-WebRequest -Uri "$Url/api/v1/ingest/alerts" -Method Post -Headers $Headers -Body $Body -UseBasicParsing
        Write-Host "HTTP $($resp.StatusCode)  $((ConvertFrom-Json $Body).title)"
    } catch {
        Write-Host "ECHEC: $($_.Exception.Message)"
    }
}

Write-Host "== SmartSOC alert simulation (run $RunId) vers $Url =="

Send-Alert (@{
    source = "wazuh"; externalId = "sim-$RunId-1"
    title = "sshd: brute force trying to get access to the system"
    description = "Multiple authentication failures on srv-web-01 (root)"
    severity = "HIGH"; detectedAt = $Now
    hostname = "srv-web-01"; ruleId = "5712"
    mitreTechniques = @("T1110")
    rawPayload = @{ rule = @{ id = "5712"; level = 10 }; data = @{ srcip = "203.0.113.42" } }
} | ConvertTo-Json -Depth 5)

Send-Alert (@{
    source = "suricata"; externalId = "sim-$RunId-2"
    title = "ET SCAN Nmap TCP scan detected"
    severity = "MEDIUM"; detectedAt = $Now
    hostname = "fw-dmz-01"; ruleId = "2009582"
    mitreTechniques = @("T1046")
    rawPayload = @{ alert = @{ signature_id = 2009582 }; src_ip = "198.51.100.7" }
} | ConvertTo-Json -Depth 5)

Send-Alert (@{
    source = "wazuh"; externalId = "sim-$RunId-3"
    title = "Windows: multiple failed logons followed by success"
    severity = "CRITICAL"; detectedAt = $Now
    hostname = "win-dc-01"; ruleId = "60204"
    mitreTechniques = @("T1110", "T1078")
    rawPayload = @{ rule = @{ id = "60204"; level = 12 }; win = @{ eventID = "4624" } }
} | ConvertTo-Json -Depth 5)

Write-Host "-- replay du premier evenement (attendu: HTTP 200, pas de doublon) --"
Send-Alert (@{
    source = "wazuh"; externalId = "sim-$RunId-1"
    title = "sshd: brute force trying to get access to the system"
    severity = "HIGH"; detectedAt = $Now
    rawPayload = @{}
} | ConvertTo-Json -Depth 5)

Write-Host "== Termine =="
