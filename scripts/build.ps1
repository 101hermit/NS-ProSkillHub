#Requires -Version 5.1
<#
  ProSkillHub 1.0（PSKH）· 构建脚本
  ===========================================================================
  用法：
    powershell -ExecutionPolicy Bypass -File scripts\build.ps1              # 后端 + 前端
    powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -BackendOnly
    powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -WithTests   # 同时跑单测
    powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Offline     # 完全离线（依赖已缓存）

  为什么需要这个脚本而不是直接 mvn：
    1) 真实测试机/仿真机都在公司内网，出外网必须走代理 proxy.neusoft.com:8080，
       且代理做了 TLS 解密 —— JVM 必须加载公司根证书，否则 https 依赖下载一律
       SSLHandshakeException / PKIX path building failed。
    2) 本机默认 Maven 仓库（D:\apache-maven-3.9.0\mvn_repo）对普通会话只读，
       所以本地仓库固定落在 <仓库根>\.m2repo（由 -Dmaven.repo.local 传入）。
#>
param(
  [switch]$BackendOnly,
  [switch]$WithTests,
  [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot    = $ProjectRoot   # 「拿走即用」：所有资产都在本工程内
# 依赖缓存位置：默认本工程 .m2repo（真正「拿走即用」）；
# 开发机上想让多个工程共用一份缓存，设 SKHUB_MAVEN_REPO=<路径> 即可。
$MvnRepo     = if ($env:SKHUB_MAVEN_REPO) { $env:SKHUB_MAVEN_REPO } else { Join-Path $RepoRoot '.m2repo' }
$Settings    = Join-Path $RepoRoot '.mvn\settings.xml'
$TrustStore  = Join-Path $RepoRoot 'config\certs\trust-all.p12'

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Warn2($msg){ Write-Host "  !   $msg" -ForegroundColor Yellow }

# ---------------------------------------------------------------- 环境检查
Write-Step '检查构建环境'

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { throw '找不到 java。怎么办：安装 JDK 17 并配置 PATH。' }
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& java -version 2>&1 | Select-Object -First 1) -join ''
$ErrorActionPreference = $prevEap
if ($javaVersion -notmatch '"(\d+)') { throw "无法识别 Java 版本：$javaVersion" }
$javaMajor = [int]$Matches[1]
if ($javaMajor -lt 17) { throw "需要 JDK 17 或更高，当前是 $javaMajor。怎么办：安装 JDK 17 并把 JAVA_HOME 指过去。" }
Write-Ok "JDK $javaMajor"

$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) { throw '找不到 mvn。怎么办：安装 Maven 3.9 并配置 PATH。' }
Write-Ok 'Maven 可用'

if (-not (Test-Path $MvnRepo)) { New-Item -ItemType Directory -Path $MvnRepo -Force | Out-Null }

# ------------------------------------------------------- 出网代理与证书（自动适配）
# 内网出外网必须走 proxy.neusoft.com:8080，且代理会做 TLS 解密，JVM 必须加载公司根证书，
# 所以 .mvn\settings.xml 里写死了代理。但工程可能被拷到没有该代理的网络里（客户环境、外网、离线机），
# 写死的代理会让每一次依赖下载都 "Connection refused" —— 那是很难自查的坑。
# 因此这里先探一次代理，再决定用哪份设置：
#   代理可达   → 用 .mvn\settings.xml 原样（内网正常路径）
#   代理不可达 → 临时生成一份去掉 <proxies> 的设置给 Maven（客户环境路径），不改动工程文件
$ProxyHost = 'proxy.neusoft.com'
$ProxyPort = 8080

function Test-ProxyReachable {
  $client = $null
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $iar = $client.BeginConnect($ProxyHost, $ProxyPort, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne(600, $false)) { return $false }
    $client.EndConnect($iar)
    return $true
  } catch {
    return $false
  } finally {
    if ($client) { try { $client.Close() } catch { } }
  }
}

$SettingsToUse  = $Settings
$proxyReachable = Test-ProxyReachable
if ($proxyReachable) {
  Write-Ok "出网代理可达（$ProxyHost`:$ProxyPort）"
} else {
  try {
    $raw = [System.IO.File]::ReadAllText($Settings)
    $stripped = [regex]::Replace($raw, '(?s)<proxies>.*?</proxies>', '')
    $SettingsToUse = Join-Path $env:TEMP 'skhub-settings-noproxy.xml'
    [System.IO.File]::WriteAllText($SettingsToUse, $stripped, (New-Object System.Text.UTF8Encoding($false)))
    Write-Warn2 "出网代理 $ProxyHost`:$ProxyPort 不可达（不在公司内网？）—— 已改用无代理的临时 Maven 设置"
  } catch {
    Write-Warn2 "出网代理不可达，且临时设置生成失败：$($_.Exception.Message)。怎么办：手删 .mvn\settings.xml 里的 <proxies> 段。"
  }
}

if (Test-Path $TrustStore) {
  $jvmOpts = @('-Dfile.encoding=UTF-8')
  if ($proxyReachable) {
    $jvmOpts += @(
      "-Dhttps.proxyHost=$ProxyHost", "-Dhttps.proxyPort=$ProxyPort",
      "-Dhttp.proxyHost=$ProxyHost",  "-Dhttp.proxyPort=$ProxyPort"
    )
  }
  $jvmOpts += @("-Djavax.net.ssl.trustStore=$TrustStore", '-Djavax.net.ssl.trustStorePassword=changeit')
  $env:MAVEN_OPTS = ($jvmOpts -join ' ')
  Write-Ok '已加载信任库（JDK CA + 公司根 CA）'
} else {
  Write-Warn2 "缺少信任库 $TrustStore（增量下载依赖可能失败；离线构建不受影响）"
}

# ---------------------------------------------------------------- 前端
if (-not $BackendOnly) {
  Write-Step '构建前端'
  $frontend = Join-Path $ProjectRoot 'frontend'
  $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
  if (-not $nodeCmd) {
    Write-Warn2 '未安装 Node.js —— 跳过前端构建（若 static/ 已有产物则照常可用）'
  } else {
    Push-Location $frontend
    try {
      if (-not (Test-Path 'node_modules')) {
        Write-Host '  安装前端依赖（首次较慢）…'
        $env:npm_config_cache = Join-Path $RepoRoot '.npm-cache'
        & npm install --no-audit --no-fund --ignore-scripts
        if ($LASTEXITCODE -ne 0) { throw 'npm install 失败。怎么办：检查代理与 npm registry（见 docs/05 §2）。' }
      }
      & node build.mjs
      if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
      Write-Ok '前端产物已写入 backend/src/main/resources/static'
    } finally {
      Pop-Location
    }
  }
}

# ---------------------------------------------------------------- 后端
Write-Step '构建后端'
$backend = Join-Path $ProjectRoot 'backend'
Push-Location $backend
try {
  $mvnArgs = @(
    '-s', $SettingsToUse,
    "-Dmaven.repo.local=$MvnRepo",
    '-B',
    'clean', 'package'
  )
  if (-not $WithTests) { $mvnArgs += '-DskipTests' }
  if ($Offline)         { $mvnArgs += '-o' }

  & mvn @mvnArgs
  if ($LASTEXITCODE -ne 0) {
    throw '后端构建失败。怎么办：先试 -Offline；仍失败就看上面第一条 [ERROR]（多半是依赖下载或语法错误）。'
  }
  $jar = Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' |
         Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*original*' } |
         Select-Object -First 1
  Write-Ok ("后端产物：{0}（{1:N1} MB）" -f $jar.Name, ($jar.Length / 1MB))
} finally {
  Pop-Location
}

Write-Host ''
Write-Host '构建完成。双击 start.cmd 启动即可。' -ForegroundColor Green
