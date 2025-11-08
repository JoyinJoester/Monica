# 搜索旧的 Keystore 文件
Write-Host "=== 搜索旧的 Monica Keystore 文件 ===" -ForegroundColor Cyan
Write-Host ""

$searchPaths = @(
    "$env:USERPROFILE\Desktop",
    "$env:USERPROFILE\Documents",
    "$env:USERPROFILE\Downloads",
    "C:\Users\joyins\Desktop\Monica-main",
    "$env:USERPROFILE\.android"
)

$keystoreFiles = @()

Write-Host "正在搜索以下位置:" -ForegroundColor Yellow
foreach ($path in $searchPaths) {
    if (Test-Path $path) {
        Write-Host "  - $path" -ForegroundColor Gray
        $files = Get-ChildItem -Path $path -Include *.jks,*.keystore -Recurse -ErrorAction SilentlyContinue
        $keystoreFiles += $files
    }
}

Write-Host ""

if ($keystoreFiles.Count -gt 0) {
    Write-Host "✅ 找到 $($keystoreFiles.Count) 个 keystore 文件:" -ForegroundColor Green
    Write-Host ""
    
    foreach ($file in $keystoreFiles) {
        Write-Host "📁 文件: $($file.FullName)" -ForegroundColor Cyan
        Write-Host "   大小: $($file.Length) 字节" -ForegroundColor Gray
        Write-Host "   修改时间: $($file.LastWriteTime)" -ForegroundColor Gray
        
        # 尝试读取 keystore 信息（可能需要密码）
        Write-Host "   尝试读取签名信息..." -ForegroundColor Yellow
        
        # 常见的密码尝试
        $commonPasswords = @("android", "123456", "password", "monica", "joyin")
        $found = $false
        
        foreach ($pass in $commonPasswords) {
            try {
                $output = keytool -list -v -keystore $file.FullName -storepass $pass 2>&1
                if ($output -notmatch "password was incorrect|Keystore was tampered") {
                    Write-Host "   ✅ 密码可能是: $pass" -ForegroundColor Green
                    
                    # 显示 CN 信息
                    $cnMatch = $output | Select-String "Owner:.*CN=([^,]+)"
                    if ($cnMatch) {
                        $cn = $cnMatch.Matches[0].Groups[1].Value
                        Write-Host "   CN: $cn" -ForegroundColor White
                        
                        if ($cn -eq "joyin") {
                            Write-Host "   🎯 这可能就是您要找的 keystore!" -ForegroundColor Magenta
                        }
                    }
                    
                    # 显示 SHA256
                    $sha256Match = $output | Select-String "SHA256:.*"
                    if ($sha256Match) {
                        Write-Host "   $($sha256Match.Line.Trim())" -ForegroundColor Yellow
                    }
                    
                    $found = $true
                    break
                }
            } catch {
                # 密码不对，继续尝试
            }
        }
        
        if (-not $found) {
            Write-Host "   ⚠️  需要密码才能读取（常见密码都不对）" -ForegroundColor Yellow
        }
        
        Write-Host ""
    }
} else {
    Write-Host "❌ 没有找到 keystore 文件" -ForegroundColor Red
    Write-Host ""
    Write-Host "建议:" -ForegroundColor Yellow
    Write-Host "1. 检查其他电脑（如果在多台电脑上开发过）" -ForegroundColor Gray
    Write-Host "2. 检查云存储（OneDrive, Google Drive 等）" -ForegroundColor Gray
    Write-Host "3. 检查备份硬盘或 U 盘" -ForegroundColor Gray
    Write-Host "4. 如果实在找不到，需要创建新的签名" -ForegroundColor Gray
}

Write-Host ""
Write-Host "目标签名信息 (需要匹配):" -ForegroundColor Cyan
Write-Host "CN: joyin" -ForegroundColor White
Write-Host "SHA256: 19:DB:C4:A4:83:17:93:FC:A4:F1:F0:7F:75:05:2A:1D:EB:FC:26:29:A2:83:73:B5:01:1E:71:03:91:CD:FA:98" -ForegroundColor Yellow
Write-Host ""

Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
