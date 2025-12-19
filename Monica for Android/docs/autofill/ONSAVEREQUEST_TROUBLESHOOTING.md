# onSaveRequest 不触发 - 完整调试指南

## 📋 问题描述

SaveInfo 已经配置,但 `onSaveRequest()` 回调没有被 Android 系统触发。

---

## 🔧 最新修复 (2024-11-08)

### 1. 移除了 FLAG_DELAY_SAVE
- **问题**: 这个 flag 会延迟保存提示
- **修复**: 只使用 `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE`

### 2. 添加了详细的诊断日志
- `addSaveInfo()` 调用确认
- 字段分类详情
- SaveInfo 配置验证
- SaveInfoDiagnostic 工具

### 3. 添加了 SaveInfo 反射诊断工具
- 验证 SaveInfo 真的存在于 FillResponse 中
- 检查 required fields, optional fields, flags, type
- 提供详细的诊断报告

---

## 🎯 诊断流程

### 第一步: 验证 SaveInfo 是否被配置

**运行测试**:
```powershell
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
adb logcat -c
adb logcat | Select-String "addSaveInfo|SaveInfo"
```

**打开测试应用** → **点击登录字段**

**期望日志**:
```
AutofillPicker: ║   addSaveInfo() CALLED                ║
AutofillPicker:   Password fields: 1
AutofillPicker: → Configuring LOGIN SaveInfo
AutofillPicker: 💾 Login SaveInfo added
SaveInfoDiag: ✅ SaveInfo exists in FillResponse
SaveInfoDiag: 📌 Required fields: 1
SaveInfoDiag: 🚩 Flags: 1 (SAVE_ON_ALL_VIEWS_INVISIBLE)
```

✅ **如果看到这些日志** → SaveInfo 配置正确,进入第二步

❌ **如果没有看到** → SaveInfo 配置失败,检查:
- 测试应用的字段是否有 `autofillHints`
- Parser 是否正确识别了字段类型

---

### 第二步: 测试表单提交

**操作**:
1. **手动输入新值** (不要选择现有密码!):
   - 用户名: `newtest@example.com`
   - 密码: `NewPassword123`

2. **点击登录按钮** (或按返回键关闭界面)

3. **监控日志**:
```powershell
adb logcat | Select-String "💾💾💾"
```

**期望日志**:
```
MonicaAutofill: 💾💾💾 onSaveRequest TRIGGERED! 💾💾💾
```

✅ **如果看到** → 成功!保存功能正常

❌ **如果没有看到** → 问题在表单提交,进入第三步

---

### 第三步: 诊断为什么 onSaveRequest 不触发

#### 可能原因 A: 字段值没有改变 ⭐ 最常见

**问题**: Android 系统比对初始值和最终值,如果一样就不触发

**验证**: 
- 确保手动输入了**全新的**用户名和密码
- 不要点击 Monica 的自动填充建议
- 不要使用之前保存的值

**正确流程**:
```
1. 打开登录界面
2. 字段是空的
3. 手动输入新值
4. 点击登录
5. onSaveRequest 应该触发
```

---

#### 可能原因 B: 表单提交未被检测到 ⭐ 第二常见

**问题**: 测试应用的提交逻辑没有触发 Android 的保存检测

**测试应用的正确代码**:
```kotlin
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loginButton.setOnClickListener {
            // ✅ 正确: 关闭 Activity 触发保存检测
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 100)
            
            // ✅ 或者: 启动新 Activity
            // startActivity(Intent(this, HomeActivity::class.java))
            // finish()
        }
    }
}
```

**错误的代码**:
```kotlin
loginButton.setOnClickListener {
    // ❌ 错误: 只显示 Toast,不关闭界面
    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()
    
    // ❌ 错误: 只更新 UI,不触发导航
    progressBar.visibility = View.VISIBLE
}
```

---

#### 可能原因 C: 测试应用禁用了 Autofill

**检查布局文件**:
```xml
<!-- ❌ 错误 -->
<EditText
    android:importantForAutofill="no" />  <!-- 不要这样! -->

<!-- ✅ 正确 -->
<EditText
    android:autofillHints="username"
    android:inputType="textEmailAddress"
    android:importantForAutofill="yes" />  <!-- 或者不设置这个属性 -->

<EditText
    android:autofillHints="password"
    android:inputType="textPassword"
    android:importantForAutofill="yes" />
```

**检查 AndroidManifest.xml**:
```xml
<!-- ❌ 不要在 Application 或 Activity 级别禁用 -->
<!-- 不要有这行: android:importantForAutofill="no" -->

<activity android:name=".LoginActivity">
    <!-- 正常即可,不需要特殊配置 -->
</activity>
```

---

#### 可能原因 D: Android 版本太低

**检查系统版本**:
```powershell
adb shell getprop ro.build.version.sdk
```

**要求**: API 26+ (Android 8.0+)

如果 < 26, `onSaveRequest` 不会被调用。

---

#### 可能原因 E: Autofill 服务未启用

**检查服务状态**:
```powershell
adb shell settings get secure autofill_service
```

**期望输出**:
```
takagi.ru.monica/.autofill.MonicaAutofillService
```

**如果不是**, 手动设置:
```
设置 → 系统 → 语言和输入法 → 高级 → 自动填充服务 → Monica
```

---

## 🧪 最简测试案例

如果仍然无法触发,创建一个最简单的测试应用:

```kotlin
class SimpleTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = 50
        }
        
        // 用户名字段
        val username = EditText(this).apply {
            hint = "Username"
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        
        // 密码字段
        val password = EditText(this).apply {
            hint = "Password"
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // 登录按钮
        val button = Button(this).apply {
            text = "Login"
            setOnClickListener {
                // 关键: 必须关闭 Activity
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 200)
            }
        }
        
        layout.addView(username)
        layout.addView(password)
        layout.addView(button)
        setContentView(layout)
    }
}
```

**测试步骤**:
1. 打开界面
2. 输入用户名: `test@example.com`
3. 输入密码: `password123`
4. 点击 Login
5. 检查 logcat 是否有 `💾💾💾 onSaveRequest TRIGGERED!`

---

## 📊 诊断清单

在报告问题之前,确保完成所有检查:

### SaveInfo 配置检查
- [ ] 看到 "addSaveInfo() CALLED" 日志
- [ ] Password fields > 0
- [ ] SaveInfo exists in FillResponse
- [ ] Required fields > 0
- [ ] Flags 包含 SAVE_ON_ALL_VIEWS_INVISIBLE

### 测试应用检查
- [ ] EditText 有 `android:autofillHints`
- [ ] 没有 `android:importantForAutofill="no"`
- [ ] 登录按钮点击后调用 `finish()` 或启动新 Activity

### 测试操作检查
- [ ] **手动输入**了新的用户名和密码
- [ ] **没有**点击自动填充建议
- [ ] 点击了登录按钮或关闭了 Activity

### 系统环境检查
- [ ] Android 版本 >= 8.0 (API 26+)
- [ ] Monica 已设置为自动填充服务
- [ ] 重新安装了最新版本的 APK

---

## 🎯 预期行为

**正确的完整流程**:

```
1. [用户] 打开测试应用登录界面
   ↓
2. [系统] 调用 onFillRequest
   ↓
3. [Monica] 解析字段,创建 FillResponse + SaveInfo
   ↓
4. [系统] 显示自动填充建议
   ↓
5. [用户] 手动输入新的用户名和密码 (不点击建议)
   ↓
6. [用户] 点击登录按钮
   ↓
7. [测试应用] 调用 finish() 关闭 Activity
   ↓
8. [系统] 检测到字段值改变 + 界面关闭
   ↓
9. [系统] 调用 onSaveRequest ← 这是关键!
   ↓
10. [Monica] 处理保存请求
    ↓
11. [系统] 显示 "保存到 Monica" 的提示
```

**如果第 9 步没有发生**, 问题在于:
- 第 5 步: 值没有真正改变
- 第 7 步: Activity 没有正确关闭
- 第 3 步: SaveInfo 配置错误

---

## 📝 报告问题模板

如果仍然无法解决,请提供:

```markdown
### 环境信息
- Android 版本: [从 adb shell getprop ro.build.version.sdk]
- 设备型号: [从 adb shell getprop ro.product.model]
- Monica 版本: [从 APK 文件名]

### 诊断结果
#### SaveInfo 配置
- addSaveInfo() 被调用: [ ] 是 [ ] 否
- Password fields: [数量]
- SaveInfo exists: [ ] 是 [ ] 否
- Required fields: [数量]
- Flags: [值]

#### 测试操作
- 手动输入新值: [ ] 是 [ ] 否
- 点击登录后关闭 Activity: [ ] 是 [ ] 否
- onSaveRequest 触发: [ ] 是 [ ] 否

### 完整日志
```
[粘贴 adb logcat 输出]
```

### 测试应用代码
```kotlin
// LoginActivity.kt
[粘贴代码]
```

```xml
<!-- activity_login.xml -->
[粘贴布局]
```
```

---

## 🔗 相关文档

- [DIAGNOSTIC_TEST_SCRIPT.md](./DIAGNOSTIC_TEST_SCRIPT.md) - 详细测试脚本
- [QUICK_TEST_CHECKLIST.md](./QUICK_TEST_CHECKLIST.md) - 快速测试清单
- [SAVE_REQUEST_DEBUG_GUIDE.md](./SAVE_REQUEST_DEBUG_GUIDE.md) - 调试指南

---

**最后更新**: 2024-11-08
**状态**: 已添加完整的诊断工具和日志
