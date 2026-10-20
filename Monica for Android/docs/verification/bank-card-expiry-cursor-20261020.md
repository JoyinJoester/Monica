# 银行卡有效期输入光标回归验证（2026-10-20）

用户录屏显示：输入月份 `07` 后，输入年份首位 `3`，文本变为 `07/3`，但光标停在 `/` 与 `3` 之间；继续输入 `1` 会插入到 `3` 前面。

## 根因与修复

共享银行卡表单将输入字符串 `073` 格式化为 `07/3`。通用 `OutlinedTextField` 的字符串封装保留了原始编辑光标偏移 3，在外部回写格式化文本时只做越界截断，没有为新插入的 `/` 增加偏移。密码编辑页的支付信息有同样的字符串格式化路径。

新增专用 `PaymentExpiryTextField`，使用 `TextFieldValue` 同时更新格式化文本、选区两端与输入法组合范围。选区移动保持本地状态，外部载入的存储值不自动格式化或触发保存。银行卡保持最多四位年份，密码支付信息保持两位年份；持久化表示、月份／年份拆分以及通用文本框保持现有方式。

## 修复前复现

在公共 API 32 x86_64 AVD `Monica_Issue136_API_32` 的隔离测试用户 10 上，使用真实 `EntryPaymentFields` 和银行卡的月份／年份拆分回写方式逐位输入。填充式与描边式两个测试均失败：

```text
expected:<TextRange(4, 4)> but was:<TextRange(3, 3)>
Tests run: 2, Failures: 2
```

该断言直接检查 Compose 编辑光标语义，而非只检查字符串结果。运行期间另有锁屏／用户切换导致的 `No compose hierarchies found`，解锁完成后重跑；未把环境启动失败作为业务复现证据。

## 修复后结果

| 验证 | 普通版 | F-Droid |
| --- | --- | --- |
| Debug APK 与 instrumentation APK 构建 | 通过 | 通过 |
| `PaymentExpiryEditTest` | 7 通过，0 失败 | 7 通过，0 失败 |
| `EntryPaymentExpiryInputTest`，API 32 x86_64 | 9 通过，0 失败 | 9 通过，0 失败 |

设备测试覆盖：填充式／描边式逐位输入及每步光标位置、四位年份逐位输入、带／不带分隔符粘贴、过滤和长度上限、正向及反向选区替换、月份中间编辑、退格删除年份和分隔符后继续输入、密码支付信息两位年份上限、外部加载旧值时不自动格式化或保存。单元测试另覆盖组合范围映射与截断后的组合范围清除。

执行命令（两版均使用 `-PincludeX86TestAbi` 打包模拟器 ABI；F-Droid 单元测试首次构建不需要此参数）：

```text
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests takagi.ru.monica.ui.components.PaymentExpiryEditTest -PincludeX86TestAbi --console=plain --max-workers=2
adb shell am instrument --user 10 -w -r -e class takagi.ru.monica.ui.components.EntryPaymentExpiryInputTest <applicationId>.test/androidx.test.runner.AndroidJUnitRunner
```

普通版应用 ID 为 `takagi.ru.monica`，F-Droid 为 `takagi.ru.monica.fdroid`。公共 AVD 在任务开始时未运行，本次启动后复用，测试结束恢复原用户与保持唤醒设置，再关闭模拟器；保留配置和数据盘，未清除保险库或用户数据。

普通版、F-Droid 与两个本地集成候选已同步组件、接入点、测试及 `1.0.321` 中英文未发布说明；F-Droid 同步中英文 fastlane `28.txt`，各不超过 500 字符。候选保留原有差异，未单独重新构建。版本号与发布历史保持不变。

本次验证使用真实 Compose 控件和模拟器输入事件；未在录屏所用手机及其第三方输入法上重测，未运行 Release 构建。录屏与截图仅留本地，不入库。
