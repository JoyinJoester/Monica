# MDBX 1.0 Android 接入验收

本文记录 Monica for Android 接入 MDBX 1.0 的当前边界、自动化证据和必须真机验证的项目。

## 发布边界

- MDBX 1.0 是 Android 端正式 MDBX 版本。
- 新建 MDBX 数据库必须写入 `format_version = MDBX-1`，并通过 `release_label = MDBX-1.0` 标记正式版本。
- 旧测试版 MDBX 数据库必须保持可打开。Android 只做非破坏性准备：补充 1.0 元数据、补充缺失的凭据材料，并保留旧数据可读。
- Sky 模式保持低使用成本：默认不强制硬件密钥，不增加额外解锁提示；key file 和更严格 TIGA 选择只能是用户主动选择。
- MDBX 逻辑必须集中在 `MdbxRepository` / `MdbxVaultStore` 边界内，UI 和 ViewModel 不直接写 MDBX 表结构。

## 当前自动化证据

已通过：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests takagi.ru.monica.repository.MdbxAndroidIntegrationGuardTest
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebugAndroidTest
git diff --check -- "Monica for Android/app/src/main/java/takagi/ru/monica" "Monica for Android/app/src/main/res" "Monica for Android/app/src/test/java/takagi/ru/monica/repository/MdbxAndroidIntegrationGuardTest.kt"
```

已在 `Pixel_Fold_API_35` 模拟器上通过：

```powershell
& 'D:\AndroidSDK\platform-tools\adb.exe' -s emulator-5554 shell am instrument -w -r -e class takagi.ru.monica.repository.MdbxVaultStoreInstrumentedCompatibilityTest takagi.ru.monica.test/androidx.test.runner.AndroidJUnitRunner
```

结果：`OK (4 tests)`。

说明：默认 PATH 中的 `C:\Windows\System32\adb.exe` 无法启动 daemon；设备验收应优先使用 `D:\AndroidSDK\platform-tools\adb.exe`。`connectedDebugAndroidTest` 当前在 UTP 安装阶段报 `List of APKs is empty`，但手动安装 debug APK 与 androidTest APK 后，instrumentation 测试本身已通过。

`MdbxAndroidIntegrationGuardTest` 覆盖：

- 新建库 1.0 元数据和旧测试版格式兼容常量。
- 本地、WebDAV、OneDrive 旧库打开路径都执行验证、1.0 准备、flush、导入。
- `project_tags`、项目搜索、sync bundle tags 导出/导入都留在 `MdbxVaultStore` 边界内。
- 旧 sync bundle 没有 `project_tags` 时保留本地标签。
- 诊断、pending sync、tags/search、delta/history、snapshot、sync bundle、conflict、attachment、flush 等高级能力都由 `MdbxRepository` 声明，并由 `MdbxVaultStore` override 实现。
- 旧测试版最小 schema 准备路径使用真实 SQLite instrumentation 测试锁住：`MdbxVaultStoreInstrumentedCompatibilityTest` 构造 `MDBX-1-DRAFT` 样本，验证准备后补齐 1.0 元数据和凭据材料；该测试已在模拟器实际运行通过。
- 真实 SQLite instrumentation 覆盖新建 MDBX 1.0 后的核心 facade 链路：创建 folder，写入 Password/TOTP/Passkey 并保留 `mdbxFolderId`，写入外部附件引用，设置 tags，按标题和大小写不敏感 tag 搜索，导出/导入 sync bundle，创建/预览 snapshot，读取 diagnostics，删除附件。
- 真实 SQLite instrumentation 覆盖安全回滚和冲突链路：坏 sync bundle hash 被拒绝且本地 entry 不变化；同一 entry 的分叉 bundle 导入后产生 unresolved conflict；`LOCAL_WINS` resolve 后本地 winner 保留。
- Password、TOTP、Note、Document、Card、Passkey 移动/绑定到 MDBX folder 时保留 `mdbxFolderId`，并覆盖写入 payload、project/object_index folder 归属、导入回 Room 的 round-trip，避免只保存 databaseId 后掉回 vault root。
- App 可见文案使用 MDBX 1.0，不再显示测试版入口。

## 真机验收矩阵

这些项目不能只靠 JVM 测试证明，必须在模拟器或真机上验证。

| 场景 | 步骤 | 通过标准 |
| --- | --- | --- |
| Instrumentation 核心链路 | 在设备/模拟器运行 `MdbxVaultStoreInstrumentedCompatibilityTest` | 旧库兼容、新 Sky 创建、核心 facade 写读、坏包回滚、冲突检测/解决全部通过 |
| 新建本地 MDBX | 创建本地 MDBX，进入详情/诊断 | 显示 `MDBX-1.0 · MDBX-1`，默认不要求 key file |
| 打开旧测试版本地 MDBX | 使用旧 `.mdbx` 样本打开 | 不要求额外迁移提示，数据可读，诊断显示兼容状态 |
| WebDAV 新建/打开 | 创建并重新打开远程 `.mdbx` | 本地 working copy flush 后，远端文件可被再次打开 |
| OneDrive 新建/打开 | 创建并重新打开远程 `.mdbx` | 远端路径、文件名和本地记录一致，重新进入可读 |
| 密码移动/复制 | 将密码移动和复制到 MDBX root/folder | Room 行和 MDBX 项目都存在，folder 过滤正确 |
| TOTP/Note/Document/Card 移动 | 分别移动到 MDBX root/folder | 对应 SecureItem 写入 MDBX，不丢 folderId |
| Passkey 移动 | 移动到 MDBX root/folder | `mdbxDatabaseId` 和 `mdbxFolderId` 都保留，私钥仍可用 |
| 附件 | 给 MDBX 项添加/删除附件 | 附件可读，删除后 MDBX attachment_count 正确 |
| Tags/Search | 设置项目 tags 并搜索 | tags 可列出，搜索按标题和 tag 命中 |
| Sync bundle | 导出后导入到另一份库 | commits/object_versions/project_tags 正确应用 |
| 旧 bundle 兼容 | 导入不含 `project_tags` 的旧 bundle | 本地已有 tags 不被清空 |
| 冲突 | 两份库修改同一项目后合并 | 冲突进入 unresolved，可选择 local/incoming/custom |
| 快照/恢复 | 创建快照、预览、恢复 | 结构预览正确，恢复后项目/条目状态一致 |
| 失败回滚 | 用副本模拟准备/导入失败 | 原始库仍可打开，未留下半写元数据 |

## 不允许回退

- 不允许把 `release_label = MDBX-1.0` 换成破坏旧读取器的低层格式 token。
- 不允许默认要求硬件密钥或 key file。
- 不允许旧测试版 MDBX 因缺少新增表而直接拒绝最小读取。
- 不允许 sync bundle 缺少 `project_tags` 时清空本地 tags。
- 不允许 Passkey 移动到 MDBX folder 后只保存 databaseId。
- 不允许 UI 或 ViewModel 直接拼 MDBX 表写入逻辑。
