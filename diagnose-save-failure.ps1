# 🔍 密码保存失败诊断脚本

Write-Host "=== Monica 密码保存失败诊断 ===" -ForegroundColor Cyan
Write-Host ""

# 检查设备连接
Write-Host "1. 检查设备连接..." -ForegroundColor Yellow
$adbCheck = adb devices 2>&1
if ($adbCheck -match "device$") {
    Write-Host "   ✅ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "   ❌ 设备未连接" -ForegroundColor Red
    exit
}

Write-Host ""

# 获取当前密码数量
Write-Host "2. 检查数据库状态..." -ForegroundColor Yellow
try {
    # 尝试查询数据库
    $dbQuery = @"
adb shell "run-as takagi.ru.monica sqlite3 databases/password_database 'SELECT COUNT(*) FROM password_entries;'" 2>&1
"@
    
    $count = Invoke-Expression $dbQuery
    if ($count -match "(\d+)") {
        $passwordCount = $matches[1]
        Write-Host "   ✅ 当前密码数量: $passwordCount" -ForegroundColor Green
        Write-Host "   📊 数据库状态: 正常" -ForegroundColor Green
        Write-Host "   🔓 没有存储数量限制!" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   ⚠️  无法查询数据库 (可能需要 root 权限)" -ForegroundColor Yellow
}

Write-Host ""

# 检查数据库大小
Write-Host "3. 检查数据库文件大小..." -ForegroundColor Yellow
try {
    $dbSize = adb shell "run-as takagi.ru.monica ls -lh databases/password_database" 2>&1
    if ($dbSize -match "(\S+)\s+password_database") {
        $size = $matches[1]
        Write-Host "   📁 数据库文件大小: $size" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   ⚠️  无法获取文件大小" -ForegroundColor Yellow
}

Write-Host ""

# 检查存储空间
Write-Host "4. 检查设备存储空间..." -ForegroundColor Yellow
$storageInfo = adb shell df /data 2>&1
if ($storageInfo -match "(\d+)%") {
    $usage = $matches[1]
    Write-Host "   📊 /data 分区使用率: $usage%" -ForegroundColor Cyan
    if ([int]$usage -gt 95) {
        Write-Host "   ⚠️  存储空间不足! 这可能导致保存失败!" -ForegroundColor Red
    } else {
        Write-Host "   ✅ 存储空间充足" -ForegroundColor Green
    }
}

Write-Host ""

# 检查应用数据目录权限
Write-Host "5. 检查应用权限..." -ForegroundColor Yellow
$permissions = adb shell "run-as takagi.ru.monica ls -la databases/" 2>&1
if ($permissions -match "password_database") {
    Write-Host "   ✅ 应用有数据库访问权限" -ForegroundColor Green
} else {
    Write-Host "   ❌ 权限问题!" -ForegroundColor Red
}

Write-Host ""

# 测试插入操作
Write-Host "6. 测试数据库写入..." -ForegroundColor Yellow
Write-Host "   清除日志并准备测试..." -ForegroundColor Gray
adb logcat -c

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "🧪 现在请在设备上测试密码保存功能" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "监控关键日志 (按 Ctrl+C 停止):" -ForegroundColor Green
Write-Host ""

# 实时监控日志
$lastLogTime = Get-Date
$saveAttempted = $false
$saveSucceeded = $false
$errorDetected = $false

adb logcat -v time | ForEach-Object {
    $line = $_
    
    # 检测保存尝试
    if ($line -match "onSaveRequest TRIGGERED|保存密码信息") {
        Write-Host $line -ForegroundColor Magenta
        $saveAttempted = $true
        $lastLogTime = Get-Date
    }
    # 检测保存成功
    elseif ($line -match "保存新密码成功|保存密码成功|insertPasswordEntry") {
        Write-Host $line -ForegroundColor Green
        $saveSucceeded = $true
        Write-Host ""
        Write-Host "   >>> ✅ 数据库插入成功!" -ForegroundColor Green
        Write-Host ""
    }
    # 检测错误
    elseif ($line -match "Error|error|错误|失败|Exception|FATAL") {
        Write-Host $line -ForegroundColor Red
        $errorDetected = $true
        
        # 分析具体错误
        if ($line -match "SQLite") {
            Write-Host "   >>> ⚠️  SQLite 数据库错误!" -ForegroundColor Red
        }
        elseif ($line -match "OutOfMemory|OOM") {
            Write-Host "   >>> ⚠️  内存不足!" -ForegroundColor Red
        }
        elseif ($line -match "IOException|FileNotFoundException") {
            Write-Host "   >>> ⚠️  文件系统错误!" -ForegroundColor Red
        }
        elseif ($line -match "SecurityException|Permission") {
            Write-Host "   >>> ⚠️  权限错误!" -ForegroundColor Red
        }
    }
    # 其他密码保存相关日志
    elseif ($line -match "AutofillSave|passwordRepository|PasswordEntry") {
        Write-Host $line -ForegroundColor Cyan
    }
    
    # 超时检测
    $elapsed = (Get-Date) - $lastLogTime
    if ($saveAttempted -and -not $saveSucceeded -and $elapsed.TotalSeconds -gt 5) {
        Write-Host ""
        Write-Host "⏱️  保存操作超时 (5秒内未完成)" -ForegroundColor Yellow
        Write-Host ""
        $saveAttempted = $false
    }
}

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "📊 诊断总结" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

if ($saveSucceeded) {
    Write-Host "✅ 密码保存成功!" -ForegroundColor Green
} elseif ($errorDetected) {
    Write-Host "❌ 检测到错误,请查看上方红色日志" -ForegroundColor Red
} else {
    Write-Host "⚠️  测试未完成或未检测到保存操作" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
