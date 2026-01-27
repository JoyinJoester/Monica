# 自动填充UI改进 - 当前状态

## 问题描述

用户看到的自动填充界面仍然是旧版本的系统原生列表,而不是我们新设计的Material Design 3风格的选择器界面。

## 根本原因

`MonicaAutofillService` 当前直接创建多个 Dataset 并显示在系统弹窗中,而没有使用我们新开发的 `AutofillPickerActivity`。

## 已完成的工作

✅ **UI组件** (100%完成)
- `AutofillPickerActivity` - 主Activity
- `AutofillPickerScreen` - Compose主屏幕
- `AutofillSearchBar` - 搜索栏组件
- `PasswordList` - 密码列表组件
- `PasswordListItem` - 密码列表项组件
- `PaymentInfoList` - 账单信息列表组件
- `PaymentInfoListItem` - 账单信息列表项组件
- `SearchUtils` - 搜索工具函数
- `AppIconCache` - 应用图标缓存

✅ **数据层** (100%完成)
- `AutofillItem` - 自动填充项数据类
- `PaymentInfo` - 账单信息数据类
- `CardBrand` - 卡品牌识别
- `PasswordEntry` 实现 Parcelable

✅ **集成层** (100%完成)
- `AutofillPickerLauncher` - 启动器和响应构建器
- `AutofillResultHandler` - 结果处理器
- `PaymentInfoFiller` - 账单信息填充器

✅ **配置** (100%完成)
- `AndroidManifest.xml` 中已注册 `AutofillPickerActivity`
- `build.gradle` 中已添加 `kotlin-parcelize` 插件
- 所有编译错误已修复

## 待完成的工作

❌ **服务集成** (0%完成)

需要修改 `MonicaAutofillService` 或 `SafeResponseBuilder`,在有多个密码匹配时:

1. 调用 `AutofillPickerLauncher.createPickerResponse()` 而不是直接创建多个 Dataset
2. 这样用户点击后会打开我们的新UI

### 具体修改位置

需要在以下文件中进行修改:

**选项1: 修改 SafeResponseBuilder.kt**
```kotlin
// 在 buildResponse 方法开始处添加
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
```

**选项2: 修改 MonicaAutofillService.kt**
在创建 FillResponse 的地方,检查密码数量并决定使用哪种方式。

## 为什么用户看到的是旧UI?

当前流程:
```
用户触发自动填充
    ↓
MonicaAutofillService.onFillRequest()
    ↓
创建多个 Dataset (每个密码一个)
    ↓
系统显示原生列表 ← 用户看到的就是这个
```

期望流程:
```
用户触发自动填充
    ↓
MonicaAutofillService.onFillRequest()
    ↓
检测到多个密码
    ↓
AutofillPickerLauncher.createPickerResponse()
    ↓
创建一个特殊的 Dataset (显示"选择密码 (N)")
    ↓
用户点击
    ↓
启动 AutofillPickerActivity ← 显示我们的新UI
```

## 下一步行动

1. 找到 `MonicaAutofillService` 中创建 FillResponse 的具体位置
2. 添加密码数量判断逻辑
3. 集成 `AutofillPickerLauncher`
4. 测试验证

## 测试清单

完成集成后,需要测试:

- [ ] 单个密码匹配 - 应该直接填充
- [ ] 多个密码匹配 - 应该显示"选择密码 (N)"选项
- [ ] 点击选择选项 - 应该打开新UI
- [ ] 搜索功能 - 应该能过滤密码
- [ ] 标签切换 - 密码/账单信息切换
- [ ] 选择密码 - 应该正确填充到表单
- [ ] 取消操作 - 应该正确关闭

## 相关文件

- `app/src/main/java/takagi/ru/monica/autofill/MonicaAutofillService.kt`
- `app/src/main/java/takagi/ru/monica/autofill/core/SafeResponseBuilder.kt`
- `app/src/main/java/takagi/ru/monica/autofill/AutofillPickerLauncher.kt`
- `app/src/main/java/takagi/ru/monica/autofill/AutofillPickerActivity.kt`
