# 密码保存功能 - 快速参考指南

## 🚀 快速开始

### 用户视角
1. 在任何应用的登录界面输入用户名和密码
2. 点击登录按钮提交表单
3. Monica 会弹出"保存到 Monica 密码管理器"的提示
4. 确认或编辑信息后点击"保存"

### 开发者视角
密码保存功能由以下组件协同工作:

```
MonicaAutofillService.onSaveRequest()
    ↓
PasswordSaveHelper (工具类)
    ↓
AutofillSaveBottomSheet (UI)
    ↓
PasswordRepository (数据层)
```

## 📋 核心 API

### 1. 保存请求处理

```kotlin
// MonicaAutofillService.kt
override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
    serviceScope.launch {
        val result = processSaveRequest(request)
        if (result) {
            callback.onSuccess()
        } else {
            callback.onFailure("保存失败")
        }
    }
}
```

### 2. 配置 SaveInfo

```kotlin
// AutofillPickerLauncher.kt
private fun addSaveInfo(
    responseBuilder: FillResponse.Builder,
    parsedStructure: ParsedStructure,
    context: Context
) {
    // 智能配置 SaveInfo
    // 根据字段类型选择:
    // - configureSaveInfoForLogin() - 登录场景
    // - configureSaveInfoForNewPassword() - 注册/修改密码场景
}
```

### 3. 重复检测

```kotlin
// PasswordSaveHelper.kt
val duplicateCheck = PasswordSaveHelper.checkDuplicate(saveData, existingPasswords)

when (duplicateCheck) {
    is DuplicateCheckResult.ExactDuplicate -> {
        // 完全相同,跳过保存
    }
    is DuplicateCheckResult.SameUsernameDifferentPassword -> {
        // 更新现有密码
    }
    is DuplicateCheckResult.NoDuplicate -> {
        // 创建新密码
    }
}
```

### 4. 加密存储

```kotlin
// AutofillSaveBottomSheet.kt
val securityManager = SecurityManager(context)
val encryptedPassword = securityManager.encryptData(password)

val newEntry = PasswordSaveHelper.createNewPasswordEntry(
    context,
    saveData,
    encryptedPassword
)

passwordRepository.insertPasswordEntry(newEntry)
```

## 🎯 关键类和方法

### PasswordSaveHelper

**静态工具类**,提供密码保存相关的工具方法

| 方法 | 功能 | 返回值 |
|------|------|--------|
| `generateTitle()` | 智能生成密码标题 | String |
| `getAppName()` | 获取应用显示名称 | String? |
| `cleanDomain()` | 清理网站域名 | String |
| `extractWebDomain()` | 从 AssistStructure 提取域名 | String? |
| `checkDuplicate()` | 检测重复密码 | DuplicateCheckResult |
| `createNewPasswordEntry()` | 创建新密码条目 | PasswordEntry |
| `updatePasswordEntry()` | 更新现有密码 | PasswordEntry |

### SaveData 数据类

```kotlin
data class SaveData(
    val username: String,
    val password: String,
    val newPassword: String? = null,
    val confirmPassword: String? = null,
    val packageName: String,
    val webDomain: String?,
    val isNewPasswordScenario: Boolean = false
) {
    fun validate(): ValidationResult
    fun getFinalPassword(): String
}
```

### DuplicateCheckResult 密封类

```kotlin
sealed class DuplicateCheckResult {
    object NoDuplicate
    data class ExactDuplicate(val existingEntry: PasswordEntry)
    data class SameUsernameDifferentPassword(val existingEntry: PasswordEntry)
    data class DifferentAccount(val existingEntries: List<PasswordEntry>)
}
```

## 🔧 配置选项

### AutofillPreferences

```kotlin
// 启用/禁用密码保存
autofillPreferences.setRequestSaveDataEnabled(true)

// 自动更新重复密码
autofillPreferences.setAutoUpdateDuplicatePasswordsEnabled(true)

// 保存时显示通知
autofillPreferences.setShowSaveNotificationEnabled(true)

// 智能标题生成
autofillPreferences.setSmartTitleGenerationEnabled(true)
```

## 📝 SaveInfo 配置模式

### 登录场景
```kotlin
SaveInfo.Builder(
    SAVE_DATA_TYPE_USERNAME or SAVE_DATA_TYPE_PASSWORD,
    passwordFields.toTypedArray() // 必需
)
.setOptionalIds(usernameFields.toTypedArray()) // 可选
.setFlags(
    FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE or FLAG_DELAY_SAVE
)
```

### 注册/修改密码场景
```kotlin
SaveInfo.Builder(
    SAVE_DATA_TYPE_PASSWORD,
    newPasswordFields.take(1).toTypedArray() // 第一个新密码必需
)
.setOptionalIds(
    (newPasswordFields.drop(1) + usernameFields).toTypedArray() // 其他可选
)
.setFlags(
    FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE or FLAG_DELAY_SAVE
)
```

## 🐛 调试技巧

### 启用详细日志

```kotlin
// 查看保存流程日志
adb logcat -s MonicaAutofill AutofillPicker AutofillSave

// 过滤 SAVE 分类日志
adb logcat | grep "\[SAVE\]"
```

### 常见日志

```
✅ 成功流程:
[SAVE] 开始处理密码保存请求
[SAVE] 解析到 3 个字段
[SAVE] 提取用户名字段: use***
[SAVE] 提取密码字段: 12个字符
[SAVE] 数据验证通过
[SAVE] 重复检查结果: NoDuplicate
[SAVE] 密码保存请求处理完成,耗时: 245ms

❌ 失败流程:
[SAVE] 数据验证失败: 密码不能为空
[SAVE] 密码保存功能已禁用
[SAVE] 处理保存请求失败,耗时: 120ms
```

## 🔍 故障排除

### 问题: SaveInfo 不显示

**可能原因**:
1. SaveInfo 未正确配置
2. 字段未被识别
3. 系统设置问题

**解决方案**:
```kotlin
// 1. 检查字段是否被解析
AutofillLogger.d("SAVE", "解析到 ${parsedStructure.items.size} 个字段")

// 2. 确保添加了 SaveInfo
responseBuilder.setSaveInfo(saveInfoBuilder.build())

// 3. 检查 flags
saveInfoBuilder.setFlags(
    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE or
    SaveInfo.FLAG_DELAY_SAVE
)
```

### 问题: 密码未加密

**检查点**:
```kotlin
// 保存前必须加密
val securityManager = SecurityManager(context)
val encryptedPassword = securityManager.encryptData(password)

// 验证加密
Log.d("Security", "Original: ${password.length}, Encrypted: ${encryptedPassword.length}")
```

### 问题: 重复保存

**原因**: 重复检测未生效

**解决**:
```kotlin
// 确保使用 PasswordSaveHelper 检测
val duplicateCheck = PasswordSaveHelper.checkDuplicate(saveData, existingPasswords)

if (duplicateCheck is DuplicateCheckResult.ExactDuplicate) {
    // 跳过保存
    return true
}
```

## 📊 性能指标

正常情况下的性能基准:

| 操作 | 目标时间 | 备注 |
|------|---------|------|
| onSaveRequest 处理 | < 300ms | 包含数据库查询 |
| 字段解析 | < 50ms | AssistStructure 遍历 |
| 重复检测 | < 100ms | 数据库查询 |
| 密码加密 | < 20ms | AES-256-GCM |
| UI 显示 | < 200ms | BottomSheet 启动 |

## 🧪 测试检查清单

### 基本功能
- [ ] 登录场景保存密码
- [ ] 注册场景保存密码
- [ ] 修改密码场景更新密码
- [ ] 重复密码提示更新

### 应用兼容性
- [ ] 原生 Android 应用
- [ ] Chrome 浏览器
- [ ] Firefox 浏览器  
- [ ] WebView 应用

### 边界情况
- [ ] 只有密码无用户名
- [ ] 只有用户名无密码
- [ ] 空表单提交
- [ ] 超长密码 (>100字符)
- [ ] 特殊字符密码

### 安全性
- [ ] 密码正确加密
- [ ] 日志不泄露明文
- [ ] 权限正确检查

## 🌐 多语言支持

需要的字符串资源:

```xml
<string name="autofill_save_password">保存密码</string>
<string name="autofill_save_new_password">保存新密码</string>
<string name="autofill_update_password">更新密码</string>
<string name="autofill_username">用户名</string>
<string name="autofill_password">密码</string>
<string name="autofill_title">标题</string>
<string name="autofill_never_for_site">永不保存此网站</string>
<string name="autofill_show_advanced">显示高级选项</string>
<string name="autofill_hide_advanced">隐藏高级选项</string>
<string name="save">保存</string>
<string name="cancel">取消</string>
<string name="close">关闭</string>
<string name="generate_password">生成密码</string>
```

## 📚 相关文档

- [完整实现文档](PASSWORD_SAVE_IMPLEMENTATION.md)
- [需求文档](../../需求文档.md)
- [自动填充架构](README.md)
- [故障排除指南](TROUBLESHOOTING.md)

## 🎓 最佳实践

### 1. 总是加密密码
```kotlin
✅ 正确:
val encrypted = securityManager.encryptData(password)
passwordEntry.copy(password = encrypted)

❌ 错误:
passwordEntry.copy(password = password) // 明文存储!
```

### 2. 使用 PasswordSaveHelper
```kotlin
✅ 正确:
val title = PasswordSaveHelper.generateTitle(context, packageName, domain, username)
val duplicateCheck = PasswordSaveHelper.checkDuplicate(saveData, existingPasswords)

❌ 错误:
val title = packageName // 不友好的标题
// 没有重复检测
```

### 3. 记录详细日志
```kotlin
✅ 正确:
AutofillLogger.i("SAVE", "保存密码成功: id=$id, title=$title")

❌ 错误:
Log.d("TAG", "保存成功") // 不够详细
```

### 4. 验证数据
```kotlin
✅ 正确:
val validation = saveData.validate()
when (validation) {
    is ValidationResult.Error -> return false
    else -> proceed()
}

❌ 错误:
// 直接保存,没有验证
```

## 💡 常见模式

### 模式 1: 检查重复并保存

```kotlin
val duplicateCheck = PasswordSaveHelper.checkDuplicate(saveData, existingPasswords)

when (duplicateCheck) {
    is DuplicateCheckResult.ExactDuplicate -> {
        // 跳过
    }
    is DuplicateCheckResult.SameUsernameDifferentPassword -> {
        // 更新
        val updated = PasswordSaveHelper.updatePasswordEntry(
            duplicateCheck.existingEntry,
            saveData,
            encryptedPassword
        )
        repository.updatePasswordEntry(updated)
    }
    else -> {
        // 创建新条目
        val newEntry = PasswordSaveHelper.createNewPasswordEntry(
            context,
            saveData,
            encryptedPassword
        )
        repository.insertPasswordEntry(newEntry)
    }
}
```

### 模式 2: 智能标题生成

```kotlin
val title = if (preferences.isSmartTitleGenerationEnabled()) {
    PasswordSaveHelper.generateTitle(
        context,
        saveData.packageName,
        saveData.webDomain,
        saveData.username
    )
} else {
    // 使用默认标题
    saveData.packageName
}
```

## 🔗 相关链接

- [Android Autofill Framework](https://developer.android.com/guide/topics/text/autofill)
- [SaveInfo API](https://developer.android.com/reference/android/service/autofill/SaveInfo)
- [Room 数据库](https://developer.android.com/training/data-storage/room)
- [加密最佳实践](https://developer.android.com/topic/security/data)

---

**最后更新**: 2025年11月8日  
**版本**: 1.0  
**维护者**: Monica Team
