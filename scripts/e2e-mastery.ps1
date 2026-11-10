#!/usr/bin/env pwsh
<#
.SYNOPSIS
    真实模型掌握之路 E2E（默认回归不包含它）。

.DESCRIPTION
    使用 data/settings.json 里已配置的模型，经真实 HTTP/WebSocket 走完一条掌握之路。
    会产生真实模型费用；默认只跑一次，不重试。

.PARAMETER DataDir
    含 settings.json 的数据目录，默认 data。

.PARAMETER MaxTurns
    学习者最多发言轮数，默认 18。

.PARAMETER Keep
    保留本次数据目录到 artifacts/e2e，便于用同一目录启动后端人工复核。

.EXAMPLE
    .\scripts\e2e-mastery.ps1

.EXAMPLE
    .\scripts\e2e-mastery.ps1 -MaxTurns 12 -Keep
#>
[CmdletBinding()]
param(
    [string]$DataDir = 'data',

    [int]$MaxTurns = 18,

    [switch]$Keep
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) {
        $binDir = Split-Path -Parent $onPath.Source
        $jdkDir = Split-Path -Parent $binDir
        if (Test-Path (Join-Path $jdkDir 'bin\javac.exe')) { return $jdkDir }
    }
    foreach ($base in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Amazon Corretto")) {
        if (-not (Test-Path $base)) { continue }
        $candidate = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    return $null
}

$resolvedHome = Resolve-JavaHome
if (-not $resolvedHome) {
    Write-Host '[FAIL] 找不到可用 JDK（需要 21+，含 javac）。'
    exit 2
}
$env:JAVA_HOME = $resolvedHome
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$settings = Join-Path $DataDir 'settings.json'
if (-not (Test-Path $settings)) {
    Write-Host "[FAIL] 未找到 $settings。请先在设置页配置一个可用模型。"
    exit 2
}

$env:MASTERY_E2E = '1'
$env:MASTERY_E2E_DATA_DIR = $DataDir
$env:MASTERY_E2E_MAX_TURNS = "$MaxTurns"
$env:MASTERY_E2E_KEEP = if ($Keep) { '1' } else { '0' }

Write-Host '[INFO] 真实模型 E2E 会调用外部模型并产生费用；本轮不重试。'
Write-Host "[INFO] data dir : $DataDir"
Write-Host "[INFO] max turns: $MaxTurns"

$mvnw = Join-Path $root 'mvnw.cmd'
& $mvnw -B -ntp '-Dgroups=e2e' '-Dtest=MasteryJourneyE2ETest' test
$code = $LASTEXITCODE

$artifacts = Join-Path $root 'target\e2e-artifacts'
if (Test-Path $artifacts) {
    Write-Host "[INFO] transcript: $artifacts"
}
if ($code -eq 0) {
    Write-Host '[PASS] 真实模型掌握之路完成。'
} else {
    Write-Host "[FAIL] E2E 失败（退出码 $code）。先读 transcript 定位，不要放宽断言。"
}
exit $code
