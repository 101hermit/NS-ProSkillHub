#Requires -Version 5.1
<#
  「拿走即用」自检：确认本工程不依赖任何外部路径。
  ===========================================================================
  判据（全部通过才算可单独交付）：
    1. 必需的本地资产都在：skills/ · .mvn/settings.xml · config/certs · scripts/*
    2. 脚本里没有指向父目录的外部引用（..\..、..\.mvn、..\config、..\tools）
    3. 后端产物存在（或可构建），前端产物已进 static/
    4. Skill 库完整（交给 check-library.mjs）
  用法：powershell -ExecutionPolicy Bypass -File scripts\check-portable.ps1
  退出码：0 可单独交付 / 1 有问题
#>
$ErrorActionPreference = 'Continue'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$failed = 0

Write-Host '==> 1) 本地资产' -ForegroundColor Cyan
$assets = @(
  'skills\catalog.json',
  'backend\pom.xml',
  'backend\src\main\resources\db\schema.sql',
  '.mvn\settings.xml',
  'config\certs\ca-bundle.pem',
  'config\certs\trust-all.p12',
  'scripts\build.ps1',
  'scripts\start.ps1',
  'scripts\stop.ps1',
  'scripts\verify.ps1',
  'scripts\api-test.mjs',
  'scripts\check-library.mjs',
  'start.cmd',
  'stop.cmd',
  'README.md',
  'PRODUCT-SPEC.md'
)
foreach ($a in $assets) {
  if (Test-Path (Join-Path $ProjectRoot $a)) { Write-Host "  OK  $a" -ForegroundColor Green }
  else { Write-Host "  !   缺 $a" -ForegroundColor Yellow; $failed++ }
}

Write-Host '==> 2) 脚本无外部路径引用' -ForegroundColor Cyan
# 注意：跳过本文件自身。它上面的注释里写着"要检查 ..\..、..\.mvn 这些串"，
# 把自己也算进去就成了"自证其罪"的假阳性 —— 自检脚本必须先能通过自己。
$selfName = 'check-portable.ps1'
$bad = @()
foreach ($f in Get-ChildItem (Join-Path $ProjectRoot 'scripts') -Filter *.ps1) {
  if ($f.Name -eq $selfName) { continue }
  $hits = Select-String -Path $f.FullName -Pattern '(\.\.\\\.\.|\.\./\.\.|\.\.\\\.mvn|\.\.\\config|\.\.\\tools)' -ErrorAction SilentlyContinue
  if ($hits) { $bad += "$($f.Name):$($hits[0].LineNumber)" }
}
if ($bad.Count -eq 0) { Write-Host '  OK  没有指向父目录的引用' -ForegroundColor Green }
else { Write-Host "  !   $($bad -join ', ')" -ForegroundColor Yellow; $failed++ }

Write-Host '==> 3) 构建产物' -ForegroundColor Cyan
$jar = Get-ChildItem (Join-Path $ProjectRoot 'backend\target') -Filter *.jar -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*original*' } | Select-Object -First 1
if ($jar) { Write-Host "  OK  后端产物 $($jar.Name)（$([math]::Round($jar.Length/1MB,1)) MB）" -ForegroundColor Green }
else { Write-Host '  !   没找到后端 jar（跑 scripts\build.ps1 即可生成）' -ForegroundColor Yellow; $failed++ }
if (Test-Path (Join-Path $ProjectRoot 'backend\src\main\resources\static\assets\app.js')) {
  Write-Host '  OK  前端产物已在 static/' -ForegroundColor Green
} else { Write-Host '  !   缺前端产物（跑 scripts\build.ps1，或需要 node）' -ForegroundColor Yellow; $failed++ }

Write-Host '==> 4) Skill 库' -ForegroundColor Cyan
& node (Join-Path $PSScriptRoot 'check-library.mjs')
if ($LASTEXITCODE -ne 0) { $failed++ }

Write-Host ''
if ($failed -eq 0) {
  Write-Host '结论：本工程可单独拷走并运行。' -ForegroundColor Green
  exit 0
}
Write-Host "结论：$failed 项需要处理（见上面 ! 行）。" -ForegroundColor Red
exit 1