# Monica 密码保存对话框不弹出诊断工具
Write-Host "=== Monica 密码保存对话框不弹出诊断 ===" -ForegroundColor Cyan
Write-Host ""

# 检查设备连接
Write-Host "1. 检查设备连接..." -ForegroundColor Yellow
$adbCheck = adb devices 2>&1
if ($adbCheck -match "device$") {
    Write-Host "   ✅ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "   ❌ 设备未连接" -ForegroundColor Red
    pause
    exit
}

Write-Host ""

# 检查自动填充服务
Write-Host "2. 检查自动填充服务..." -ForegroundColor Yellow
$autofillService = adb shell settings get secure autofill_service 2>&1
Write-Host "   当前服务: $autofillService" -ForegroundColor Cyan

if ($autofillService -match "takagi.ru.monica") {
    Write-Host "   ✅ Monica 自动填充已启用" -ForegroundColor Green
} else {
    Write-Host "   ❌ Monica 自动填充未启用!" -ForegroundColor Red
    Write-Host "   请在: 设置 → 系统 → 语言和输入法 → 自动填充服务 → Monica" -ForegroundColor Yellow
    pause
    exit
}

Write-Host ""

# 清除日志
Write-Host "3. 清除旧日志并开始监控..." -ForegroundColor Yellow
adb logcat -c
Start-Sleep -Seconds 1

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "🔍 诊断说明" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "请在设备上操作:" -ForegroundColor Yellow
Write-Host "  1. 打开任意应用的登录页面" -ForegroundColor White
Write-Host "  2. 点击用户名或密码输入框" -ForegroundColor White
Write-Host "  3. 输入用户名和密码" -ForegroundColor White
Write-Host "  4. 点击登录按钮" -ForegroundColor White
Write-Host ""
Write-Host "关键检查点:" -ForegroundColor Yellow
Write-Host "  ① onFillRequest 是否触发?" -ForegroundColor Gray
Write-Host "  ② addSaveInfo() 是否被调用?" -ForegroundColor Gray
Write-Host "  ③ SaveInfo 是否配置成功?" -ForegroundColor Gray
Write-Host "  ④ onSaveRequest 是否触发?" -ForegroundColor Gray
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "监控中... (按 Ctrl+C 停止)" -ForegroundColor Green
Write-Host ""

# 状态跟踪
$fillRequestSeen = $false
$addSaveInfoSeen = $false
$saveInfoConfigured = $false
$saveRequestTriggered = $false
$lastActivity = Get-Date

# 监控关键日志
adb logcat -v time | ForEach-Object {
    $line = $_
    $now = Get-Date
    
    # ① 检测 FillRequest
    if ($line -match "FILL REQUEST START|onFillRequest called") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "① onFillRequest 触发!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Cyan
        $fillRequestSeen = $true
        $lastActivity = $now
    }
    
    # ② 检测 addSaveInfo 调用
    elseif ($line -match "addSaveInfo\(\) CALLED") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "② addSaveInfo() 被调用!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Magenta
        $addSaveInfoSeen = $true
        $lastActivity = $now
    }
    
    # ③ 检测 SaveInfo 配置
    elseif ($line -match "SaveInfo configured|Login SaveInfo added|NewPassword SaveInfo added") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "③ SaveInfo 配置成功!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Green
        $saveInfoConfigured = $true
        $lastActivity = $now
    }
    
    # ④ 检测 onSaveRequest
    elseif ($line -match "💾💾💾 onSaveRequest TRIGGERED") {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host "④ onSaveRequest 触发!" -ForegroundColor Green
        Write-Host "═══════════════════════════════════════════" -ForegroundColor Yellow
        Write-Host $line -ForegroundColor Magenta
        $saveRequestTriggered = $true
        $lastActivity = $now
    }
    
    # 其他重要日志
    elseif ($line -match "AutofillPicker|SaveInfo|Field hint|password fields|username fields") {
        Write-Host $line -ForegroundColor Gray
    }
    
    # 错误日志
    elseif ($line -match "Error|error|错误|Exception|FATAL") {
        Write-Host $line -ForegroundColor Red
    }
    
    # 警告日志
    elseif ($line -match "No password fields found|SaveInfo NOT configured") {
        Write-Host ""
        Write-Host "⚠️⚠️⚠️ 关键问题!" -ForegroundColor Red
        Write-Host $line -ForegroundColor Red
        Write-Host ""
    }
    
    # 超时检测 - 如果10秒内没有新活动，显示诊断报告
    $elapsed = ($now - $lastActivity).TotalSeconds
    if ($fillRequestSeen -and $elapsed -gt 10) {
        Write-Host ""
        Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
        Write-Host "📊 诊断结果 (10秒无新活动)" -ForegroundColor Yellow
        Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
        Write-Host ""
        
        Write-Host "检查项目:" -ForegroundColor White
        if ($fillRequestSeen) {
            Write-Host "  ✅ ① onFillRequest 触发" -ForegroundColor Green
        } else {
            Write-Host "  ❌ ① onFillRequest 未触发" -ForegroundColor Red
            Write-Host "     → 自动填充服务可能未正确启用" -ForegroundColor Yellow
        }
        
        if ($addSaveInfoSeen) {
            Write-Host "  ✅ ② addSaveInfo() 调用" -ForegroundColor Green
        } else {
            Write-Host "  ❌ ② addSaveInfo() 未调用" -ForegroundColor Red
            Write-Host "     → 代码可能没有调用此方法" -ForegroundColor Yellow
        }
        
        if ($saveInfoConfigured) {
            Write-Host "  ✅ ③ SaveInfo 配置成功" -ForegroundColor Green
        } else {
            Write-Host "  ❌ ③ SaveInfo 未配置" -ForegroundColor Red
            Write-Host "     → 可能没有识别到密码字段" -ForegroundColor Yellow
            Write-Host "     → 检查日志中是否有 'No password fields found'" -ForegroundColor Yellow
        }
        
        if ($saveRequestTriggered) {
            Write-Host "  ✅ ④ onSaveRequest 触发" -ForegroundColor Green
        } else {
            Write-Host "  ❌ ④ onSaveRequest 未触发" -ForegroundColor Red
            if ($saveInfoConfigured) {
                Write-Host "     → SaveInfo 已配置但未触发" -ForegroundColor Yellow
                Write-Host "     → 用户可能没有提交表单?" -ForegroundColor Yellow
                Write-Host "     → 或者应用阻止了保存提示?" -ForegroundColor Yellow
            } else {
                Write-Host "     → 因为 SaveInfo 未配置,所以无法触发" -ForegroundColor Yellow
            }
        }
        
        Write-Host ""
        Write-Host "💡 建议:" -ForegroundColor Green
        
        if (!$addSaveInfoSeen) {
            Write-Host "  • 检查 AutofillPickerLauncher.kt 中 addSaveInfo() 是否被调用" -ForegroundColor Yellow
        }
        
        if ($addSaveInfoSeen -and !$saveInfoConfigured) {
            Write-Host "  • 查看日志中的字段识别结果" -ForegroundColor Yellow
            Write-Host "  • 检查是否有 'password fields: 0' 的日志" -ForegroundColor Yellow
            Write-Host "  • 可能是字段解析问题,密码字段没有被正确识别" -ForegroundColor Yellow
        }
        
        if ($saveInfoConfigured -and !$saveRequestTriggered) {
            Write-Host "  • 确认您已经点击了登录按钮" -ForegroundColor Yellow
            Write-Host "  • 某些应用可能阻止自动填充保存" -ForegroundColor Yellow
            Write-Host "  • 尝试在不同的应用中测试" -ForegroundColor Yellow
        }
        
        Write-Host ""
        Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
        Write-Host ""
        
        # 重置状态准备下次测试
        $fillRequestSeen = $false
        $addSaveInfoSeen = $false
        $saveInfoConfigured = $false
        $saveRequestTriggered = $false
        $lastActivity = $now
    }
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
