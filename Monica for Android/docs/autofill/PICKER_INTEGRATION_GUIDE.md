# AutofillPicker 集成指南

## 问题说明

当前的自动填充服务直接在系统弹窗中显示所有匹配的密码列表,而不是使用我们新设计的 `AutofillPickerActivity` UI。

从用户截图可以看到,显示的是系统原生的自动填充列表,而不是我们设计的Material Design 3风格的选择器界面。

## 解决方案

需要修改 `MonicaAutofillService` 或相关的响应构建器,当有多个密码匹配时:

1. **不要**直接创建多个 Dataset
2. **而是**使用 `AutofillPickerLauncher.createPickerResponse()` 创建一个特殊的 Dataset
3. 用户点击这个 Dataset 后,会启动 `AutofillPickerActivity`,显示我们的新UI

## 集成步骤

### 1. 在 SafeResponseBuilder 中添加判断逻辑

```kotlin
fun buildResponse(
    passwords: List<PasswordEntry>,
    parsedFields: List<ParsedFieldInfo>,
    inlineRequest: InlineSuggestionsRequest?,
    packageName: String?,
    domain: String?,
    parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
): BuildResult {
    
    // 如果有多个密码匹配,使用 Picker UI
    if (passwords.size > 1) {
        val pickerResponse = AutofillPickerLauncher.createPickerResponse(
            context = context,
            passwords = passwords,
            packageName = packageName,
            domain = domain,
            parsedStructure = parsedStructure
        )
        
        return BuildResult(
            response = pickerResponse,
            datasetsCreated = 1,
            datasetsFailed = 0,
            errors = emptyList()
        )
    }
    
    // 如果只有一个密码,直接填充
    if (passwords.size == 1) {
        return AutofillPickerLauncher.createDirectFillResponse(
            context = context,
            password = passwords[0],
            parsedStructure = parsedStructure
        )
    }
    
    // 原有逻辑...
}
```

### 2. 修改 MonicaAutofillService

在 `MonicaAutofillService` 中找到创建 FillResponse 的地方,确保传递所需的参数:

```kotlin
// 获取匹配的密码
val matchedPasswords = getMatchedPasswords(packageName, domain)

// 使用 SafeResponseBuilder 构建响应
val result = safeResponseBuilder.buildResponse(
    passwords = matchedPasswords,
    parsedFields = parsedFields,
    inlineRequest = inlineRequest,
    packageName = packageName,
    domain = domain,
    parsedStructure = parsedStructure
)
```

### 3. 处理 Activity 返回结果

在 `MonicaAutofillService` 的 `onFillRequest` 中,需要处理从 `AutofillPickerActivity` 返回的结果:

```kotlin
// 这部分逻辑已经在 AutofillResultHandler 中实现
// 需要在服务中集成这个处理器
```

## 当前状态

✅ 已完成:
- `AutofillPickerActivity` - 新的UI界面
- `AutofillPickerScreen` - Compose UI组件
- `AutofillPickerLauncher` - 启动器和响应构建器
- `AutofillResultHandler` - 结果处理器
- 所有UI组件(搜索栏、列表项、标签等)

❌ 待完成:
- 在 `SafeResponseBuilder` 或 `MonicaAutofillService` 中集成 `AutofillPickerLauncher`
- 确保多密码匹配时使用新UI而不是直接显示Dataset列表

## 测试方法

1. 构建并安装新版本APK
2. 在测试应用中触发自动填充
3. 如果有多个匹配的密码,应该看到一个"选择密码 (N)" 的选项
4. 点击后应该打开我们的新UI界面,而不是系统原生列表

## 注意事项

- 确保 `PasswordEntry` 实现了 `Parcelable`(已完成)
- 确保在 `AndroidManifest.xml` 中注册了 `AutofillPickerActivity`
- 测试时需要完全卸载旧版本,避免缓存问题
