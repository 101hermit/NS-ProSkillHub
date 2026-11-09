#Requires -Version 5.1
<#
  ProSkillHub 1.0（PSKH）· 一键启动
  ===========================================================================
  行为序列（与 docs/01 §7 一致）：
    1. 检查 JDK >= 17、Maven 可用
    2. 检查端口（被占用则报出占用进程；可用 -Port 换端口）
    3. 缺 jar 或源码比 jar 新 → 调 scripts/build.ps1 构建
    4. 用正确的 JVM 参数启动（UTF-8 + 出网代理 + 公司根证书）
    5. 轮询 /api/v1/health，ready 后打开浏览器
    6. 失败时打印最后 40 行日志 + 三条最可能的原因

  环境变量（都可选）：
    SKHUB_DB=mysql           走 MySQL 8（默认 H2）
    DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD
    SKHUB_NO_BROWSER=1       不自动开浏览器
#>
param(
  [int]$Port = 0,
  [switch]$NoBuild,
  [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot    = $ProjectRoot   # 「拿走即用」：所有资产都在本工程内
$JarPath     = Join-Path $ProjectRoot 'backend\target\pskh.jar'
$LogDir      = Join-Path $ProjectRoot 'logs'
$TrustStore  = Join-Path $RepoRoot 'config\certs\trust-all.p12'

if ($Port -le 0) { $Port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 30802 } }
if ($env:SKHUB_NO_BROWSER -eq '1') { $NoBrowser = $true }
# 「拿走即用」：客户机上通常没有 Maven，或不想现场构建 —— 设 SKHUB_SKIP_BUILD=1 直接用现成 jar
if ($env:SKHUB_SKIP_BUILD -eq '1') { $NoBuild = $true }

function Test-PortFree([int]$Port) {
  # 用"能不能绑定"来判断：比读 netstat 更可靠（受限会话里 netstat 会给出误导性结果）
  $listener = $null
  try {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Any, $Port)
    $listener.Start()
    return $true
  } catch {
    return $false
  } finally {
    if ($listener) { try { $listener.Stop() } catch { } }
  }
}
function Get-PortListeners([int]$p) {
  # 用 netstat 而不是 Get-NetTCPConnection：受限会话里后者可能查不到监听者
  $pids = @()
  $lines = netstat -ano 2>$null | Select-String ":$p\s"
  foreach ($l in $lines) {
    $parts = ($l.Line -split '\s+') | Where-Object { $_ }
    if ($parts.Count -ge 5 -and $parts[-2] -eq 'LISTENING') { $pids += [int]$parts[-1] }
  }
  return ($pids | Select-Object -Unique)
}
function Write-Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Write-Warn2($m){ Write-Host "  !   $m" -ForegroundColor Yellow }
function Fail($m, $code = 1) { Write-Host "`n[启动失败] $m" -ForegroundColor Red; exit $code }

# ---------------------------------------------------------------- 环境
Write-Step '检查运行环境'
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { Fail '找不到 java。怎么办：安装 JDK 17 并配置 PATH。' 2 }
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& java -version 2>&1 | Select-Object -First 1) -join ''
$ErrorActionPreference = $prevEap
if ($javaVersion -notmatch '"(\d+)') { Fail "无法识别 Java 版本：$javaVersion" 2 }
$javaMajor = [int]$Matches[1]
if ($javaMajor -lt 17) { Fail "需要 JDK 17 或更高，当前是 $javaMajor。怎么办：安装 JDK 17（真实测试机基线 17.0.20）。" 2 }
Write-Ok "JDK $javaMajor"

# ---------------------------------------------------------------- 端口
if (-not (Test-PortFree -Port $Port)) {
  $listeners = Get-PortListeners -Port $Port
  $ownerPid = if ($listeners.Count -gt 0) { $listeners[0] } else { 0 }
  $proc = if ($ownerPid) { Get-Process -Id $ownerPid -ErrorAction SilentlyContinue } else { $null }
  $name = if ($proc) { "$($proc.ProcessName) PID $ownerPid" } else { '未知进程' }
  Fail "端口 $Port 已被占用（$name）。怎么办：先跑 stop.cmd，或换个端口：start.cmd 30810" 3
}
Write-Ok "端口 $Port 可用"

# ---------------------------------------------------------------- 产物
Write-Step '检查构建产物'
$needsBuild = (-not (Test-Path $JarPath))
if (-not $needsBuild) {
  $jarTime = (Get-Item $JarPath).LastWriteTimeUtc
  $newest = Get-ChildItem (Join-Path $ProjectRoot 'backend\src') -Recurse -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
  if ($newest -and $newest.LastWriteTimeUtc -gt $jarTime) { $needsBuild = $true }
}
# 客户机上通常没有 Maven：只要产物在，就不要为了"源码更新"强行构建。
# 这条判断必须放在 build.ps1 之前 —— 否则 build.ps1 会抛"找不到 mvn"，一次本可成功的启动就变成了失败。
if ($needsBuild -and -not $NoBuild -and -not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Warn2 '没有检测到 Maven —— 跳过构建，直接使用现成 jar（之后改了源码也不会生效）'
  $needsBuild = $false
}

if ($needsBuild -and -not $NoBuild) {
  Write-Warn2 '产物缺失或源码已更新，开始构建（首次约 1 分钟）…'
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'build.ps1')
  if ($LASTEXITCODE -ne 0) { Fail '构建失败。怎么办：单独跑 scripts\build.ps1 看第一条 [ERROR]。' 4 }
  $needsBuild = $false
}
if (-not (Test-Path $JarPath)) { Fail "找不到 $JarPath。怎么办：跑 scripts\build.ps1 生成（首次需要 Maven 与网络）；或把已有的 backend\target 目录一起拷过来。" 4 }
Write-Ok 'jar 已就绪'

# ---------------------------------------------------------------- 启动
Write-Step "启动服务（端口 $Port）"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logFile = Join-Path $LogDir "pskh-$stamp.log"

$jvmArgs = @(
  '-Dfile.encoding=UTF-8',
  '-Dsun.stdout.encoding=UTF-8',
  '-Dsun.stderr.encoding=UTF-8',
  "-Dserver.port=$Port"
)
# 资源站不发起外部调用，因此不需要代理/信任库；这里只在存在时显式声明"不注入"，避免误解

$env:SPRING_PROFILES_ACTIVE = if ($env:SKHUB_DB -eq 'mysql') { 'mysql' } else { '' }

$argList = @()
if ($env:SPRING_PROFILES_ACTIVE) { $argList += "-Dspring.profiles.active=$($env:SPRING_PROFILES_ACTIVE)" }
$argList += $jvmArgs
$argList += @('-jar', $JarPath)

$proc = Start-Process -FilePath 'java' -ArgumentList $argList -WorkingDirectory $ProjectRoot `
        -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" -PassThru -WindowStyle Hidden
Write-Ok "进程已启动 (PID $($proc.Id))，日志：$logFile"

# ---------------------------------------------------------------- 健康检查
Write-Step '等待服务就绪（最多 90 秒）'
$url = "http://127.0.0.1:$Port"
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 1
  if ($proc.HasExited) { break }
  try {
    $resp = Invoke-WebRequest -Uri "$url/api/v1/health" -UseBasicParsing -TimeoutSec 2
    if ($resp.StatusCode -eq 200) { $ready = $true; break }
  } catch { }
}

if (-not $ready) {
  Write-Host ''
  Write-Host '--- 最后 40 行日志 ---' -ForegroundColor Yellow
  if (Test-Path $logFile)        { Get-Content $logFile -Tail 40 }
  if (Test-Path "$logFile.err")  { Get-Content "$logFile.err" -Tail 20 }
  Write-Host ''
  Write-Host '三条最可能的原因：' -ForegroundColor Yellow
  Write-Host '  1) 端口被占用（换端口：start.cmd 30810）'
  Write-Host '  2) 数据库文件被占用（先 stop.cmd 再启动）'
  Write-Host '  3) skills/ 目录缺失（从母本仓库执行 node tools/sync-skill-library.mjs 重新同步；本工程自带副本一般不会缺）'
  Fail '90 秒内没有等到 ready=true。' 5
}

# ---------------------------------------------------------------- 首次启动：等 Skill 库导完
# 「能访问」比「库导完」早约 7 秒。若此时就宣布就绪，用户打开的是一个空站点，
# 会误判为"装好了但坏了"。所以这里按 /health 的 library.state 明确等待并打印进度。
$h = $null
$libState = 'unknown'
$libNote = ''
for ($i = 0; $i -lt 150; $i++) {
  try { $h = (Invoke-WebRequest -Uri "$url/api/v1/health" -UseBasicParsing -TimeoutSec 3).Content | ConvertFrom-Json } catch { }
  $lib = if ($h) { $h.data.extra.library } else { $null }
  if ($lib) {
    $libState = "$($lib.state)"
    if ($libState -eq 'running') {
      $p = 0; $t = 0
      try { $p = [int]$lib.progress; $t = [int]$lib.total } catch { }
      Write-Host ("      首次启动：正在导入 Skill 库 {0}/{1} …" -f $p, $t) -ForegroundColor DarkGray
      Start-Sleep -Seconds 1
      continue
    }
    if ($libState -eq 'failed') { $libNote = "（库导入失败：$($lib.message)）"; break }
    if ($libState -eq 'done') { $libNote = "（本次导入 $($lib.progress) 个 Skill）"; break }
    if ($libState -eq 'skipped') { $libNote = '（库已存在，未重复导入）'; break }
  }
  Start-Sleep -Seconds 1
}

try {
  Write-Ok ("就绪：dialect={0} skills={1} library={2} 库导入={3} {4}" -f `
    $h.data.dialect, $h.data.skill_count, $h.data.library_version, $libState, $libNote)
} catch { }

Write-Host ''
Write-Host "  ProSkillHub 已启动：$url" -ForegroundColor Green
Write-Host '  停止服务：双击 stop.cmd' -ForegroundColor DarkGray
Write-Host ''

if (-not $NoBrowser) { Start-Process $url }
exit 0
