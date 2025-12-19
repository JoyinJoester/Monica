# 手动集成 AutofillPicker - 详细步骤

由于 `MonicaAutofillService` 使用了复杂的自定义引擎架构,这里提供手动集成步骤。

## 方案1: 在响应创建时集成(推荐)

在 `MonicaAutofillService.kt` 中找到创建 `FillResponse` 的地方,添加以下逻辑:

```kotlin
// 在获取匹配密码后
val matchedPasswords: List<PasswordEntry> = // ... 你的密码匹配逻辑

// 🎯 新增: 当有多个密码时使用 Picker UI
if (matchedPasswords.size > 1) {
    val pickerResponse = AutofillPickerLauncher.createPickerResponse(
        context = applicationContext,
        passwords = matchedPasswords,
        packageName = packageName,
        domain = domain,
        parsedStructure = parsedStructure
    )
    
    callback.onSuccess(pickerResponse)
    return
}

// 单个密码时直接填充
if (matchedPasswords.size == 1) {
    val directResponse = AutofillPickerLauncher.createDirectFillResponse(
        context = applicationContext,
        password = matchedPasswords[0],
        parsedStructure = parsedStructure
    )
    
    callback.onSuccess(directResponse)
    return
}
```

## 方案2: 修改 SafeResponseBuilder(已完成)

`SafeResponseBuilder` 已经更新,支持自动使用 Picker UI。

### 更新的方法签名:

```kotlin
fun buildResponse(
    passwords: List<PasswordEntry>,
    parsedFields: List<ParsedFieldInfo>,
    inlineRequest: InlineSuggestionsRequest?,
    packageName: String,
    domain: String? = null,  // 新增
    parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure? = null,  // 新增
    usePickerForMultiple: Boolean = true  // 新增
): BuildResult
```

### 使用方式:

```kotlin
val result = safeResponseBuilder.buildResponse(
    passwords = matchedPasswords,
    parsedFields = parsedFields,
    inlineRequest = inlineRequest,
    packageName = packageName,
    domain = domain,  // 传递域名
    parsedStructure = parsedStructure,  // 传递解析结构
    usePickerForMultiple = true  // 启用Picker UI
)
```

## 方案3: 在引擎层集成

如果你的项目使用 `AutofillEngine`,在引擎的响应构建方法中添加:

```kotlin
// 在 AutofillEngine 或相关类中
fun createFillResponse(
    passwords: List<PasswordEntry>,
    context: AutofillContext
): FillResponse? {
    
    // 多个密码时使用 Picker
    if (passwords.size > 1) {
        return AutofillPickerLauncher.createPickerResponse(
            context = context.androidContext,
            passwords = passwords,
            packageName = context.packageName,
            domain = context.domain,
            parsedStructure = context.parsedStructure
        )
    }
    
    // 单个密码直接填充
    if (passwords.size == 1) {
        return AutofillPickerLauncher.createDirectFillResponse(
            context = context.androidContext,
            password = passwords[0],
            parsedStructure = context.parsedStructure
        )
    }
    
    return null
}
```

## 关键点

1. **必须传递 `parsedStructure`**: `AutofillPickerLauncher` 需要知道字段信息才能正确填充
2. **packageName 和 domain**: 用于显示应用/网站名称
3. **多个密码判断**: `passwords.size > 1` 时使用 Picker UI
4. **单个密码优化**: `passwords.size == 1` 时直接填充,无需打开选择界面

## 测试验证

集成后,测试以下场景:

1. **单个密码匹配**
   - 应该直接显示该密码的 Dataset
   - 点击后直接填充

2. **多个密码匹配**
   - 应该显示 "选择密码 (N)" 的 Dataset
   - 点击后打开 `AutofillPickerActivity`
   - 显示新的 Material Design 3 UI
   - 可以搜索和选择密码

3. **无密码匹配**
   - 不显示任何自动填充建议

## 调试技巧

添加日志来验证集成:

```kotlin
android.util.Log.d("AutofillPicker", "Matched passwords: ${passwords.size}")
android.util.Log.d("AutofillPicker", "Using Picker UI: ${passwords.size > 1}")
android.util.Log.d("AutofillPicker", "Package: $packageName, Domain: $domain")
```

## 常见问题

### Q: 点击后没有打开新UI?
A: 检查 `AndroidManifest.xml` 是否注册了 `AutofillPickerActivity`(已确认注册)

### Q: 编译错误: PasswordEntry 不是 Parcelable?
A: 已修复,`PasswordEntry` 现在实现了 `Parcelable`

### Q: 仍然显示旧UI?
A: 确保:
1. 完全卸载旧版本APK
2. 重新安装新版本
3. 清除自动填充服务缓存(系统设置 > 自动填充服务 > 重新选择)

### Q: 如何禁用 Picker UI?
A: 在调用 `buildResponse` 时设置 `usePickerForMultiple = false`

## 下一步

完成集成后,可以考虑:
1. 添加生物识别认证(已有 `BiometricAuthActivity`)
2. 支持账单信息自动填充(UI已准备好)
3. 添加更多搜索过滤选项
4. 优化应用图标加载性能
