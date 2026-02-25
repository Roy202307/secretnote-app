# 构建APK脚本
Write-Host "正在准备构建APK..." -ForegroundColor Green

# 设置JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"

# 设置ANDROID_HOME (需要您修改为实际路径)
$env:ANDROID_HOME = $env:LOCALAPPDATA + "\Android\Sdk"

# 设置PATH
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\build-tools\34.0.0;$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
Write-Host ""

# 检查是否安装了Android SDK
if (-not (Test-Path $env:ANDROID_HOME)) {
    Write-Host "错误: 未找到Android SDK" -ForegroundColor Red
    Write-Host "请先安装Android SDK或修改脚本中的ANDROID_HOME路径" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "推荐安装方式:" -ForegroundColor Cyan
    Write-Host "1. 访问 https://developer.android.com/tools"
    Write-Host "2. 下载 Command line tools only"
    Write-Host "3. 解压到: $env:ANDROID_HOME"
    Write-Host ""
    exit 1
}

# 尝试使用Android SDK构建
$aapt = "$env:ANDROID_HOME\build-tools\34.0.0\aapt.exe"
if (Test-Path $aapt) {
    Write-Host "找到Android SDK,开始构建..." -ForegroundColor Green
    & $aapt version
} else {
    Write-Host "警告: 未找到build-tools" -ForegroundColor Yellow
    Write-Host "请运行sdkmanager安装build-tools: sdkmanager 'build-tools;34.0.0'"
    exit 1
}

Write-Host ""
Write-Host "构建准备完成!" -ForegroundColor Green
Write-Host "请运行: .\gradlew.bat assembleDebug" -ForegroundColor Cyan
