#!/usr/bin/env pwsh
<#
.SYNOPSIS
    MasteryPath-Java 唯一验证入口。

.DESCRIPTION
    编译 -> 分层测试 -> 落日志 -> 打印下一步。
    证据由本脚本产出，编辑端不得预填结论。

.PARAMETER scope
    all | unit | storage | api

.PARAMETER change
    绑定到某个 change id（用于命名日志与提示回填的报告）

.EXAMPLE
    .\scripts\verify.ps1 --change 0001-java-skeleton

.EXAMPLE
    .\scripts\verify.ps1 --scope unit
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'unit', 'storage', 'api')]
    [string]$Scope = 'all',

    [string]$Change = 'local'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- JDK 解析 -------------------------------------------------------------
# 环境里可能存在指向已删除目录的 JAVA_HOME（例如 VS Code 扩展自带的 JRE）。
# 这里做一次校验与回退，避免每次构建都因环境问题失败。
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
    Write-Host '[FAIL] 找不到可用 JDK（需要 21+，含 javac）。请安装 JDK 或设置 JAVA_HOME。'
    exit 2
}
if ($resolvedHome -ne $env:JAVA_HOME) {
    Write-Host "[INFO] JAVA_HOME 无效或缺失，改用: $resolvedHome"
    $env:JAVA_HOME = $resolvedHome
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$artifactDir = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$log = Join-Path $artifactDir "verify-$Change-$stamp.log"

function Write-Log([string]$message) {
    $message | Tee-Object -FilePath $log -Append
}

function Get-JavaVersionLine {
    # java -version 把版本写到 stderr；在 ErrorActionPreference=Stop 下会被当作终止错误，故局部放宽。
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $line = (& java -version 2>&1 | Select-Object -First 1)
        return "$line".Trim()
    } catch {
        return 'unknown'
    } finally {
        $ErrorActionPreference = $previous
    }
}

Write-Log '== MasteryPath-Java verify =='
Write-Log "change : $Change"
Write-Log "scope  : $Scope"
Write-Log "log    : $log"
Write-Log "java   : $(Get-JavaVersionLine)"
Write-Log "javahome: $env:JAVA_HOME"
Write-Log ''

$mvnw = Join-Path $root 'mvnw.cmd'
if (-not (Test-Path $mvnw)) {
    Write-Log "[FAIL] 未找到 mvnw.cmd：本仓库要求用 Maven Wrapper 构建。"
    exit 2
}

$goals = @('-B', '-ntp', 'test')

switch ($Scope) {
    'unit'    { $goals += @('-Dtest=*PolicyTest,*OrderingTest,*ScoreTest,*GradingTest,*SchedulerTest,*SchedulingTest,*RepairTest,*ProfileTest,*PathStateTest,*PathBindingTest,*NavigationTest,*LlmClientTest,*SseParserTest,*CompatibleClientTest,*AgentLoopTest,*ModelTest,*JsonTest,*ProjectionTest,*ProjectorTest,*ClassificationTest,*ModeTest,*ModeGateTest,*BudgetTest,*CoverageTest,*GapTest,*OutlineBuildTest,*OutlineReviseTest,*QuizSpecTest,*QuizRegistrationTest,*GradeFlowTest,*QuestionBankTest,*AssessTest,*BuildReviseTest,*QuizGradeTest,*CaptureTest,*OutlineEvalTest,*LifecycleTest,*AttachmentTest,*OverrideServiceTest,*ObjectiveStateTest') }
    'storage' { $goals += @('-Dtest=*StoreTest,*StoreConcurrencyTest,*CompatibilityTest,*PreservationTest,*DeletionTest') }
    'api'     { $goals += @('-Dtest=*ApiTest,*WebSocketTurnTest,*TurnRunnerTest,*RestTest') }
    'all'     { }
}

Write-Log "command: mvnw.cmd $($goals -join ' ')"
Write-Log ''

# 构建工具把进度与警告写到 stderr。若沿用 ErrorActionPreference=Stop，
# 这些行会被当成终止错误，导致「测试全绿但脚本判失败」。此处只按退出码判定。
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $mvnw @goals 2>&1 | Tee-Object -FilePath $log -Append
$code = $LASTEXITCODE
$ErrorActionPreference = $previous

Write-Log ''
if ($code -eq 0) {
    Write-Log '[PASS] 测试通过。'
} else {
    Write-Log "[FAIL] 测试失败，退出码 $code。先读日志定位；最多自愈 3 轮；禁止放宽断言。"
}

$reportDir = Join-Path $root 'target\surefire-reports'
if (Test-Path $reportDir) {
    Write-Log "结果包: $reportDir"
}

Write-Log ''
Write-Log "下一步：回填 docs/verify/$Change.md，并追加 docs/regression-log.md 一行。"

exit $code
