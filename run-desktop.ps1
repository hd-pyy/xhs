# XHS Downloader 桌面端启动脚本
# 用法：在 PowerShell 里跑
#   .\run-desktop.ps1 run          # 启动桌面 GUI
#   .\run-desktop.ps1 dist         # 打 .exe
#   .\run-desktop.ps1 test         # 跑 commonMain 单元测试
#   .\run-desktop.ps1 core-desktop # 只编译 :core 的 desktop target

param(
    [Parameter(Mandatory=$true)][ValidateSet('run','dist','test','core-desktop')]$Task
)

$ErrorActionPreference = 'Stop'

# 1. 强制使用 Android Studio 自带的 JDK 17（避免 Gradle daemon 用上系统的 JDK 11）
$jdk17 = 'C:\Program Files (x86)\Android\openjdk\jdk-17.0.8.101-hotspot'
if (-not (Test-Path $jdk17)) {
    Write-Error "找不到 JDK 17：$jdk17。请先装 Android Studio 或改这个变量。"
}
$env:JAVA_HOME = $jdk17
$env:PATH = "$jdk17\bin;$env:PATH"

# 2. 切到本仓库根目录
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "PWD       = $(Get-Location)" -ForegroundColor Cyan
Write-Host "Task      = $Task" -ForegroundColor Cyan
Write-Host ""

switch ($Task) {
    'run' {
        & .\gradlew.bat :desktop:run --no-daemon
    }
    'dist' {
        & .\gradlew.bat :desktop:createDistributable --no-daemon
    }
    'test' {
        & .\gradlew.bat :core:desktopTest --no-daemon
    }
    'core-desktop' {
        & .\gradlew.bat :core:compileKotlinDesktop --no-daemon
    }
}
