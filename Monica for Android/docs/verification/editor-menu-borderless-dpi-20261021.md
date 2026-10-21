# 编辑菜单连续弹窗与 DPI 自适应（2026-10-21）

## 调整

- 三点编辑菜单改为 Keyguard 风格的单一连续 M3 弹窗表面：外层统一使用 `surfaceContainerHigh`、24dp 圆角和 6dp 阴影；菜单项不再各自绘制背景、圆角、裁剪或间隔。
- 菜单宽度从固定 `224.dp–320.dp` 改为当前 `screenWidthDp × 0.76`，并限制在 `224.dp–320.dp`，因此显示大小／DPI改变时会随可用屏幕宽度重新计算。
- 普通版与 F-Droid 共用同一实现；URL 匹配、移动、删除和保存逻辑不变。
- 本地 M3E Canvas：`editor-menu-continuous-321-20261021`，可编辑 JSON、分享链接和截图保存在忽略的 `docs/design/editor-menu-borderless-321/`；已检查实际渲染为连续表面。

## 验证

- 两版 `:app:assembleDebug :app:assembleDebugAndroidTest -PincludeX86TestAbi` 构建通过；两版共用菜单源码 SHA-256 一致。
- 公共 AVD `Monica_Issue136_API_32`，Android 32，隔离用户 10：普通版 DPI 420、F-Droid DPI 280 各通过 `ProjectCredentialEditorTest#urlRulesMoveWithRowsAndReopenAfterSaving`，验证菜单打开、匹配策略选择、移动、保存与重开。
- 原生截图确认菜单项连接成一块连续弹窗，没有断开的卡片，删除项保留红色。此前无外包边方案的字体缩放测试不作为本次最终样式的独立验证。
- 最初两次设备用例受隔离用户锁屏干扰，未找到 Compose 层级；临时禁用该测试用户锁屏后重跑通过。测试后恢复显示密度、锁屏及相关设置，复用已运行的公共 AVD。
