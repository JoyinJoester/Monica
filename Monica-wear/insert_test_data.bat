@echo off
REM Monica-wear 测试数据插入脚本 (Windows)
REM 使用方法: insert_test_data.bat

set PACKAGE=takagi.ru.monica.wear
set DB_PATH=/data/data/%PACKAGE%/databases/monica_wear_database

echo 正在插入测试数据到 Monica-wear...
echo.

REM 插入 Google 验证器
echo 插入 Google 验证器...
adb shell "run-as %PACKAGE% sqlite3 %DB_PATH% \"INSERT INTO secure_items (itemType, title, notes, isFavorite, sortOrder, createdAt, updatedAt, itemData, imagePaths) VALUES ('TOTP', 'Google', '测试账号', 0, 0, 1700000000000, 1700000000000, '{\"secret\":\"JBSWY3DPEHPK3PXP\",\"issuer\":\"Google\",\"accountName\":\"user@gmail.com\",\"period\":30,\"digits\":6,\"algorithm\":\"SHA1\",\"otpType\":\"TOTP\",\"counter\":0,\"pin\":\"\"}', '');\""

REM 插入 GitHub 验证器
echo 插入 GitHub 验证器...
adb shell "run-as %PACKAGE% sqlite3 %DB_PATH% \"INSERT INTO secure_items (itemType, title, notes, isFavorite, sortOrder, createdAt, updatedAt, itemData, imagePaths) VALUES ('TOTP', 'GitHub', '开发账号', 0, 1, 1700000000000, 1700000000000, '{\"secret\":\"HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ\",\"issuer\":\"GitHub\",\"accountName\":\"developer\",\"period\":30,\"digits\":6,\"algorithm\":\"SHA1\",\"otpType\":\"TOTP\",\"counter\":0,\"pin\":\"\"}', '');\""

REM 插入 Microsoft 验证器（收藏）
echo 插入 Microsoft 验证器...
adb shell "run-as %PACKAGE% sqlite3 %DB_PATH% \"INSERT INTO secure_items (itemType, title, notes, isFavorite, sortOrder, createdAt, updatedAt, itemData, imagePaths) VALUES ('TOTP', 'Microsoft', '工作账号', 1, 2, 1700000000000, 1700000000000, '{\"secret\":\"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\",\"issuer\":\"Microsoft\",\"accountName\":\"work@outlook.com\",\"period\":30,\"digits\":6,\"algorithm\":\"SHA1\",\"otpType\":\"TOTP\",\"counter\":0,\"pin\":\"\"}', '');\""

REM 插入 Steam 验证器
echo 插入 Steam 验证器...
adb shell "run-as %PACKAGE% sqlite3 %DB_PATH% \"INSERT INTO secure_items (itemType, title, notes, isFavorite, sortOrder, createdAt, updatedAt, itemData, imagePaths) VALUES ('TOTP', 'Steam', '游戏账号', 0, 3, 1700000000000, 1700000000000, '{\"secret\":\"JBSWY3DPEHPK3PXP\",\"issuer\":\"Steam\",\"accountName\":\"gamer123\",\"period\":30,\"digits\":5,\"algorithm\":\"SHA1\",\"otpType\":\"STEAM\",\"counter\":0,\"pin\":\"\"}', '');\""

echo.
echo 验证插入的数据:
adb shell "run-as %PACKAGE% sqlite3 %DB_PATH% 'SELECT id, title, itemType FROM secure_items;'"

echo.
echo 测试数据插入完成！
echo 请重启应用查看效果。
pause
