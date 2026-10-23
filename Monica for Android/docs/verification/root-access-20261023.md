# 独立 Root 授权与安全验证（2026-10-23）

普通版和 F-Droid 均接入独立 Root 通道，用于增强无障碍恢复、默认密码管理器设置。保留 Shizuku；用户选择 Root 后不会绑定 Shizuku 用户服务。

## 安全边界

- 首次 Root 请求必须经用户确认；仅切换单选项不授予权限。无 Root、拒绝、超时均停止该次操作，不自动换用另一通道。
- 授权请求有撤销版本检查：停用或切换方式会使排队请求失效；已经发起的请求即使迟到返回成功，也不能重新保存已撤销的应用内授权。
- 命令只允许身份检查、应用自身 APK 内的固定恢复辅助入口，以及当前 Android 用户下四个 secure 设置键：`enabled_accessibility_services`、`credential_service`、`credential_service_primary`、`autofill_service`。值以独立参数或严格单引号转义传入，拒绝未知键、非法用户及换行/NUL。每个特权命令再次检查 UID 0；辅助入口也独立检查 UID。
- 没有新增导出 Activity、Service、Provider 或 Root IPC 服务；没有新增 Root 依赖。新 Root 代码不解锁密码库、不读取其他应用数据，也不发送网络请求。应用系统备份保持禁用；授权选择不接入保险库导入/同步。
- 后台恢复同时要求后台保护、增强恢复、所选通道授权和已开启但断连的 Monica 无障碍服务；保留启动等待和持久化冷却。不会替用户授予无障碍权限；检测到其他服务的并发修改时停止覆盖。
- 默认管理器写入前持久化原配置，检查当前用户、应用服务和绑定权限；保留读回校验、失败回滚和手动恢复。已进入事务的修改会完成校验/必要的恢复；停用后不接受新的事务。进程意外退出时可用持久化备份检查并恢复配置。
- Root 命令失败后仍可在 Monica 内停用 Root。系统级授权仍由 Root 管理器控制；应用内停用与 Root 管理器撤销是两个不同操作。

## 验证结果

| 项目 | 普通版 | F-Droid |
| --- | --- | --- |
| Debug 应用与测试包构建 | 通过 | 通过 |
| 核心单元测试 | 42 通过 | 44 通过 |
| Android 32 设备测试 | 19 通过 | 19 通过 |

设备使用公共 `Monica_Issue136_API_32`（x86_64），没有创建新 AVD。测试结束后停止本任务启动的模拟器，保留数据盘。

设备测试包括：选择 Root 不授权、取消确认、无 su 的错误提示且不改变系统设置、大字体下操作可达、实际后台保护页和默认管理器弹窗、切换/停用清除增强恢复、失败后仍可停用、迟到授权与排队撤销竞态。辅助进程在非 Root 身份下明确拒绝；引号、分号、命令替换载荷还在真实 Android shell 中按普通文字回显，未执行载荷中的文件写入。

单元测试覆盖命令白名单、参数转义、进程拒绝/缺失/超时、输出长度限制，以及原有设置冲突检测、回滚和服务恢复规则。F-Droid 保留其包名适配及额外回归用例。

测试命令（在各自 Android 根目录运行；F-Droid 额外使用 `-PfdroidUniversal=true`）：

```text
gradlew.bat :app:testDebugUnitTest --tests '*autofill_ng.privilege.*' --tests '*autofill_ng.protection.*' --tests '*autofill_ng.defaultmanager.*' :app:assembleDebug :app:assembleDebugAndroidTest -PincludeX86TestAbi=true
adb -s emulator-5556 shell am instrument -w -r -e class takagi.ru.monica.ui.screens.RootAuthorizationUiTest,takagi.ru.monica.ui.screens.DefaultManagerUiTest,takagi.ru.monica.autofill_ng.protection.AutofillProtectionUiTest <applicationId>.test/androidx.test.runner.AndroidJUnitRunner
```

## 验证限制

公共模拟器未 Root，且为 Android 32。真实 Root 管理器的允许/撤销流程、UID 0 下的无障碍重连，以及 Android 14+ 默认凭据提供方的实际写入/恢复，尚未在 Root 真机验证。竞态测试中的身份成功结果是受控测试替身，不能当作 Root 真机授权成功。当前结果也不代表所有 ROM 或 Root 管理器均兼容。系统设置没有跨应用的原子事务，本实现通过变更检测、读回核验和回滚降低风险；异常中断后仍应刷新核对，必要时使用持久化备份恢复。

Root 管理器授予的是系统 Root 能力，命令白名单约束的是 Monica 的实现，不能抵御设备或 Root 管理器自身已被攻破。此记录是本次代码与测试覆盖的说明，不是“绝对安全”的证明。

## 设计、发行说明与交付

沿用并重新检查本地 M3E Canvas 的 `root-access-20261022` 草图。可编辑 JSON、完整 `#docz=` 本地链接及实际 UI 截图只保存在普通版 `docs/design/root-access-20261022/`；未加入 Git，本文不引用图片。

两版中英文下一版发行说明均已补齐 Root 功能、失败后停用与授权撤销保护。已发布的 1.0.321 归档、fastlane 28.txt、标签和版本配置保持原样；尚未切换新版本。本次仅提交源码、测试与文档；未创建 Release 或额外交付 APK。

详细测试计数及本次源码 SHA-256 见同目录 `root-access-20261023-results.json`。
