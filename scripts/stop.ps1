#Requires -Version 5.1
<#
  ProSkillHub 1.0（PSKH）· 停止脚本
  按端口定位监听进程（用 netstat，避免受限会话里 Get-NetTCPConnection 查不到监听者），
  确认是 java 之后才结束，并等待端口真正释放。
#>
param([int]$Port = 0)

$ErrorActionPreference = 'Continue'
if ($Port -le 0) { $Port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 30802 } }

function Get-PortListeners([int]$p) {
  $pids = @()
  $lines = netstat -ano 2>$null | Select-String ":$p\s"
  foreach ($l in $lines) {
    $parts = ($l.Line -split '\s+') | Where-Object { $_ }
    if ($parts.Count -ge 5 -and $parts[-2] -eq 'LISTENING') { $pids += [int]$parts[-1] }
  }
  return ($pids | Select-Object -Unique)
}

Write-Host "==> 查找端口 $Port 上的监听进程" -ForegroundColor Cyan
$targets = Get-PortListeners -p $Port
if ($targets.Count -eq 0) {
  Write-Host "  OK  端口 $Port 上没有监听进程（可能已经停了）" -ForegroundColor Green
  exit 0
}

$killed = 0
foreach ($ownerPid in $targets) {
  $proc = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
  if (-not $proc) { continue }
  if ($proc.ProcessName -ne 'java') {
    Write-Host "  !  端口 $Port 被非 java 进程占用（PID $ownerPid / $($proc.ProcessName)），跳过" -ForegroundColor Yellow
    continue
  }
  Write-Host "  结束 java 进程 PID $ownerPid …"
  Stop-Process -Id $ownerPid -Force -ErrorAction SilentlyContinue
  $killed++
}

for ($i = 0; $i -lt 25; $i++) {
  Start-Sleep -Milliseconds 300
  if ((Get-PortListeners -p $Port).Count -eq 0) { break }
}

if ((Get-PortListeners -p $Port).Count -gt 0) {
  Write-Host "  !  端口 $Port 仍被占用，请手工检查" -ForegroundColor Yellow
  exit 1
}
Write-Host "  OK  已停止（$killed 个进程），端口 $Port 已释放" -ForegroundColor Green
exit 0
