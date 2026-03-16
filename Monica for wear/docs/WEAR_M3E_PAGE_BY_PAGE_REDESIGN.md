# Monica Wear Wear OS M3E 逐页重设计与升级方案

## 1. 目标

本方案用于重新设计 `Monica for wear` 的全部核心页面，并按页面逐步升级到官方 `Wear OS Material 3 Expressive` 体系。

核心目标：

- 以官方 `androidx.wear.compose.material3` 组件为主，尽可能少写自定义容器。
- 让组件自己适配圆屏边缘、列表变形、底部边缘按钮和不同屏幕尺寸。
- 保证页面先可用，再表达，再精致，不再用“手工圆屏骨架”去覆盖官方适配行为。
- 每个页面都定义明确的升级顺序、动效策略、验收标准。

## 2. 已确认的设计原则

基于 Android 官方 Wear OS 文档，当前设计必须遵循这些原则：

- `Material 3 Expressive` 是针对圆屏设计的形态系统，不需要再手工套一层“圆形外壳”。
- 列表页默认优先使用滚动布局。
- `AppScaffold` + `ScreenScaffold` 负责 `TimeText`、`ScrollIndicator`、底部主操作与过渡协调。
- 列表内容优先使用 `TransformingLazyColumn`，让条目在滚动时自然变形。
- 底部主操作优先使用 `EdgeButton`，并放进 `ScreenScaffold` 的 `edgeButton` 槽位。
- 百分比内边距优于手写固定宽度，尤其在圆屏上。
- 大屏不允许显示更少信息；超过断点时只能增强，不能退化。

## 3. 当前问题复盘

这一轮重构里已经暴露出几个反模式，后续必须禁止：

- 禁止手工做“圆屏骨架”来包住整个页面。
- 禁止在空状态页上再悬浮一层与正文争抢空间的底部按钮。
- 禁止用固定宽度的大方卡模拟“贴合圆屏”。
- 禁止用装饰性容器替代 `ScreenScaffold` 的官方底部动作能力。
- 禁止为了视觉效果牺牲触达性、层级和可读性。

## 4. 设计系统总规则

### 4.1 布局

- 入口层只保留一个 `AppScaffold`。
- 单屏页面统一使用 `ScreenScaffold`。
- 列表页统一使用 `TransformingLazyColumn`。
- 首项优先使用 `ListHeader` 或官方 header 模式，不再手工垂直堆标题。
- 页内边距使用响应式 padding，优先百分比策略。
- 底部主操作优先 `EdgeButton`，次操作放在列表内，不悬浮遮挡正文。

### 4.2 组件优先级

优先级从高到低：

1. 官方 `ScreenScaffold` / `HorizontalPagerScaffold`
2. 官方 `TransformingLazyColumn`
3. 官方 `EdgeButton`
4. 官方 `Button` / `FilledTonalButton` / `OutlinedButton`
5. 官方 `ButtonGroup`
6. 官方 `Card` / `TitleCard` / `AppCard`
7. 官方 `SwitchButton` / `SplitSwitchButton`
8. 官方 `AlertDialog` / `ConfirmationDialog`
9. 官方 `AnimatedPage` / `AnimatedText`
10. 只有在官方没有对应模式时，才允许写轻量自定义布局

### 4.3 动效

统一节奏：

- 微交互：150ms 到 220ms
- 列表进入：30ms 到 50ms 轻微 stagger
- 页面切换：官方 scaffold / pager 默认动效优先
- 状态切换：优先透明度、缩放、shape 变化，不动画宽高
- 所有动效必须可打断，不得阻塞点击

### 4.4 响应式与断点

- 基础适配依赖官方组件本身的圆屏适配能力。
- 大于等于 `225dp` 宽度时允许显示更多说明、副标题或额外操作。
- 小屏优先保留主信息与主操作。
- 字号变大时，页面仍可完整滚动，不得截断关键操作。

## 5. 页面清单与目标状态

当前主要页面与文件映射：

- 首页验证码分页页：[TotpPagerScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/TotpPagerScreen.kt)
- 验证码卡片：[TotpCard.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/TotpCard.kt)
- 搜索与管理页：[SearchOverlay.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/SearchOverlay.kt)
- 设置页：[SettingsScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/SettingsScreen.kt)
- PIN 锁页：[PinLockScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/PinLockScreen.kt)
- 修改锁定方式对话框：[ChangeLockDialog.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/ChangeLockDialog.kt)

## 6. 逐页重设计

### 6.1 首页验证码分页页

文件：

- [TotpPagerScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/TotpPagerScreen.kt)
- [TotpCard.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/TotpCard.kt)

目标：

- 成为唯一的“高频主场景”页面。
- 空数据、有数据、无 WebDAV 三种状态清晰切换。
- 分页切换、复制反馈、剩余时间强调都体现表达力。

官方组件方案：

- 外层：`AppScaffold`
- 分页：`HorizontalPagerScaffold`
- 页动画：`AnimatedPage`
- 页面内内容：`ScreenScaffold`
- 主要 CTA：`EdgeButton`
- 卡片：优先官方 `Card`
- 指示器：用 `HorizontalPagerScaffold` 默认页指示器，不手工重复造

页面结构：

1. `HorizontalPagerScaffold` 承载 `PagerState`
2. 每页使用 `AnimatedPage`
3. 每页内部只保留一个主验证码卡
4. 主卡下方不再额外挂自定义 dock
5. 搜索入口和设置入口改为页内二级操作或顶部/列表入口，而不是悬浮覆盖正文

空状态方案：

- 无数据且未配置：列表页样式，不做中央大方卡
- 第一项 `ListHeader`
- 第二项官方 `Card`
- 底部使用 `EdgeButton("配置 WebDAV")`

- 无数据但已配置：`ListHeader` + `Card`
- 底部 `EdgeButton("添加验证器")`

动效：

- 分页：用 `AnimatedPage`
- 验证码变化：保留 `AnimatedText` 或轻量 crossfade
- 倒计时临界提醒：颜色和透明度变化，不做夸张脉冲

验收标准：

- 空状态下正文和底部主操作绝不重叠
- 不出现自定义圆壳包裹整个页面
- 分页器在不同圆屏尺寸下保持稳定

### 6.2 搜索与管理页

文件：

- [SearchOverlay.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/SearchOverlay.kt)

目标：

- 成为“查找、跳转、编辑、删除、新增”的统一管理入口。
- 不再像一层随手盖上去的透明遮罩。

官方组件方案：

- 外层：`ScreenScaffold`
- 内容：`TransformingLazyColumn`
- 搜索输入：`TextField` 或当前 `WearTextField` 的轻量包装
- 结果项：官方 `Card` 或 `Button`
- 主操作：`EdgeButton("添加验证器")`
- 删除确认：`AlertDialog`
- 成功反馈：`ConfirmationDialog`
- 行内危险操作：`SwipeToReveal` 优先于手写展开按钮

页面结构：

1. 首项 `ListHeader("搜索验证器")`
2. 第二项搜索输入框
3. 结果项默认只显示名称、账号、验证码
4. 编辑/删除改为 `SwipeToReveal` 或进入详情页操作，不在列表内平铺 3 个图标按钮
5. 底部唯一主操作为 `EdgeButton("添加验证器")`

空状态方案：

- 空状态仅保留一张信息卡
- 文案 + 一个明确动作
- 不叠加第二层底部 dock

动效：

- 搜索结果过滤用 `AnimatedContent` 或 `Crossfade`
- 新结果出现时轻量 stagger
- 删除成功用 `ConfirmationDialog`

验收标准：

- 遮罩层与底层页面视觉分离明确
- 删除/编辑入口不会挤压正文
- 小屏下单条结果仍可完整点击

### 6.3 设置页

文件：

- [SettingsScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/SettingsScreen.kt)

目标：

- 变成标准 Wear 列表页，而不是“很多卡片堆起来”的手机压缩版。
- 让同步、外观、语言、安全、数据管理都有明确层级。

官方组件方案：

- 外层：`ScreenScaffold`
- 内容：`TransformingLazyColumn`
- 分组头：`ListHeader` / `ListSubheader`
- 设置项：`Button`、`SwitchButton`、`SplitSwitchButton`
- 同步主操作：`EdgeButton("立即同步")`，只在合适状态下显示
- 说明型信息：`Card`

页面结构：

1. 顶部总标题只保留一次
2. `Cloud Sync`
3. `Authenticator`
4. `Appearance`
5. `Security`
6. `Data`
7. `About`

同步区设计：

- 第一项是同步状态卡
- WebDAV 未配置时：卡片说明 + `EdgeButton("配置 WebDAV")`
- 已配置时：卡片显示状态与上次同步时间 + `EdgeButton("立即同步")`

设置项设计：

- 单纯点击进入的项用 `Button`
- 开关项用 `SwitchButton`
- 危险项单独一个 section，颜色和文案明确区分

动效：

- section 出现时不额外强调
- 同步成功/失败使用 `ConfirmationDialog` 或状态卡颜色过渡

验收标准：

- 设置页必须是标准滚动列表节奏
- 不允许 section 和 item 的宽度各不相同
- 不允许靠手工圆角容器制造“圆屏感”

### 6.4 PIN 锁页

文件：

- [PinLockScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/PinLockScreen.kt)

目标：

- 保留拨号器感觉，但去掉过于自定义的舞台外壳。
- 成为一个少量自定义布局、主要交互仍可读可点的页面。

官方组件方案：

- 外层：`ScreenScaffold`
- 标题与说明：官方 `Text`
- 数字键：可保留轻量自定义网格，但优先用官方 `Button` / `FilledTonalButton`
- 删除 / 确认：官方 `FilledTonalIconButton` / `FilledIconButton`

设计原则：

- 键盘区居中，但不再放在巨大圆壳面板里
- 错误反馈只做轻量震动与颜色变化
- 首次设置与确认页复用同一布局

动效：

- 按键按下缩放 0.96 左右
- 错误时水平轻抖
- 输入圆点使用轻量放大/淡入

验收标准：

- 所有键可稳定点击
- 字体放大时标题不压住键盘
- 不依赖固定屏幕比例才能成立

### 6.5 对话框与配置流

文件：

- [SettingsScreen.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/screens/SettingsScreen.kt)
- [ChangeLockDialog.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/ChangeLockDialog.kt)
- [SearchOverlay.kt](C:/Users/joyins/Desktop/Monica-all/Monica%20for%20wear/src/main/java/takagi/ru/monica/wear/ui/components/SearchOverlay.kt)

目标：

- 所有对话框统一升级成官方滚动式 dialog 体验。

官方组件方案：

- 纯确认：`AlertDialog`
- 成功反馈：`ConfirmationDialog`
- 多字段编辑：滚动 `ScreenScaffold` + `TransformingLazyColumn`
- 选择器：优先官方 `Picker` / `PickerGroup`

规则：

- 不再手工做全屏黑底 + 自己拼按钮区
- 不再在对话框底部额外绘制渐变浮层按钮条

## 7. 逐页升级顺序

按风险从低到高、按用户感知从高到低推进：

### Phase 1：基础设施纠偏

- 去掉会覆盖官方适配的自定义圆屏骨架
- 统一 `AppScaffold` / `ScreenScaffold`
- 引入响应式 padding
- 为所有核心页面补 `WearPreviewDevices` 和 `WearPreviewFontScales`

### Phase 2：首页

- 升级 `TotpPagerScreen`
- 升级 `TotpCard`
- 接入 `HorizontalPagerScaffold` / `AnimatedPage`

### Phase 3：搜索页

- 搜索层改为标准 `ScreenScaffold` + `TransformingLazyColumn`
- 删除自定义底部 dock
- 引入 `EdgeButton`
- 列表项引入 `SwipeToReveal`

### Phase 4：设置页

- 变成标准分组列表
- 同步主动作迁移到 `EdgeButton`
- 统一设置项组件

### Phase 5：PIN 与对话框流

- 精简 PIN 页容器
- 统一所有编辑/确认/成功反馈对话框

### Phase 6：验收与回归

- 小圆屏
- 大圆屏
- 字体放大
- 空状态
- 有 1 条、3 条、10 条数据
- WebDAV 已配置 / 未配置

## 8. 每页升级前必须回答的 5 个问题

1. 这个页面到底是不是滚动页？
2. 底部主操作能否直接交给 `EdgeButton`？
3. 当前自定义容器是否真的必要？
4. 删除这个自定义容器后，官方组件是否已经能满足圆屏适配？
5. 这个页面在小圆屏 + 大字体下是否仍然完整可操作？

## 9. 验收清单

- 不再出现正文与悬浮按钮互相遮挡
- 所有页面在圆屏上都依赖官方组件适配，不依赖手工“圆屏骨架”
- 列表页统一为滚动式结构
- 主要 CTA 统一为 `EdgeButton`
- 页面间动效节奏统一
- 重要状态都有明确反馈
- 预览和截图测试覆盖关键尺寸

## 10. 参考官方资料

以下资料用于本方案，均为 Android 官方来源：

- Wear OS Material 3 Expressive 总览：<https://developer.android.com/design/ui/wear/guides/get-started>
- Material 3 Expressive 设计语言：<https://developer.android.com/design/ui/wear/guides/get-started/apply>
- Adaptive design：<https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design>
- Design quality tiers：<https://developer.android.com/design/ui/wear/guides/foundations/quality-tiers>
- Develop for different screen sizes：<https://developer.android.com/training/wearables/compose/screen-size>
- Wear Compose Material 3 API：<https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/package-summary>
- M2.5 到 M3 迁移说明：<https://developer.android.com/training/wearables/compose/migrate-to-material3>
- Wear Compose M3 release notes：<https://developer.android.com/jetpack/androidx/releases/wear-compose-m3>

本方案的判断重点来自这些官方信息：

- `Material 3 Expressive` 自带面向圆屏的 shape framework。
- `ScreenScaffold` 能协调 `TimeText`、`ScrollIndicator`、底部 `EdgeButton`。
- `TransformingLazyColumn` 是默认推荐的滚动页内容容器。
- `EdgeButton` 是官方 round device 主操作模式，不该自己再造一个底部圆 dock。

