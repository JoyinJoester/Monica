# Bottom Sheet 功能实现总结

## 功能概述
实现了 Material Design 3 Expressive 风格的 Bottom Sheet（底部抽屉）功能，用户可以在设置中启用，将传统的底部导航栏变为可上拉的抽屉式导航。

## 主要特性

### 1. M3 Expressive 设计
- ✅ 使用 Material Design 3 的 Expressive 风格
- ✅ 流畅的弹性动画效果（Spring Animation）
- ✅ 圆角设计（28dp 顶部圆角）
- ✅ 自然的手势交互（上拉/下拉）

### 2. 功能特点
- **可选启用**：在设置中有独立开关控制
- **保留原UI**：未启用时保持原有底部导航栏不变
- **显示所有项**：抽屉中显示所有导航项，包括隐藏的项
- **状态标识**：隐藏的项会显示"已隐藏"标签
- **智能切换**：点击任意项后自动收起抽屉

### 3. 交互体验
- 🎯 拖拽手柄：顶部有明显的拖拽指示器
- 🎯 手势识别：支持上滑展开、下拉收起
- 🎯 背景遮罩：展开时显示半透明黑色背景
- 🎯 弹性动画：使用中等弹性的 Spring 动画
- 🎯 阻尼效果：拖拽到边界时有阻尼感

## 技术实现

### 1. 数据模型（AppSettings.kt）
```kotlin
data class AppSettings(
    // ...其他设置
    val bottomSheetEnabled: Boolean = false // 默认关闭
)
```

### 2. 核心组件（ExpressiveBottomSheet.kt）
- **动画系统**：使用 `Animatable` 和 `spring()` 实现流畅动画
- **手势检测**：通过 `detectVerticalDragGestures` 处理拖拽
- **高度管理**：
  - 收起状态：80dp
  - 展开状态：400dp
  - 动态偏移：通过 `graphicsLayer` 实现

### 3. 集成位置（SimpleMainScreen.kt）
```kotlin
bottomBar = {
    if (appSettings.bottomSheetEnabled) {
        ExpressiveBottomSheet(...)  // 使用抽屉
    } else {
        NavigationBar(...)  // 使用传统导航栏
    }
}
```

### 4. 设置界面（SettingsScreen.kt）
```kotlin
SettingsSwitchItem(
    icon = Icons.Default.DragHandle,
    title = stringResource(R.string.settings_bottom_sheet_title),
    subtitle = stringResource(R.string.settings_bottom_sheet_description),
    checked = settings.bottomSheetEnabled,
    onCheckedChange = { enabled ->
        viewModel.updateBottomSheetEnabled(enabled)
    }
)
```

### 5. ViewModel & Manager
- **SettingsViewModel**：添加 `updateBottomSheetEnabled()` 方法
- **SettingsManager**：
  - 新增 `BOTTOM_SHEET_ENABLED_KEY` 存储键
  - 实现 `updateBottomSheetEnabled()` 数据持久化
  - 在 `settingsFlow` 中读取设置值

## 字符串资源

### 英文（values/strings.xml）
```xml
<string name="settings_bottom_sheet_title">Bottom Sheet Mode</string>
<string name="settings_bottom_sheet_description">Enable pull-up drawer navigation</string>
<string name="all_navigation_items">All Navigation Items</string>
<string name="hidden">Hidden</string>
```

### 中文（values-zh/strings.xml）
```xml
<string name="settings_bottom_sheet_title">底部抽屉模式</string>
<string name="settings_bottom_sheet_description">启用可上拉的抽屉式导航</string>
<string name="all_navigation_items">所有导航项</string>
<string name="hidden">已隐藏</string>
```

### 越南语（values-vi/strings.xml）
```xml
<string name="settings_bottom_sheet_title">Chế độ ngăn kéo dưới</string>
<string name="settings_bottom_sheet_description">Bật điều hướng ngăn kéo có thể kéo lên</string>
<string name="all_navigation_items">Tất cả mục điều hướng</string>
<string name="hidden">Đã ẩn</string>
```

## 文件清单

### 新增文件
- ✅ `app/src/main/java/takagi/ru/monica/ui/components/ExpressiveBottomSheet.kt`

### 修改文件
- ✅ `app/src/main/java/takagi/ru/monica/data/AppSettings.kt`
- ✅ `app/src/main/java/takagi/ru/monica/ui/SimpleMainScreen.kt`
- ✅ `app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt`
- ✅ `app/src/main/java/takagi/ru/monica/viewmodel/SettingsViewModel.kt`
- ✅ `app/src/main/java/takagi/ru/monica/utils/SettingsManager.kt`
- ✅ `app/src/main/res/values/strings.xml`
- ✅ `app/src/main/res/values-zh/strings.xml`
- ✅ `app/src/main/res/values-vi/strings.xml`

## 使用方法

### 启用 Bottom Sheet
1. 打开应用 **设置**
2. 在 **安全** 部分找到 **底部抽屉模式**
3. 打开开关即可启用

### 使用 Bottom Sheet
1. **查看可见项**：底部显示当前可见的导航项（与原导航栏相同）
2. **上拉展开**：拖动顶部手柄或上滑展开完整抽屉
3. **查看所有项**：展开后显示所有导航项，包括隐藏的
4. **切换页面**：点击任意项切换到对应页面并自动收起
5. **关闭抽屉**：下拉手柄、点击背景或点击导航项

## 设计亮点

### 1. 渐进式设计
- 默认关闭，不影响现有用户体验
- 可随时在设置中开启/关闭
- 切换后立即生效，无需重启

### 2. 视觉层次
- **收起状态**：与原导航栏视觉一致
- **展开状态**：清晰的层级结构
  - 顶部：拖拽手柄 + 可见导航项
  - 中部：标题"所有导航项"
  - 底部：完整导航项列表（带状态标识）

### 3. 动画细节
- **弹性动画**：`Spring.DampingRatioMediumBouncy`
- **阻尼效果**：接近边界时减速
- **背景渐变**：随拖拽距离渐变透明度
- **自动判断**：拖拽超过50%自动展开/收起

### 4. 信息架构
```
Bottom Sheet (展开状态)
├── 拖拽手柄
├── 底部导航栏（可见项）
│   ├── 密码
│   ├── 验证器
│   └── 证件
└── 所有导航项列表
    ├── 密码 ✓
    ├── 验证器 ✓
    ├── 证件 ✓
    ├── 银行卡 [已隐藏]
    └── 生成器 [已隐藏]
```

## 技术优势

1. **性能优化**
   - 使用 `Animatable` 而非 `State` 避免重组
   - `graphicsLayer` 实现硬件加速动画
   - 条件渲染背景遮罩

2. **代码复用**
   - 复用现有的 `BottomNavContentTab` 枚举
   - 共享导航逻辑和图标资源
   - 统一的状态管理

3. **可维护性**
   - 独立的组件文件
   - 清晰的职责分离
   - 完善的类型安全

## 后续优化建议

1. **动画增强**
   - 添加项目进入/退出动画
   - 支持自定义动画速度

2. **功能扩展**
   - 支持长按快速切换
   - 添加项目重排序功能
   - 记忆上次展开状态

3. **视觉优化**
   - 支持自定义抽屉高度
   - 主题色动态适配
   - 添加触觉反馈

## 构建状态
✅ **BUILD SUCCESSFUL** - 所有功能已完成并通过编译

---

**实现时间**: 2025年10月20日  
**设计风格**: Material Design 3 Expressive  
**兼容性**: Android 完全兼容
