@echo off
echo ========================================
echo Monica 自动填充测试应用 - 构建和安装
echo ========================================
echo.

echo [1/3] 清理旧的构建文件...
call gradlew clean

echo.
echo [2/3] 编译 APK...
call gradlew assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 编译失败！请检查错误信息。
    pause
    exit /b 1
)

echo.
echo [3/3] 安装到设备...
adb install -r build\outputs\apk\debug\test-app-debug.apk

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 安装失败！请确保：
    echo    1. 设备已连接并启用 USB 调试
    echo    2. 运行 'adb devices' 检查设备连接
    pause
    exit /b 1
)

echo.
echo ========================================
echo ✅ 安装成功！
echo ========================================
echo.
echo 下一步：
echo 1. 在 Monica 中添加测试密码（包名：com.test.autofilltest）
echo 2. 启用 Monica 自动填充服务
echo 3. 打开"自动填充测试"应用进行测试
echo.
pause
