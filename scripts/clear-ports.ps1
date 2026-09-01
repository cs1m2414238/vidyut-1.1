# Vidyut EV - PowerShell Port Cleaner
$ports = @(8001, 8080, 8081, 8082, 5173, 5174, 5175, 3000, 5000, 19000, 19006)

Write-Host "`n🧹 [Vidyut] Clearing ports: $($ports -join ', ')..." -ForegroundColor Cyan

$freed = 0
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($conn in $connections) {
        $pidToKill = $conn.OwningProcess
        if ($pidToKill -and $pidToKill -ne 0 -and $pidToKill -ne $PID) {
            try {
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                Write-Host "  ✓ Terminated PID $pidToKill on port $port" -ForegroundColor Green
                $freed++
            } catch {}
        }
    }
}

if ($freed -eq 0) {
    Write-Host "✨ All ports are already clear!" -ForegroundColor Yellow
} else {
    Write-Host "✅ Freed $freed port process(es). Ready for 'npm run dev'!`n" -ForegroundColor Green
}
