# 快速修复脚本 - 一键设置 Monica 自动填充服务

Write-Host "=== Monica 自动填充快速修复 ===" -ForegroundColor Cyan
Write-Host ""

# 设置自动填充服务
Write-Host "⚙️  正在设置自动填充服务..." -ForegroundColor Yellow
adb shell settings put secure autofill_service com.joyinjoystin.monica/.autofill.MonicaAutofillService

# 启用辅助功能
Write-Host "⚙️  正在启用辅助功能..." -ForegroundColor Yellow
adb shell settings put secure enabled_accessibility_services com.joyinjoystin.monica/.accessibility.MonicaAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 验证设置
Write-Host ""
Write-Host "=== 验证设置 ===" -ForegroundColor Cyan
Write-Host ""

$autofill = adb shell settings get secure autofill_service
Write-Host "自动填充服务: $autofill" -ForegroundColor White

$accessibility = adb shell settings get secure enabled_accessibility_services
Write-Host "辅助功能服务: $accessibility" -ForegroundColor White

Write-Host ""

if ($autofill -match "monica") {
    Write-Host "✅ 自动填充服务设置成功" -ForegroundColor Green
} else {
    Write-Host "❌ 自动填充服务设置失败" -ForegroundColor Red
    Write-Host "   请手动设置: 设置 → 语言和输入法 → 自动填充服务" -ForegroundColor Yellow
}

if ($accessibility -match "monica") {
    Write-Host "✅ 辅助功能设置成功" -ForegroundColor Green
} else {
    Write-Host "❌ 辅助功能设置失败" -ForegroundColor Red
    Write-Host "   请手动设置: 设置 → 无障碍 → Monica" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== 接下来 ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 建议重启设备: adb reboot" -ForegroundColor Yellow
Write-Host "2. 启动日志监控: adb logcat -s 'MonicaAutofill:*'" -ForegroundColor Yellow
Write-Host "3. 在设备上测试自动填充功能" -ForegroundColor Yellow
Write-Host ""
