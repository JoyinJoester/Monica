# Monica 自动填充服务诊断和修复脚本
# 用于解决 Vivo 设备上日志为空的问题

Write-Host "=== Monica 自动填充服务诊断 ===" -ForegroundColor Cyan
Write-Host ""

# 1. 检查设备连接
Write-Host "【步骤 1/8】检查设备连接..." -ForegroundColor Yellow
$devices = adb devices
if ($devices -match "device$") {
    Write-Host "✅ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "❌ 未检测到设备！请连接设备并启用 USB 调试" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 2. 检查 Monica 是否已安装
Write-Host "【步骤 2/8】检查 Monica 安装状态..." -ForegroundColor Yellow
$monicaInstalled = adb shell pm list packages | Select-String "monica"
if ($monicaInstalled) {
    Write-Host "✅ Monica 已安装: $monicaInstalled" -ForegroundColor Green
} else {
    Write-Host "❌ Monica 未安装！正在安装..." -ForegroundColor Red
    adb install -r app\build\outputs\apk\debug\app-debug.apk
}

Write-Host ""

# 3. 获取设备信息
Write-Host "【步骤 3/8】获取设备信息..." -ForegroundColor Yellow
$manufacturer = adb shell getprop ro.product.manufacturer
$model = adb shell getprop ro.product.model
$android_version = adb shell getprop ro.build.version.release
$sdk_version = adb shell getprop ro.build.version.sdk

Write-Host "设备制造商: $manufacturer" -ForegroundColor Cyan
Write-Host "设备型号: $model" -ForegroundColor Cyan
Write-Host "Android 版本: $android_version (SDK $sdk_version)" -ForegroundColor Cyan

Write-Host ""

# 4. 检查当前自动填充服务
Write-Host "【步骤 4/8】检查当前自动填充服务..." -ForegroundColor Yellow
$currentAutofill = adb shell settings get secure autofill_service
Write-Host "当前自动填充服务: $currentAutofill" -ForegroundColor Cyan

if ($currentAutofill -match "monica") {
    Write-Host "✅ Monica 已设置为自动填充服务" -ForegroundColor Green
} else {
    Write-Host "⚠️  当前自动填充服务不是 Monica，正在修复..." -ForegroundColor Yellow
    adb shell settings put secure autofill_service com.joyinjoystin.monica/.autofill.MonicaAutofillService
    
    # 验证设置
    $newAutofill = adb shell settings get secure autofill_service
    if ($newAutofill -match "monica") {
        Write-Host "✅ 已成功设置 Monica 为自动填充服务" -ForegroundColor Green
    } else {
        Write-Host "❌ 设置失败！需要手动在系统设置中切换" -ForegroundColor Red
        Write-Host "   路径: 设置 → 系统和更新 → 语言和输入法 → 自动填充服务" -ForegroundColor Yellow
    }
}

Write-Host ""

# 5. 检查辅助功能服务
Write-Host "【步骤 5/8】检查辅助功能服务..." -ForegroundColor Yellow
$accessibilityServices = adb shell settings get secure enabled_accessibility_services
Write-Host "已启用的辅助功能: $accessibilityServices" -ForegroundColor Cyan

if ($accessibilityServices -match "monica") {
    Write-Host "✅ Monica 辅助功能已启用" -ForegroundColor Green
} else {
    Write-Host "⚠️  Monica 辅助功能未启用，正在修复..." -ForegroundColor Yellow
    
    # 启用辅助功能
    adb shell settings put secure enabled_accessibility_services com.joyinjoystin.monica/.accessibility.MonicaAccessibilityService
    adb shell settings put secure accessibility_enabled 1
    
    # 验证设置
    $newAccessibility = adb shell settings get secure enabled_accessibility_services
    if ($newAccessibility -match "monica") {
        Write-Host "✅ 已成功启用 Monica 辅助功能" -ForegroundColor Green
    } else {
        Write-Host "❌ 设置失败！需要手动在系统设置中启用" -ForegroundColor Red
        Write-Host "   路径: 设置 → 快捷与辅助 → 无障碍 → Monica" -ForegroundColor Yellow
    }
}

Write-Host ""

# 6. 检查应用权限
Write-Host "【步骤 6/8】检查应用权限..." -ForegroundColor Yellow
$permissions = adb shell dumpsys package com.joyinjoystin.monica | Select-String "permission" | Select-Object -First 10
Write-Host "部分权限信息:" -ForegroundColor Cyan
$permissions | ForEach-Object { Write-Host "  $_" }

Write-Host ""
Write-Host "⚠️  Vivo 设备需要额外授予以下权限:" -ForegroundColor Yellow
Write-Host "   1. 自启动权限: i管家 → 自启动管理 → Monica → 允许" -ForegroundColor White
Write-Host "   2. 后台运行: i管家 → 后台高耗电 → Monica → 允许" -ForegroundColor White
Write-Host "   3. 悬浮窗权限: 设置 → 权限管理 → 悬浮窗 → Monica → 允许" -ForegroundColor White

Write-Host ""

# 7. 清除日志缓存
Write-Host "【步骤 7/8】清除旧日志..." -ForegroundColor Yellow
adb logcat -c
Write-Host "✅ 日志已清除" -ForegroundColor Green

Write-Host ""

# 8. 提供测试指导
Write-Host "【步骤 8/8】准备测试..." -ForegroundColor Yellow
Write-Host ""
Write-Host "=== 设置完成！请按以下步骤测试 ===" -ForegroundColor Green
Write-Host ""
Write-Host "1️⃣  安装测试应用 (推荐):" -ForegroundColor Cyan
Write-Host "   .\gradlew :test-app:assembleDebug" -ForegroundColor White
Write-Host "   adb install -r test-app\build\outputs\apk\debug\test-app-debug.apk" -ForegroundColor White
Write-Host ""
Write-Host "2️⃣  启动实时日志监控:" -ForegroundColor Cyan
Write-Host "   adb logcat -s 'MonicaAutofill:*' 'AutofillSave:*'" -ForegroundColor White
Write-Host ""
Write-Host "3️⃣  在设备上测试:" -ForegroundColor Cyan
Write-Host "   a. 打开测试应用或浏览器" -ForegroundColor White
Write-Host "   b. 点击用户名输入框" -ForegroundColor White
Write-Host "   c. 观察是否弹出 Monica 的自动填充建议" -ForegroundColor White
Write-Host ""
Write-Host "4️⃣  如果仍然没有反应:" -ForegroundColor Cyan
Write-Host "   a. 重启设备: adb reboot" -ForegroundColor White
Write-Host "   b. 手动检查系统设置中的自动填充和辅助功能" -ForegroundColor White
Write-Host "   c. 确保在 i管家 中授予所有必要权限" -ForegroundColor White
Write-Host ""

# 询问是否立即启动日志监控
Write-Host "是否立即启动日志监控? (Y/N)" -ForegroundColor Yellow
$response = Read-Host

if ($response -eq "Y" -or $response -eq "y") {
    Write-Host ""
    Write-Host "=== 启动实时日志监控 ===" -ForegroundColor Green
    Write-Host "请在设备上测试自动填充功能..." -ForegroundColor Cyan
    Write-Host "按 Ctrl+C 停止监控" -ForegroundColor Yellow
    Write-Host ""
    
    adb logcat -s "MonicaAutofill:*" "AutofillSave:*" "AndroidRuntime:E"
} else {
    Write-Host ""
    Write-Host "诊断完成！您可以稍后手动运行日志监控" -ForegroundColor Green
}
