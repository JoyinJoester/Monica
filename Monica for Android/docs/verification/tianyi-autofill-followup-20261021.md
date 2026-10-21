# 天翼云盘自动填充复报排查（2026-10-21）

## 反馈与范围

反馈中的两条凭据分别将 `com.cn21.ecloud` 填在网址字段，以及直接绑定天翼云盘应用。自动填充页截图选择了“仅本地”，未展示推荐条目。用户希望安装真实客户端验证。

本次未修改自动填充匹配或来源筛选代码，也未将该复报标记为已修复。

## 真实客户端尝试

- 从天翼云盘官网下载页及 `https://cloud.189.cn/api/portal/getClientByType.action?clientType=TELEANDROID` 获取 Android 安装包，未使用第三方重签包。
- 客户端 `com.cn21.ecloud`，版本 `11.1.1`，versionCode `635`；Android 最低 API 24，target API 30，包含 ARM64／ARMv7 原生库。
- 文件 SHA-256：`2780437866a0f1468da500e18ef15bc5c0c5b5baace59dae182b0b6577414444`。`apksigner verify --print-certs` 通过，签名证书 SHA-256：`b122369063be8c5a809c449c3e5655afe17bd8059a8273b498cc9c8b7e1b9ae8`。
- 安装至公共 AVD `Monica_Issue136_API_32` 的隔离用户 10。该 AVD 为 Android 32 x86_64，使用 `libndk_translation.so` 转译 ARM64。
- 应用启动阶段即退出／重启，未进入登录页。日志包含 ARM64 NDK translation 初始化、`androidx.core.app.CoreComponentFactory` 类加载失败，以及启动尝试中的原生 `SIGSEGV`。冷重启 AVD 后仍未进入登录页。
- 因此没有取得真实天翼云盘登录页的 AssistStructure，也没有完成该客户端的端到端填充。不能用下面的模拟用例替代这一结论。未登录真实账号。

## 现有实现回归

- 普通版 JVM：`AutofillPickerSuggestionsTest` 6 项、`BitwardenLikeAutofillMatcherNgTest` 12 项、`AutofillRequestContextPolicyTest` 3 项，共 21 项通过，无失败或跳过。
- 两版设备用例中，`issue167PickerRecommendsBarePackageStoredAsWebsite` 与 `issue167PickerRecommendsBoundAppWithoutPrecomputedIds` 均通过。前者使用反馈中的真实包名打开选择页；后者使用合成应用绑定。它们不覆盖天翼云盘的真实系统请求。
- 两版的 `systemAutofillFillsBarePackageWebsiteBindingInLargeVault` 均通过，在合成表单与 300 条无关记录下，经 Android 系统实际选择并填写账号／密码。两版各 3 项设备用例通过，无跳过。

## 待确认的现场信息

- 手机上的天翼云盘版本、Monica 构建版本。
- 自动填充页搜索“天翼”是否能找到两条凭据；切到“全部来源”是否恢复。
- 两条凭据的存储来源。当前“仅本地”按 Monica 本地所有权筛选，会排除 MDBX、KeePass 等条目；截图无法确认凭据归属，不能据此判定根因。
- 一次真实触发后的 Monica 自动填充诊断日志，重点查看 `Autofill target diagnostics`、`Picker source diagnostics` 与 `Picker data load complete`，以区分请求范围、来源筛选与推荐结果。

后续应先建立能复现现场的反馈回路，再修改匹配规则。不要以放宽无域名 WebView／浏览器匹配或忽略服务提供的字段作用域作为未经验证的修复。
