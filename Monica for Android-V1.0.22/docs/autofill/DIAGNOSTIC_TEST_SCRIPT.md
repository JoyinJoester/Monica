# 详细诊断测试脚本

## 🚀 步骤 1: 重新安装应用

```powershell
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## 🔍 步骤 2: 启动全面的日志监控

在**新的 PowerShell 窗口**中运行:

```powershell
# 清空日志
adb logcat -c

# 监控所有关键日志
adb logcat | Select-String "AutofillPicker|MonicaAutofill|SaveInfo"
```

---

## 📊 步骤 3: 测试并观察日志输出

### 测试操作:
1. 打开测试应用的登录界面
2. 点击用户名字段 ← Monica 会被触发
3. **观察日志输出**

### 期望的完整日志序列:

```
# ═══════════════════════════════════════════
# 1. addSaveInfo() 被调用
# ═══════════════════════════════════════════

AutofillPicker: ╔════════════════════════════════════════╗
AutofillPicker: ║   addSaveInfo() CALLED                ║
AutofillPicker: ╚════════════════════════════════════════╝
AutofillPicker: Parsed structure items: 2

# ═══════════════════════════════════════════
# 2. 字段分类
# ═══════════════════════════════════════════

AutofillPicker:   Field hint: USERNAME, id: [AutofillId]
AutofillPicker:   Field hint: PASSWORD, id: [AutofillId]
AutofillPicker: Field classification complete:
AutofillPicker:   Username fields: 1
AutofillPicker:   Password fields: 1
AutofillPicker:   New password fields: 0

# ═══════════════════════════════════════════
# 3. 场景判断
# ═══════════════════════════════════════════

AutofillPicker: Scenario determination:
AutofillPicker:   Is new password scenario: false
AutofillPicker:   Will configure SaveInfo: true
AutofillPicker: → Configuring LOGIN SaveInfo

# ═══════════════════════════════════════════
# 4. SaveInfo 配置完成
# ═══════════════════════════════════════════

AutofillPicker: 💾 Login SaveInfo added: requiredFields=1, optionalFields=1
AutofillPicker: 💾 SaveInfo configured: scenario=LOGIN, username=1, password=1, newPassword=0
AutofillPicker: ╚════════════════════════════════════════╝

# ═══════════════════════════════════════════
# 5. SaveInfo 诊断报告
# ═══════════════════════════════════════════

SaveInfoDiag: ╔════════════════════════════════════════╗
SaveInfoDiag: ║   SaveInfo Diagnostic Report          ║
SaveInfoDiag: ╚════════════════════════════════════════╝
SaveInfoDiag: ✅ SaveInfo exists in FillResponse
SaveInfoDiag: 📋 SaveInfo Type: 3
SaveInfoDiag:    USERNAME | PASSWORD
SaveInfoDiag: 📌 Required fields: 1
SaveInfoDiag:    [0] [AutofillId]
SaveInfoDiag: 📎 Optional fields: 1
SaveInfoDiag:    [0] [AutofillId]
SaveInfoDiag: 🚩 Flags: 1
SaveInfoDiag:    SAVE_ON_ALL_VIEWS_INVISIBLE
SaveInfoDiag: 📝 Description: 保存到 Monica 密码管理器
SaveInfoDiag: ╚════════════════════════════════════════╝
```

---

## ⚠️ 关键诊断点

### 诊断点 A: addSaveInfo() 是否被调用?

**查找日志**:
```
AutofillPicker: ║   addSaveInfo() CALLED                ║
```

- ✅ **如果看到**: SaveInfo 配置逻辑正常执行
- ❌ **如果没有**: 问题在更早的阶段,检查 `createDirectListResponse` 是否被调用

---

### 诊断点 B: 字段是否被正确识别?

**查找日志**:
```
AutofillPicker:   Password fields: 1
```

- ✅ **如果 > 0**: 字段识别正常
- ❌ **如果 = 0**: 问题是字段解析失败,SaveInfo 不会被配置

**如果 Password fields = 0**:
- 检查测试应用的 EditText 是否有 `android:autofillHints="password"`
- 检查日志中的 "Field hint" 输出,看看字段被识别成了什么

---

### 诊断点 C: SaveInfo 是否真的存在于 FillResponse?

**查找日志**:
```
SaveInfoDiag: ✅ SaveInfo exists in FillResponse
```

- ✅ **如果看到**: SaveInfo 已成功添加到 FillResponse
- ❌ **如果看到 "SaveInfo is NULL"**: 这是根本原因 - SaveInfo 配置失败

---

### 诊断点 D: SaveInfo 配置是否正确?

**检查以下字段**:

1. **Required fields** 必须 > 0
   ```
   SaveInfoDiag: 📌 Required fields: 1
   ```

2. **Flags** 必须包含正确的标志
   ```
   SaveInfoDiag: 🚩 Flags: 1
   SaveInfoDiag:    SAVE_ON_ALL_VIEWS_INVISIBLE
   ```

3. **Save Type** 必须包含 PASSWORD
   ```
   SaveInfoDiag: 📋 SaveInfo Type: 3
   SaveInfoDiag:    USERNAME | PASSWORD
   ```

---

## 🧪 步骤 4: 测试表单提交

### 操作:
1. 在用户名字段输入: `testuser@example.com`
2. 在密码字段输入: `TestPassword123`
3. 点击登录按钮(或按返回键关闭 Activity)

### 监控新的 PowerShell 窗口:

```powershell
adb logcat | Select-String "💾💾💾"
```

### 期望日志:

```
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MonicaAutofill: 💾💾💾 onSaveRequest TRIGGERED! 💾💾💾
MonicaAutofill: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🔧 故障排除

### 情况 1: addSaveInfo() 没有被调用

**原因**: `createDirectListResponse` 可能没有被使用

**解决方案**:
```powershell
# 检查哪个响应方法被使用
adb logcat | Select-String "Creating direct list|Using new direct list|Direct list response created"
```

如果没有看到这些日志,说明代码路径不对。

---

### 情况 2: Password fields = 0

**原因**: 测试应用的字段没有正确的 autofillHints

**解决方案**:
检查测试应用的布局文件,确保:
```xml
<EditText
    android:id="@+id/password"
    android:autofillHints="password"
    android:inputType="textPassword"
    android:importantForAutofill="yes" />
```

---

### 情况 3: SaveInfo exists 但 onSaveRequest 不触发

这是**最常见的问题**,有以下几个可能原因:

#### 原因 A: 字段值没有改变
- Android 系统会比对初始值和最终值
- **解决方案**: 确保手动输入新值,不要选择现有密码

#### 原因 B: 表单提交未被检测到
- **解决方案**: 确保点击登录按钮后:
  - 关闭了当前 Activity (`finish()`)
  - 或者启动了新的 Activity

测试应用的正确代码:
```kotlin
loginButton.setOnClickListener {
    // 模拟登录
    Handler(Looper.getMainLooper()).postDelayed({
        finish()  // 必须关闭 Activity
    }, 100)
}
```

#### 原因 C: Android 版本问题
```powershell
# 检查 Android 版本
adb shell getprop ro.build.version.sdk
```
- onSaveRequest 需要 API 26+ (Android 8.0+)

#### 原因 D: 服务未正确设置
```powershell
# 检查自动填充服务
adb shell settings get secure autofill_service
```
应该输出: `takagi.ru.monica/.autofill.MonicaAutofillService`

---

## 📝 诊断报告模板

请将以下信息复制并填写:

```
### 诊断结果

#### 步骤 1: addSaveInfo() 调用
- [ ] ✅ 看到 "addSaveInfo() CALLED"
- [ ] ❌ 没有看到

#### 步骤 2: 字段识别
- Password fields: ____
- Username fields: ____
- New password fields: ____

#### 步骤 3: SaveInfo 存在性
- [ ] ✅ 看到 "SaveInfo exists in FillResponse"
- [ ] ❌ 看到 "SaveInfo is NULL"

#### 步骤 4: SaveInfo 配置
- Required fields: ____
- Optional fields: ____
- Flags: ____
- Type: ____

#### 步骤 5: onSaveRequest 触发
- [ ] ✅ 看到 "💾💾💾 onSaveRequest TRIGGERED!"
- [ ] ❌ 没有看到

#### 测试环境
- Android 版本: ____
- 设备型号: ____
- 测试应用: ____
- 操作步骤: ____

#### 完整日志
[粘贴 logcat 输出]
```

---

## 🎯 成功标准

所有以下条件都必须满足:

1. ✅ addSaveInfo() 被调用
2. ✅ Password fields > 0
3. ✅ SaveInfo exists in FillResponse
4. ✅ Required fields > 0
5. ✅ Flags = SAVE_ON_ALL_VIEWS_INVISIBLE
6. ✅ 手动输入新的用户名和密码
7. ✅ 点击提交或关闭 Activity
8. ✅ onSaveRequest 被触发

如果前5个条件都满足,但第8个不满足,问题在于**测试应用的表单提交逻辑**,不是 Monica 的问题。

---

**开始测试吧!** 🚀
