# Monica APK 签名诊断工具
# 用于检查和比较 APK 签名,找出数据丢失的原因

Write-Host "=== Monica APK 签名诊断工具 ===" -ForegroundColor Cyan
Write-Host ""

# 检查 ADB 连接
Write-Host "1. 检查 ADB 连接..." -ForegroundColor Yellow
$adbDevices = adb devices
if ($adbDevices -match "device$") {
    Write-Host "   ✅ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "   ❌ 没有检测到设备,请连接设备并启用 USB 调试" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 检查 Monica 是否已安装
Write-Host "2. 检查 Monica 是否已安装..." -ForegroundColor Yellow
$packageInfo = adb shell pm list packages takagi.ru.monica
if ($packageInfo -match "takagi.ru.monica") {
    Write-Host "   ✅ Monica 已安装" -ForegroundColor Green
    
    # 获取版本信息
    $versionInfo = adb shell dumpsys package takagi.ru.monica | Select-String "versionCode|versionName"
    Write-Host "   版本信息:" -ForegroundColor Cyan
    Write-Host "   $versionInfo" -ForegroundColor White
} else {
    Write-Host "   ⚠️  Monica 未安装" -ForegroundColor Yellow
}

Write-Host ""

# 导出已安装的 APK
Write-Host "3. 导出已安装的 APK..." -ForegroundColor Yellow
$apkPath = adb shell pm path takagi.ru.monica | Select-String -Pattern "package:" | ForEach-Object { $_.ToString().Replace("package:", "").Trim() }

if ($apkPath) {
    Write-Host "   APK 路径: $apkPath" -ForegroundColor Cyan
    
    $outputPath = "installed-monica.apk"
    adb pull $apkPath $outputPath 2>&1 | Out-Null
    
    if (Test-Path $outputPath) {
        Write-Host "   ✅ APK 已导出到: $outputPath" -ForegroundColor Green
        
        # 检查签名
        Write-Host ""
        Write-Host "4. 分析 APK 签名..." -ForegroundColor Yellow
        Write-Host "   --- 签名详细信息 ---" -ForegroundColor Cyan
        
        $certInfo = keytool -printcert -jarfile $outputPath 2>&1
        $certInfo | ForEach-Object {
            if ($_ -match "Owner:|Issuer:|Serial number:|Valid from:|Certificate fingerprints:") {
                Write-Host "   $_" -ForegroundColor White
            } elseif ($_ -match "SHA1:|SHA256:|MD5:") {
                Write-Host "   $_" -ForegroundColor Yellow
            }
        }
        
        # 检查是否是 debug 签名
        if ($certInfo -match "CN=Android Debug") {
            Write-Host ""
            Write-Host "   📱 这是 Android Debug 签名" -ForegroundColor Magenta
            Write-Host "   Debug keystore 位置: C:\Users\$env:USERNAME\.android\debug.keystore" -ForegroundColor Cyan
        } else {
            Write-Host ""
            Write-Host "   🔐 这是 Release 签名 (自定义)" -ForegroundColor Magenta
        }
        
    } else {
        Write-Host "   ❌ APK 导出失败" -ForegroundColor Red
    }
}

Write-Host ""

# 检查本地 debug keystore
Write-Host "5. 检查本地 debug keystore..." -ForegroundColor Yellow
$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
if (Test-Path $debugKeystore) {
    Write-Host "   ✅ Debug keystore 存在: $debugKeystore" -ForegroundColor Green
    
    # 显示 debug keystore 的签名
    Write-Host "   --- Debug Keystore 签名 ---" -ForegroundColor Cyan
    $debugCertInfo = keytool -list -v -keystore $debugKeystore -storepass android -alias androiddebugkey 2>&1
    $debugCertInfo | ForEach-Object {
        if ($_ -match "Owner:|Issuer:|Valid from:|Certificate fingerprints:") {
            Write-Host "   $_" -ForegroundColor White
        } elseif ($_ -match "SHA1:|SHA256:|MD5:") {
            Write-Host "   $_" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "   ❌ Debug keystore 不存在" -ForegroundColor Red
}

Write-Host ""

# 检查项目中的 keystore
Write-Host "6. 检查项目中的 keystore..." -ForegroundColor Yellow
$projectKeystores = Get-ChildItem -Path . -Include *.jks,*.keystore -Recurse -ErrorAction SilentlyContinue
if ($projectKeystores) {
    Write-Host "   ✅ 找到以下 keystore 文件:" -ForegroundColor Green
    foreach ($ks in $projectKeystores) {
        Write-Host "      - $($ks.FullName)" -ForegroundColor Cyan
    }
} else {
    Write-Host "   ⚠️  项目中没有 keystore 文件" -ForegroundColor Yellow
}

Write-Host ""

# 检查最新编译的 APK
Write-Host "7. 检查最新编译的 APK..." -ForegroundColor Yellow
$buildApk = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $buildApk) {
    Write-Host "   ✅ 找到构建的 APK: $buildApk" -ForegroundColor Green
    
    Write-Host "   --- 构建 APK 签名 ---" -ForegroundColor Cyan
    $buildCertInfo = keytool -printcert -jarfile $buildApk 2>&1
    $buildCertInfo | ForEach-Object {
        if ($_ -match "Owner:|Issuer:|Serial number:|Valid from:|Certificate fingerprints:") {
            Write-Host "   $_" -ForegroundColor White
        } elseif ($_ -match "SHA1:|SHA256:|MD5:") {
            Write-Host "   $_" -ForegroundColor Yellow
        }
    }
    
    # 比较签名
    if (Test-Path "installed-monica.apk") {
        Write-Host ""
        Write-Host "8. 比较签名..." -ForegroundColor Yellow
        
        $installedSHA256 = $certInfo | Select-String "SHA256:" | Select-Object -First 1
        $buildSHA256 = $buildCertInfo | Select-String "SHA256:" | Select-Object -First 1
        
        if ($installedSHA256 -eq $buildSHA256) {
            Write-Host "   ✅ 签名匹配! 可以覆盖安装,数据不会丢失" -ForegroundColor Green
        } else {
            Write-Host "   ❌ 签名不匹配! 无法覆盖安装,会清空数据!" -ForegroundColor Red
            Write-Host ""
            Write-Host "   已安装版本签名:" -ForegroundColor Yellow
            Write-Host "   $installedSHA256" -ForegroundColor White
            Write-Host ""
            Write-Host "   构建版本签名:" -ForegroundColor Yellow
            Write-Host "   $buildSHA256" -ForegroundColor White
        }
    }
} else {
    Write-Host "   ⚠️  没有找到构建的 APK,请先编译项目" -ForegroundColor Yellow
    Write-Host "   运行: .\gradlew assembleDebug" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=== 诊断完成 ===" -ForegroundColor Cyan
Write-Host ""

# 提供建议
Write-Host "💡 建议:" -ForegroundColor Green
Write-Host "1. 如果签名不匹配,在安装前请先备份数据!" -ForegroundColor Yellow
Write-Host "2. 考虑设置统一的签名配置,避免未来数据丢失" -ForegroundColor Yellow
Write-Host "3. 查看 docs\SIGNING_CONFIG_GUIDE.md 了解签名配置详情" -ForegroundColor Yellow
Write-Host ""

# 询问是否备份 debug keystore
if (Test-Path $debugKeystore) {
    Write-Host "是否要备份当前的 debug keystore 到项目目录? (y/n): " -ForegroundColor Cyan -NoNewline
    $response = Read-Host
    if ($response -eq "y" -or $response -eq "Y") {
        $backupDir = "keystore"
        if (-not (Test-Path $backupDir)) {
            New-Item -ItemType Directory -Path $backupDir | Out-Null
        }
        Copy-Item $debugKeystore "$backupDir\debug.keystore" -Force
        Write-Host "✅ Debug keystore 已备份到: $backupDir\debug.keystore" -ForegroundColor Green
        
        # 添加到 .gitignore
        $gitignorePath = ".gitignore"
        $gitignoreContent = ""
        if (Test-Path $gitignorePath) {
            $gitignoreContent = Get-Content $gitignorePath -Raw
        }
        if ($gitignoreContent -notmatch "keystore/") {
            Add-Content $gitignorePath "`n# 签名文件 - 不要提交到 Git`nkeystore/`n*.jks`n*.keystore`nkeystore.properties"
            Write-Host "✅ 已更新 .gitignore" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
