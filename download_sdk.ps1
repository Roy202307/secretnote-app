# 下载Android Command Line Tools脚本
Write-Host "Android SDK下载指南" -ForegroundColor Cyan
Write-Host ""

$sdkPath = $env:LOCALAPPDATA + "\Android\Sdk"
Write-Host "推荐SDK安装路径: $sdkPath" -ForegroundColor Yellow
Write-Host ""

Write-Host "手动安装步骤:" -ForegroundColor Green
Write-Host "1. 访问: https://developer.android.com/studio#command-tools" -ForegroundColor White
Write-Host "2. 下载 Command line tools for Windows" -ForegroundColor White
Write-Host "3. 解压到: $sdkPath\cmdline-tools\latest" -ForegroundColor White
Write-Host ""
Write-Host "安装后运行以下命令:" -ForegroundColor Yellow
Write-Host "  cd $sdkPath\cmdline-tools\latest\bin" -ForegroundColor White
Write-Host "  .\sdkmanager.bat --sdk_root=$sdkPath 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'" -ForegroundColor White
Write-Host ""
