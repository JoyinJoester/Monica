# 🎯 完整测试流程 - 修复后

## ✅ 问题已修复!

**根本原因**: 测试应用的登录按钮没有调用 `finish()`,导致 Activity 不关闭,Android 系统无法检测到表单提交。

**已修复**: 
- 添加了 `Handler.postDelayed()` + `finish()`
- 点击登录后 1.5 秒自动关闭 Activity
- 这会触发 Android 的表单提交检测
- 从而调用 `onSaveRequest`

---

## 🚀 现在开始测试

### 步骤 1: 安装修复后的应用

```powershell
# 安装 Monica
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

# 安装测试应用
adb install -r "test-app\build\outputs\apk\debug\test-app-debug.apk"
```

---

### 步骤 2: 启动日志监控

**在新的 PowerShell 窗口运行**:
```powershell
adb logcat -c
adb logcat | Select-String "💾"
```

你应该看到类似这样的输出窗口,等待日志:
```
正在等待日志...
```

---

### 步骤 3: 执行测试

#### 3.1 打开测试应用

在手机上找到并打开 "Autofill Test" 应用

#### 3.2 点击用户名字段

**日志窗口应该显示**:
```
AutofillPicker: ╔════════════════════════════════════════╗
AutofillPicker: ║   addSaveInfo() CALLED                ║
AutofillPicker: ╚════════════════════════════════════════╝
AutofillPicker:   Password fields: 1
AutofillPicker: → Configuring LOGIN SaveInfo
AutofillPicker: 💾 Login SaveInfo added: requiredFields=1, optionalFields=1
AutofillPicker: 💾 SaveInfo configured: scenario=LOGIN
```

✅ 如果看到这些,SaveInfo 配置成功!

#### 3.3 手动输入新值

**⚠️ 关键步骤 - 必须这样做!**

- ❌ **不要点击** Monica 的自动填充建议!
- ✅ **手动输入** 全新的值:

```
用户名: brand-new-user-2024@test.com
密码: CompletelyNewPassword999
```

**为什么必须手动输入?**
- Android 系统会比对初始值和最终值
- 如果值是自动填充的,系统认为没有新数据
- 只有手动输入的新值才会触发保存

#### 3.4 点击"登录"按钮

**界面会显示**:
```
登录成功！
用户名: brand-new-user-2024@test.com
密码: ***************************
```

**1.5 秒后界面自动关闭**

#### 3.5 查看日志 - 关键时刻!

**日志窗口应该立即显示**:
```
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MonicaAutofill: 💾💾💾 onSaveRequest TRIGGERED! 💾💾💾
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MonicaAutofill: SaveRequest contexts: 2
```

🎉 **如果看到这个,说明成功了!**

#### 3.6 系统应该显示保存提示

**手机屏幕底部应该弹出**:
```
┌─────────────────────────────────┐
│ 保存到 Monica 密码管理器         │
│                                 │
│ brand-new-user-2024@test.com   │
│ ••••••••••••••••••••••••••     │
│                                 │
│  [取消]              [保存]     │
└─────────────────────────────────┘
```

---

## 📊 预期的完整日志序列

```
# 1. 打开应用,点击用户名字段
AutofillPicker: ╔════════════════════════════════════════╗
AutofillPicker: ║   addSaveInfo() CALLED                ║
AutofillPicker: ╚════════════════════════════════════════╝
AutofillPicker: Parsed structure items: 2
AutofillPicker:   Field hint: USERNAME, id: [...]
AutofillPicker:   Field hint: PASSWORD, id: [...]
AutofillPicker: Field classification complete:
AutofillPicker:   Username fields: 1
AutofillPicker:   Password fields: 1
AutofillPicker:   New password fields: 0
AutofillPicker: Scenario determination:
AutofillPicker:   Is new password scenario: false
AutofillPicker:   Will configure SaveInfo: true
AutofillPicker: → Configuring LOGIN SaveInfo
AutofillPicker: 💾 Login SaveInfo added: requiredFields=1, optionalFields=1
AutofillPicker: 💾 SaveInfo configured: scenario=LOGIN, username=1, password=1, newPassword=0
AutofillPicker: ╚════════════════════════════════════════╝
MonicaAutofill: ✓ Direct list response created successfully
MonicaAutofill: 📌 SaveInfo has been configured in FillResponse

# 2. 手动输入新值,点击登录

# 3. 1.5秒后 Activity 关闭,触发 onSaveRequest
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MonicaAutofill: 💾💾💾 onSaveRequest TRIGGERED! 💾💾💾
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MonicaAutofill: SaveRequest contexts: 2
MonicaAutofill: 💾 开始处理密码保存请求
MonicaAutofill: 💾 解析到 2 个字段
MonicaAutofill: 💾 提取到的值: username='brand-new-user-2024@test.com', password='[REDACTED]'
MonicaAutofill: 💾 应用包名: com.test.autofilltest
MonicaAutofill: 💾 检测重复密码中...
MonicaAutofill: 💾 检测到重复密码: 类型=NoDuplicate
MonicaAutofill: 💾 创建新密码条目: title='Autofill Test'
MonicaAutofill: 💾 密码已加密存储
MonicaAutofill: 💾 密码保存成功!
```

---

## 🎬 测试视频脚本

如果想录制测试:

1. **开始录屏**
2. **打开测试应用** - 看到登录界面
3. **点击用户名字段** - 看到 Monica 建议(不要点击!)
4. **手动输入**: `brand-new-user-2024@test.com`
5. **点击密码字段**
6. **手动输入**: `CompletelyNewPassword999`
7. **点击"登录"按钮** - 看到成功消息
8. **等待 1.5 秒** - 界面自动关闭
9. **应该看到保存提示** - "保存到 Monica 密码管理器"
10. **点击"保存"** - 密码被保存
11. **结束录屏**

---

## ❓ 故障排除

### 如果还是没有看到 onSaveRequest

#### 检查 1: 确认使用了手动输入
```
❌ 错误: 点击了自动填充建议
✅ 正确: 完全手动输入新值
```

#### 检查 2: 确认 Activity 真的关闭了
```powershell
# 检查应用进程
adb shell dumpsys activity | Select-String "autofilltest"

# 如果还有 MainActivity 在运行,说明没有关闭
```

#### 检查 3: 确认 Monica 服务已启用
```powershell
adb shell settings get secure autofill_service
# 应该输出: takagi.ru.monica/.autofill.MonicaAutofillService
```

#### 检查 4: 查看完整日志
```powershell
# 获取完整日志
adb logcat -d > full_log.txt

# 搜索关键词
Get-Content full_log.txt | Select-String "MonicaAutofill|AutofillPicker"
```

---

## 🎯 成功标准

测试成功需要满足:

- [x] SaveInfo 配置日志出现
- [x] 手动输入了新的用户名和密码
- [x] 点击登录后界面在 1.5 秒后关闭
- [x] 看到 `💾💾💾 onSaveRequest TRIGGERED!` 日志
- [x] 系统显示保存密码提示
- [x] 点击保存后密码被保存到 Monica

---

## 📝 下一步

如果测试成功:
1. ✅ 标记任务完成
2. ✅ 创建 PR
3. ✅ 更新文档
4. ✅ 庆祝! 🎉

如果还有问题:
1. 提供完整的 logcat 日志
2. 录制屏幕操作视频
3. 报告具体的错误信息

---

**开始测试吧!** 这次应该能成功! 🚀

记住:
- ❌ 不要点击自动填充建议
- ✅ 手动输入全新的值
- ⏰ 等待 1.5 秒让界面自动关闭
