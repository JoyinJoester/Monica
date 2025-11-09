# Monica 密码自动保存诊断工具
Write-Host "=== Monica 密码自动保存诊断工具 ===" -ForegroundColor Cyan
Write-Host ""

# 检查 ADB 连接
Write-Host "1. 检查设备连接..." -ForegroundColor Yellow
$adbDevices = adb devices
if ($adbDevices -match "device$") {
    Write-Host "   ✅ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "   ❌ 没有检测到设备" -ForegroundColor Red
    Write-Host "   请连接设备并启用 USB 调试" -ForegroundColor Yellow
    pause
    exit
}

Write-Host ""

# 检查 Monica 是否已安装
Write-Host "2. 检查 Monica 应用状态..." -ForegroundColor Yellow
$packageInfo = adb shell pm list packages takagi.ru.monica
if ($packageInfo -match "takagi.ru.monica") {
    Write-Host "   ✅ Monica 已安装" -ForegroundColor Green
} else {
    Write-Host "   ❌ Monica 未安装" -ForegroundColor Red
    pause
    exit
}

Write-Host ""

# 检查自动填充服务状态
Write-Host "3. 检查自动填充服务状态..." -ForegroundColor Yellow
$autofillService = adb shell settings get secure autofill_service
Write-Host "   当前自动填充服务: $autofillService" -ForegroundColor Cyan

if ($autofillService -match "takagi.ru.monica") {
    Write-Host "   ✅ Monica 自动填充服务已启用" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Monica 自动填充服务未启用" -ForegroundColor Yellow
    Write-Host "   请在设置中启用 Monica 的自动填充服务" -ForegroundColor Gray
}

Write-Host ""

# 清除日志并开始监控
Write-Host "4. 开始监控日志..." -ForegroundColor Yellow
Write-Host "   清除旧日志..." -ForegroundColor Gray
adb logcat -c

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "📋 诊断说明:" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "请按照以下步骤测试密码保存功能:" -ForegroundColor Yellow
Write-Host ""
Write-Host "步骤 1: 打开任意应用的登录页面" -ForegroundColor White
Write-Host "步骤 2: 输入用户名和密码" -ForegroundColor White
Write-Host "步骤 3: 点击登录按钮" -ForegroundColor White
Write-Host "步骤 4: 等待 Monica 保存提示出现" -ForegroundColor White
Write-Host ""
Write-Host "关键检查点:" -ForegroundColor Yellow
Write-Host "  ✓ 是否出现 '💾💾💾 onSaveRequest TRIGGERED!' 日志?" -ForegroundColor Gray
Write-Host "  ✓ 是否显示密码保存弹窗?" -ForegroundColor Gray
Write-Host "  ✓ 点击保存后是否有 '✅ 保存新密码成功!' 日志?" -ForegroundColor Gray
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "监控中... (按 Ctrl+C 停止)" -ForegroundColor Green
Write-Host ""

# 监控关键日志
adb logcat -v time | Select-String -Pattern "MonicaAutofill|AutofillSave|onSaveRequest" | ForEach-Object {
    $line = $_.Line
    
    # 高亮重要日志
    if ($line -match "💾💾💾 onSaveRequest TRIGGERED") {
        Write-Host $line -ForegroundColor Magenta
        Write-Host "   >>> 🎯 保存请求已触发!" -ForegroundColor Green
    } elseif ($line -match "保存新密码成功|保存密码成功") {
        Write-Host $line -ForegroundColor Green
        Write-Host "   >>> ✅ 密码已保存到数据库!" -ForegroundColor Green
    } elseif ($line -match "Error|error|错误|失败") {
        Write-Host $line -ForegroundColor Red
    } elseif ($line -match "SavePasswordBottomSheetContent") {
        Write-Host $line -ForegroundColor Yellow
        Write-Host "   >>> 🎨 保存界面已显示!" -ForegroundColor Cyan
    } elseif ($line -match "AutofillSaveTransparentActivity|AutofillSaveBottomSheet") {
        Write-Host $line -ForegroundColor Cyan
    } else {
        Write-Host $line -ForegroundColor Gray
    }
}
