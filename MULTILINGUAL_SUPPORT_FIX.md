# 多语言支持修复 - 自动填充和清空数据功能

## 修复日期
2025年10月6日

## 问题描述
SettingsScreen.kt 中的"自动填充"和"清空所有数据"功能使用了硬编码的中文文本，没有支持多语言。

## 修复内容

### 1. 新增字符串资源

#### English (`values/strings.xml`)
```xml
<!-- Autofill -->
<string name="autofill">Autofill</string>
<string name="autofill_subtitle">Auto-fill passwords for apps and websites</string>

<!-- Clear All Data -->
<string name="clear_all_data">Clear All Data</string>
<string name="clear_all_data_subtitle">Delete all passwords, authenticators, bank cards and documents</string>
<string name="clear_all_data_confirm">Confirm Clear All Data?</string>
<string name="clear_all_data_warning">This will permanently delete all passwords, authenticators, bank cards and documents, and cannot be recovered.\n\nIt is recommended to export a backup before clearing!</string>
<string name="enter_master_password_to_confirm">Please enter your master password to confirm</string>
<string name="clearing_data">Clearing data…</string>
<string name="password_incorrect">Password incorrect</string>

<!-- Biometric -->
<string name="biometric_cannot_enable">Cannot enable fingerprint unlock</string>
```

#### 中文 (`values-zh/strings.xml`)
```xml
<!-- Autofill -->
<string name="autofill">自动填充</string>
<string name="autofill_subtitle">为应用和网站自动填充密码</string>

<!-- Clear All Data -->
<string name="clear_all_data">清空所有数据</string>
<string name="clear_all_data_subtitle">删除所有密码、验证器、银行卡和证件数据</string>
<string name="clear_all_data_confirm">确认清空所有数据?</string>
<string name="clear_all_data_warning">此操作将永久删除所有密码、验证器、银行卡和证件数据，且无法恢复。\n\n建议在清空前先导出备份!</string>
<string name="enter_master_password_to_confirm">请输入主密码确认</string>
<string name="clearing_data">正在清空数据…</string>
<string name="password_incorrect">密码错误</string>

<!-- Biometric -->
<string name="biometric_cannot_enable">无法启用指纹解锁</string>
```

### 2. 更新代码

#### SettingsScreen.kt 修改点

##### 自动填充设置项
**修改前:**
```kotlin
SettingsItem(
    icon = Icons.Default.VpnKey,
    title = "自动填充",
    subtitle = "为应用和网站自动填充密码",
    onClick = onNavigateToAutofill
)
```

**修改后:**
```kotlin
SettingsItem(
    icon = Icons.Default.VpnKey,
    title = context.getString(R.string.autofill),
    subtitle = context.getString(R.string.autofill_subtitle),
    onClick = onNavigateToAutofill
)
```

##### 清空所有数据设置项
**修改前:**
```kotlin
SettingsItem(
    icon = Icons.Default.DeleteForever,
    title = "清空所有数据",
    subtitle = "删除所有密码、验证器、银行卡和证件数据",
    onClick = { showClearDataDialog = true },
    iconTint = MaterialTheme.colorScheme.error
)
```

**修改后:**
```kotlin
SettingsItem(
    icon = Icons.Default.DeleteForever,
    title = context.getString(R.string.clear_all_data),
    subtitle = context.getString(R.string.clear_all_data_subtitle),
    onClick = { showClearDataDialog = true },
    iconTint = MaterialTheme.colorScheme.error
)
```

##### 清空数据确认对话框
**修改前:**
```kotlin
title = {
    Text("确认清空所有数据?")
},
text = {
    Column {
        Text(
            "此操作将永久删除所有密码、验证器、银行卡和证件数据,且无法恢复。\n\n建议在清空前先导出备份!",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = clearDataPasswordInput,
            onValueChange = { clearDataPasswordInput = it },
            label = { Text("请输入主密码确认") },
            ...
        )
    }
}
```

**修改后:**
```kotlin
title = {
    Text(context.getString(R.string.clear_all_data_confirm))
},
text = {
    Column {
        Text(
            context.getString(R.string.clear_all_data_warning),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = clearDataPasswordInput,
            onValueChange = { clearDataPasswordInput = it },
            label = { Text(context.getString(R.string.enter_master_password_to_confirm)) },
            ...
        )
    }
}
```

##### Toast 提示消息
**修改前:**
```kotlin
Toast.makeText(context, "正在清空数据...", Toast.LENGTH_SHORT).show()
Toast.makeText(context, "主密码错误", Toast.LENGTH_SHORT).show()
Toast.makeText(context, "无法启用指纹解锁", Toast.LENGTH_SHORT).show()
Toast.makeText(context, "指纹解锁已禁用", Toast.LENGTH_SHORT).show()
```

**修改后:**
```kotlin
Toast.makeText(context, context.getString(R.string.clearing_data), Toast.LENGTH_SHORT).show()
Toast.makeText(context, context.getString(R.string.password_incorrect), Toast.LENGTH_SHORT).show()
Toast.makeText(context, context.getString(R.string.biometric_cannot_enable), Toast.LENGTH_SHORT).show()
Toast.makeText(context, context.getString(R.string.biometric_unlock_disabled), Toast.LENGTH_SHORT).show()
```

##### 对话框按钮
**修改前:**
```kotlin
confirmButton = {
    TextButton(...) {
        Text("确认清空")
    }
},
dismissButton = {
    TextButton(...) {
        Text("取消")
    }
}
```

**修改后:**
```kotlin
confirmButton = {
    TextButton(...) {
        Text(context.getString(R.string.confirm))
    }
},
dismissButton = {
    TextButton(...) {
        Text(context.getString(R.string.cancel))
    }
}
```

## 修改文件列表

1. `app/src/main/res/values/strings.xml` - 添加英文字符串资源
2. `app/src/main/res/values-zh/strings.xml` - 添加中文字符串资源
3. `app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt` - 替换硬编码文本为字符串资源引用

## 影响范围

- ✅ 自动填充设置项标题和副标题
- ✅ 清空所有数据设置项标题和副标题
- ✅ 清空数据确认对话框标题、内容和输入框提示
- ✅ 清空数据对话框按钮（确认、取消）
- ✅ 清空数据相关的 Toast 提示
- ✅ 指纹解锁相关的 Toast 提示

## 支持的语言

- 🇺🇸 English
- 🇨🇳 简体中文

## 测试建议

### 1. 测试中文环境
```bash
# 切换到中文
adb shell "setprop persist.sys.locale zh-CN; setprop ctl.restart zygote"
```

验证：
- [ ] 自动填充显示"自动填充"
- [ ] 清空所有数据显示"清空所有数据"
- [ ] 对话框标题显示"确认清空所有数据?"
- [ ] Toast 提示显示中文

### 2. 测试英文环境
```bash
# 切换到英文
adb shell "setprop persist.sys.locale en-US; setprop ctl.restart zygote"
```

验证：
- [ ] 自动填充显示"Autofill"
- [ ] 清空所有数据显示"Clear All Data"
- [ ] 对话框标题显示"Confirm Clear All Data?"
- [ ] Toast 提示显示英文

## 兼容性

- ✅ Android 8.0+ (API 26+)
- ✅ 向后兼容现有功能
- ✅ 不影响其他已有的多语言功能

## 注意事项

1. 所有硬编码文本已全部替换为字符串资源引用
2. 使用了已有的 `confirm` 和 `cancel` 字符串资源
3. 新增的字符串资源遵循项目现有的命名规范
4. Toast 提示和对话框文本都已支持多语言

## 完成状态

- ✅ 字符串资源已添加（英文、中文）
- ✅ 代码已更新使用字符串资源
- ✅ 编译验证通过
- ⏳ 待用户测试验证

---

**修复完成** ✅
所有自动填充和清空数据功能的文本已完全支持多语言！
