# 快速测试指南

## ✅ 应用已安装成功！

测试应用 "自动填充测试" 已经安装到你的模拟器上。

## 下一步操作

### 1. 在 Monica 中添加测试密码

打开 Monica 应用，添加一个新密码：

```
标题: 测试账号
用户名: testuser
密码: Test123456
应用包名: com.test.autofilltest
```

**重要**: 确保填写了"应用包名"字段！

### 2. 启用 Monica 自动填充服务

1. 打开模拟器的设置
2. 搜索"自动填充"或进入：
   - 设置 → 系统 → 语言和输入法 → 自动填充服务
3. 选择 "Monica"

### 3. 测试自动填充

1. 测试应用应该已经打开（如果没有，在应用列表中找到"自动填充测试"）
2. 点击"用户名"输入框
3. 观察是否出现 Monica 的密码建议：
   - Android 11+: 键盘上方会显示内联建议
   - Android 8-10: 输入框下方会显示下拉菜单
4. 点击密码建议
5. 用户名和密码应该自动填充
6. 点击"登录"按钮验证

## 预期结果

✅ 点击输入框后显示 Monica 的密码建议
✅ 选择密码后自动填充用户名和密码
✅ 点击登录显示"登录成功"

## 如果没有显示自动填充建议

1. **检查 Monica 服务状态**
   - 打开 Monica → 设置 → 自动填充
   - 查看状态是否为"已启用"
   - 点击"故障排查"查看详细信息

2. **检查密码条目**
   - 确认包名是 `com.test.autofilltest`
   - 确认用户名和密码都已填写

3. **重启应用**
   - 完全关闭 Monica 和测试应用
   - 重新打开测试

4. **查看日志**
   ```bash
   adb logcat | findstr "Autofill"
   ```

## 重新安装应用

如果需要重新安装：

```bash
./gradlew.bat :test-app:assembleDebug
adb install -r test-app\build\outputs\apk\debug\test-app-debug.apk
adb shell am start -n com.test.autofilltest/.MainActivity
```

## 卸载应用

```bash
adb uninstall com.test.autofilltest
```

## 应用信息

- **包名**: com.test.autofilltest
- **Activity**: com.test.autofilltest.MainActivity
- **APK 位置**: test-app\build\outputs\apk\debug\test-app-debug.apk

祝测试顺利！🎉
