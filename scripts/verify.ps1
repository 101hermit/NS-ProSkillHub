#Requires -Version 5.1
<#
  ProSkillHub 1.0（PSKH）· 一键验收
  ===========================================================================
  用法：
    powershell -ExecutionPolicy Bypass -File scripts\verify.ps1
    powershell -ExecutionPolicy Bypass -File scripts\verify.ps1 -NoPages      # 只跑接口
    powershell -ExecutionPolicy Bypass -File scripts\verify.ps1 -Port 30810

  判据是退出码：0 = 全绿（可交付），1 = 有用例失败，2 = 环境不满足。
#>
param(
  [int]$Port = 0,
  [switch]$NoPages
)

$ErrorActionPreference = 'Continue'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot    = $ProjectRoot   # 「拿走即用」：所有资产都在本工程内
if ($Port -le 0) { $Port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 30802 } }
$Base = "http://127.0.0.1:$Port"

Write-Host "==> 检查服务是否在线：$Base" -ForegroundColor Cyan
$online = $false
for ($i = 0; $i -lt 10; $i++) {
  try {
    $r = Invoke-WebRequest -Uri "$Base/api/v1/health" -UseBasicParsing -TimeoutSec 2
    if ($r.StatusCode -eq 200) { $online = $true; break }
  } catch { Start-Sleep -Seconds 1 }
}
if (-not $online) {
  Write-Host "  !  服务没起来。先跑 start.cmd（或 scripts\start.ps1）再验收。" -ForegroundColor Yellow
  exit 2
}
Write-Host "  OK  服务在线" -ForegroundColor Green

$failed = 0

# ------------------------------------------------------------------ 接口
Write-Host ''
Write-Host '==> L3 接口验收（29 项）' -ForegroundColor Cyan
& node (Join-Path $PSScriptRoot 'api-test.mjs') --system pro --base $Base
if ($LASTEXITCODE -ne 0) { $failed++ }

# ------------------------------------------------------------------ 页面
if (-not $NoPages) {
  Write-Host ''
  Write-Host '==> L4 页面验收（jsdom，11 项 × 21 路由）' -ForegroundColor Cyan
  $frontend = Join-Path $ProjectRoot 'frontend'
  if (-not (Test-Path (Join-Path $frontend 'node_modules\jsdom'))) {
    Write-Host '  !  前端依赖未安装，跳过页面验收（cd frontend; npm install --ignore-scripts）' -ForegroundColor Yellow
  } else {
    Push-Location $frontend
    try {
      # 专业版页面最多：库语义 + 实测 + 接入 + 连接矩阵 + 实验台 + 知识图谱 + 指标
      # /eval/matrix/:code 需要真实矩阵编号，不放进冒烟（见 docs/04 §6）
      foreach ($route in @('/', '/login', '/skills', '/risk', '/docs', '/about', '/contributors', '/contribute', '/ms', '/ms/objects', '/ms/indexes', '/ms/constraints', '/ms/events', '/ms/query', '/eval/live', '/eval/quality', '/access', '/conn', '/eval/lab', '/kg', '/ix')) {
        & node 'test\smoke.mjs' --base $Base --route $route | Out-Null
        if ($LASTEXITCODE -ne 0) {
          Write-Host "  ✘ 路由 $route 页面验收失败" -ForegroundColor Red
          & node 'test\smoke.mjs' --base $Base --route $route | Select-Object -Last 14
          $failed++
        } else {
          Write-Host "  ✔ 路由 $route" -ForegroundColor Green
        }
      }
    } finally { Pop-Location }
  }
}

# ------------------------------------------------------------------ 库质检
Write-Host ''
Write-Host '==> L5 Skill 库质检（三工程一致性）' -ForegroundColor Cyan
& node (Join-Path $PSScriptRoot 'check-library.mjs')
if ($LASTEXITCODE -ne 0) { $failed++ }

Write-Host ''
if ($failed -eq 0) {
  Write-Host '验收通过：可以交付。' -ForegroundColor Green
  exit 0
} else {
  Write-Host "验收未通过：$failed 个环节有问题。看上面第一条 ✘。" -ForegroundColor Red
  exit 1
}
