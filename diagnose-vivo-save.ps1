# Vivo 设备密码保存问题诊断
Write-Host "=== Vivo 设备 - 密码保存按钮无响应诊断 ===" -ForegroundColor Cyan
Write-Host ""

# 检查设备
Write-Host "1. 检查设备信息..." -ForegroundColor Yellow
$manufacturer = adb shell getprop ro.product.manufacturer 2>&1
$model = adb shell getprop ro.product.model 2>&1
$android = adb shell getprop ro.build.version.release 2>&1

Write-Host "   制造商: $manufacturer" -ForegroundColor Cyan
Write-Host "   型号: $model" -ForegroundColor Cyan
Write-Host "   Android 版本: $android" -ForegroundColor Cyan

if ($manufacturer -match "vivo") {
    Write-Host "   ✅ 已确认为 Vivo 设备" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  非 Vivo 设备" -ForegroundColor Yellow
}

Write-Host ""

# Vivo 特殊权限检查
Write-Host "2. 检查 Vivo 特殊权限..." -ForegroundColor Yellow
Write-Host "   Vivo 设备可能需要额外权限:" -ForegroundColor Yellow
Write-Host "   • 自启动权限" -ForegroundColor Gray
Write-Host "   • 后台运行权限" -ForegroundColor Gray
Write-Host "   • 悬浮窗权限" -ForegroundColor Gray
Write-Host ""

# 清除日志
Write-Host "3. 清除日志并开始监控..." -ForegroundColor Yellow
adb logcat -c
Start-Sleep -Seconds 1

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "🔍 测试步骤" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "请在 Vivo 设备上:" -ForegroundColor Yellow
Write-Host "  1. 打开任意应用的登录页面" -ForegroundColor White
Write-Host "  2. 输入用户名和密码" -ForegroundColor White
Write-Host "  3. 点击登录" -ForegroundColor White
Write-Host "  4. 等待密码保存对话框出现" -ForegroundColor White
Write-Host "  5. 点击'保存'按钮" -ForegroundColor White
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "实时监控日志... (按 Ctrl+C 停止)" -ForegroundColor Green
Write-Host ""

# 状态跟踪
$dialogShown = $false
$buttonClicked = $false
$onSaveCalled = $false
$databaseSaved = $false
$activityFinished = $false

# 监控日志
adb logcat -v time *:V | ForEach-Object {
    $line = $_
    
    # Activity 启动
    if ($line -match "AutofillSaveTransparentActivity 启动") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "① 保存 Activity 启动" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Cyan
        $dialogShown = $true
    }
    
    # BottomSheet 显示
    elseif ($line -match "BottomSheet 已显示") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "② BottomSheet 对话框显示" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Green
    }
    
    # 保存按钮点击
    elseif ($line -match "🔘🔘🔘 保存按钮被点击") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Magenta
        Write-Host "③ 保存按钮被点击!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Magenta
        Write-Host $line -ForegroundColor Magenta
        $buttonClicked = $true
    }
    
    # onSave 回调
    elseif ($line -match "开始密码保存流程") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "④ onSave 回调开始执行" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Cyan
        $onSaveCalled = $true
    }
    
    # 数据库保存
    elseif ($line -match "✅✅✅ 保存新密码成功") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Green
        Write-Host "⑤ 密码保存到数据库成功!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Green
        Write-Host $line -ForegroundColor Green
        $databaseSaved = $true
    }
    
    # onSaveListener 回调
    elseif ($line -match "🎉🎉🎉 onSaveListener 回调触发") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Green
        Write-Host "⑥ onSaveListener 回调触发" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Green
        Write-Host $line -ForegroundColor Magenta
    }
    
    # Activity 关闭
    elseif ($line -match "Activity.finish\(\) 已调用|Activity.onDestroy") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "⑦ Activity 关闭" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Cyan
        $activityFinished = $true
    }
    
    # 其他重要日志
    elseif ($line -match "AutofillSave|BottomSheet|onSave") {
        Write-Host $line -ForegroundColor Gray
    }
    
    # 错误日志
    elseif ($line -match "❌|Exception|Error|error|失败") {
        Write-Host ""
        Write-Host "⚠️⚠️⚠️ 发现错误! ⚠️⚠️⚠️" -ForegroundColor Red
        Write-Host $line -ForegroundColor Red
        Write-Host ""
    }
    
    # Vivo 相关的权限错误
    elseif ($line -match "SecurityException|Permission denied|EACCES") {
        Write-Host ""
        Write-Host "🚨 Vivo 权限问题!" -ForegroundColor Red
        Write-Host $line -ForegroundColor Red
        Write-Host "   → 可能需要在 Vivo 的安全设置中授予额外权限" -ForegroundColor Yellow
        Write-Host ""
    }
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
