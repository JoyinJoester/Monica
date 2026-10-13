# 原始数据库管理交互验证（1.0.320）

日期：2026-10-14。普通版和 F-Droid 使用相同的管理页实现；版本分别保持 versionCode 12 和 27，未创建发布或标签。

## 实现

- 每栏的条目宽度受栏宽约束，名称最多两行，列表独立纵向滚动。
- 复用密码页 SwipeActions：右滑进入选择模式，点击切换单项，再右滑按锚点选择区间。清空最后一项后仍保持选择模式，可明确取消。
- 点击、触摸或滚动一栏会聚焦该栏。两栏分别保存路径、搜索、选择及前进／后退历史。
- 底部浏览栏提供后退、前进、新建文件夹、切换栏、上一级；选择栏提供定向复制、移动、更多和取消。
- 长按与条目更多按钮共用菜单：已选条目使用该栏的整个选择集，未选条目只操作该条目。复制、移动在确认前固定来源与目标路径。
- 修复普通 MDBX 条目被跨格式白名单误拦截；允许现有的 is_favorite 和 created_at 字段，导出快照从原始载荷读取它们。

## 自动验证

复用公共 AVD Monica_Issue136_API_32（Android 32、x86_64），仅创建合成测试数据，测试夹具负责清理自己创建的记录。没有清除共享 AVD 的应用数据。

| 验证 | 普通版 | F-Droid |
| --- | --- | --- |
| DatabaseManagerSelectionTest | 3/3 | 3/3 |
| DatabaseManagerPaneStateTest | 5/5 | 5/5 |
| DatabaseManagerPaneTest | 7/7 | 7/7 |
| DatabaseManagerTransferTest | 13/13 | 13/13 |
| Debug 应用及测试 APK 构建 | 通过 | 通过 |

界面用例覆盖：两行标题和菜单可达、两栏独立滚动与导航、横屏深色及 1.4 倍字体、点击与滑动选择、长按单项／多项、状态恢复。完整页面用例从左栏批量复制两个密码到右栏 KeePass 文件夹，再从右栏长按移动一个条目回左栏 MDBX，检查方向、目标文件夹、前进／后退和传输结果。

存储用例覆盖：文件夹批量移动、原始未知格式同格式复制、附件字节保留、笔记附件跨格式传输、自定义字段、源数据版本变化保护，以及没有 Room 投影时的收藏／创建时间读取。未知字段与未知模式跨格式移动仍被阻止，源数据保留。

KeePass 跨格式移动成功后，源条目按原有策略进入回收站。测试分别核验原文件夹只剩一项、回收站保留已移动条目、MDBX 收到新条目。

## 复现命令

在相应 Android 根目录运行，普通版额外带 -PdailySeq=01：

```text
gradlew :app:testDebugUnitTest --tests '*DatabaseManagerSelectionTest' --tests '*DatabaseManagerPaneStateTest' :app:assembleDebug :app:assembleDebugAndroidTest -PincludeX86TestAbi
adb -s emulator-5554 shell am instrument -w -r -e class takagi.ru.monica.ui.screens.DatabaseManagerPaneTest,takagi.ru.monica.repository.DatabaseManagerTransferTest <applicationId>.test/androidx.test.runner.AndroidJUnitRunner
```

先安装对应 x86_64 Debug APK 和测试 APK；applicationId 使用各版输出元数据中的值。

## 设计与范围

本地 M3E Canvas 完成浏览、选择及菜单三种状态，检查过编辑与导出；实际 Compose 渲染检查了双栏长名称、底部动作及长按菜单。草图 JSON、分享链接和截图保存在本地 docs/design/manager-interaction-320，未加入 Git。

本次复用现有打开、重命名、复制、移动及新建文件夹能力。跨格式仍受原有无损转换、附件大小和凭据绑定限制；没有新增通用删除或改变回收站策略。设备验证仅覆盖上述 API 32 虚拟机，不代表所有厂商设备。
