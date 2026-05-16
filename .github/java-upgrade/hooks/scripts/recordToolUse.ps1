# Records run_in_terminal and appmod-* tool calls as JSONL for the extension to process.

$raw = [Console]::In.ReadToEnd()

if ($raw -notmatch '"tool_name"\s*:\s*"([^"]+)"') { exit 0 }
$toolName = $Matches[1]

if ($toolName -ne 'run_in_terminal' -and $toolName -notlike 'appmod-*') { exit 0 }

if ($raw -notmatch '"session_id"\s*:\s*"([^"]+)"') { exit 0 }
$sessionId = $Matches[1]

$hooksDir = '.github\java-upgrade\hooks'
if (-not (Test-Path $hooksDir)) { New-Item -ItemType Directory -Path $hooksDir -Force | Out-Null }

$line = ($raw -replace '[\r\n]+', ' ').Trim() + "`n"
[System.IO.File]::AppendAllText("$hooksDir\$sessionId.json", $line, [System.Text.UTF8Encoding]::new($false))
