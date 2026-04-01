## Monica Preview KSP Missing Files Analysis

### 问题现象

- GitHub Actions `Android Preview Build` 在 `Build Release APK` 步骤失败。
- 失败任务不是签名，也不是 `assembleRelease` 本身，而是 `:app:kspReleaseKotlin`。
- 日志中的关键报错：
  - `e: [ksp] [MissingType]: Element 'takagi.ru...data.PasswordDatabase' references a type that is not present`

### 根因

- Android 主工程里已经有代码引用了新的数据库实体、DAO、仓库和若干配套新文件。
- 这些文件在本地工作区存在，但没有进入 Git 历史，因此 GitHub Actions 使用的远端代码快照缺文件。
- Room/KSP 在处理 `PasswordDatabase` 时，发现引用的类型不存在，于是直接在 `kspReleaseKotlin` 阶段失败。

### 本次确认到的缺失文件集合

- `Monica for Android/app/src/main/java/takagi/ru/monica/data/PasswordPageAggregateStackEntry.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/data/PasswordPageAggregateStackDao.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/repository/PasswordPageAggregateStackRepository.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/password/PasswordAggregateStackSupport.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/password/PasswordBatchMoveMixedSupport.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/password/PasswordListMainPane.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/password/PasswordManualStackGroup.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/SettingsSearchSupport.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/util/TotpDataResolver.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/utils/LegacyMonicaZipCsvRestoreParser.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/utils/SecureItemRestoreTypeResolver.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/bitwarden/service/BitwardenHistoricalTotpRepairService.kt`
- `Monica for Android/app/src/test/java/takagi/ru/monica/util/LegacyMonicaZipCsvRestoreParserTest.kt`
- `Monica for Android/app/src/test/java/takagi/ru/monica/util/SecureItemRestoreTypeResolverTest.kt`

### 为什么本地能过、CI 会挂

- 本地工作区已经有这些文件，所以 `kspReleaseKotlin` 可以解析完整类型图。
- GitHub Actions 只看远端提交快照，不会看到本地未提交文件。
- 因此 CI 里 `PasswordDatabase` 引用了不存在的类型，触发 KSP MissingType。

### 修复原则

- 不改预览版与发行版的签名链。
- 不把问题归咎于 workflow 工具链。
- 直接补齐远端缺失的 Android 文件，保证远端代码快照自洽。
- 提交前用 `kspReleaseKotlin` 和 `assembleRelease` 重新验证。

### 注意点

- 这类问题以后优先检查“新增文件是否真的进入 Git”，不要只看已修改的引用文件。
- 数据库、Room、KSP、导航拆文件等改动尤其容易出现“引用已提交、文件未提交”的不完整状态。
- CI 出现 `MissingType` 时，先查 Git 快照完整性，再考虑 KSP/Gradle 本身。
