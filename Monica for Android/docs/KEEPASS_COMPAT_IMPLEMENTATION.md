# Monica Android KeePass 兼容与去重展示实现文档

## 目标
- Monica 能正确解析本地 .kdbx，显示全部条目
- 新增/修改/删除能落盘且保持标准 KeePass 格式
- 外部 KeePass 客户端可读取与同步
- “全部数据”视图避免重复展示

## 范围与约束
- 不改动 Monica 本地数据库结构与逻辑
- 仅处理 KeePass 读写链路与展示层合并去重
- 去重仅影响“全部数据”视图，不改变源数据
- KeePass 密码编辑页与详情页沿用现有 Monica 页面，不新增或替换 UI
- 必须保证数据库完整性，写入后可被外部客户端正常打开

## 总体架构
- 数据源
  - Monica 本地库
  - KeePass 外部库（.kdbx）
- 读取链路
  - 路径解析 → 权限校验 → 解密 → 解析 → 统一模型 → 展示
- 写入链路
  - 统一模型 → 字段映射 → 序列化 → 加密 → 持久化 → 刷新

## 任务分解
1. 统一 KeePass 数据库路径解析与权限模型
2. 完善解密与解析流程，确保条目树完整加载
3. 完成统一模型与 KeePass 字段映射
4. 落实标准 KDBX 写入与落盘流程
5. 增加读写后的缓存失效与 UI 刷新
6. 实现“全部数据”视图跨源去重
7. 增加同步策略与冲突提示
8. 建立日志与诊断输出点
9. 完成端到端验证与回归测试

## 接口清单
### 数据源层
- openKeePassDatabase(path, credentials)
- readKeePassEntries()
- writeKeePassEntry(entry)
- updateKeePassEntry(entry)
- deleteKeePassEntry(entryId)
- reloadKeePassDatabase()

### 展示层
- getAllItemsMerged()
- getLocalItems()
- getKeePassItems()
- getDuplicateItems()

### 同步层
- syncKeePassOnDemand()
- observeKeePassFileChange()
- handleSyncConflict()

## 字段映射表
| Monica 字段 | KeePass 字段 | 规则 |
| --- | --- | --- |
| title | Title | 必填 |
| username | UserName | 必填 |
| password | Password | 必填 |
| url | URL | 可选 |
| notes | Notes | 可选 |
| tags | Tags | 可选 |
| createdAt | CreationTime | 保留 |
| updatedAt | LastModificationTime | 保留 |
| icon | Icon | 映射或默认 |
| customFields | CustomStringFields | 保留 |

## 去重与合并展示
- 触发范围：仅“全部数据”视图
- 去重对象：按账号卡片聚合后的多密码集合
- 匹配规则
  - 主键：Title + Username + URL
  - 次级：Title + Username 或 Title + URL
  - 备注不作为唯一判定条件
- 保留策略
  - 默认保留 Monica 卡片
  - 若 KeePass 字段更完整，可提供高级开关保留 KeePass
- 可视化支持
  - 仅 KeePass / 仅本地 / 合并显示 切换
  - 查看重复项入口

## 同步与一致性
- 写入后强制重载 KeePass 数据源
- 切换分类或主动刷新触发重新加载
- 检测外部文件变更并提示刷新
- 冲突处理
  - 同一条目同时被外部修改时提示用户选择
  - 优先保留更新版本并记录操作日志

## 数据库完整性保障
- 写入前后进行完整性校验
  - 读取校验：可解密、可解析、条目数量可用
  - 写入校验：重新打开并验证条目可读
- 采用原子写入策略
  - 先写入临时文件，再安全替换原文件
  - 失败时回滚，确保原文件可用
- 避免并发破坏
  - 写入期间锁定文件
  - 外部修改检测到冲突时暂停写入并提示

## UI 复用原则与页面约束
- 密码编辑与详情页面统一复用现有 Monica 页面
  - 编辑页：AddEditPasswordScreen（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt）
  - 详情页：PasswordDetailScreen（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/PasswordDetailScreen.kt）
- KeePass 条目的编辑/查看亦通过上述页面完成，不新增单独的 KeePass UI
- 交互差异通过数据源和筛选逻辑体现，不在 UI 层做分叉
  - 分类筛选：CategoryFilter.KeePassDatabase（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt）
  - 列表展示：PasswordListScreen、SimpleMainScreen 内的列表与移动操作（文件参考）

## 接口落地映射与现有代码位
- KDBX 导出/导入
  - 导出：KeePassWebDavViewModel.exportToKdbxStream（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/KeePassWebDavViewModel.kt）
  - 导入：KeePassWebDavViewModel.parseKdbxAndInsertToDb（同文件参考）
- 本地 KeePass 管理与写入
  - 创建/导入/复制：LocalKeePassViewModel.createDatabase、importExternalDatabase、copyToInternal（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt）
  - 添加密码到 .kdbx：LocalKeePassViewModel.addPasswordEntriesToKdbx（同文件参考）
  - 原子写入与编码：KeePassDatabase.encode/DocumentFile 输出流（同文件参考）
- 数据模型与筛选
  - 密码实体：PasswordEntry 包含 keepassDatabaseId 字段（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/data/PasswordEntry.kt）
  - 仓库筛选：PasswordRepository.getPasswordEntriesByKeePassDatabase（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/repository/PasswordRepository.kt）
  - ViewModel 筛选：PasswordViewModel.CategoryFilter.KeePassDatabase 与 passwordEntries 流（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt）
  - UI 移动到 KeePass：SimpleMainScreen 中 move 到 KeePass 数据库并写入 kdbx（文件参考：Monica for Android/app/src/main/java/takagi/ru/monica/ui/SimpleMainScreen.kt）

## 执行步骤（落地计划）
- 阶段一：读写链路与权限核对
  - 检查外部 URI 持久化权限与路径一致性
  - 统一 Credentials 构造与 KDBX 版本
- 阶段二：字段映射与序列化一致性
  - 对齐 Title/UserName/Password/URL/Notes 等字段
  - 保留时间戳与 UUID，兼容其他客户端
- 阶段三：原子写入与并发控制
  - 临时文件写入 + 成功后替换
  - 文件级锁与冲突提示
- 阶段四：展示合并与去重
  - “全部数据”视图按主键合并去重
  - 提供“仅本地/仅 KeePass/合并显示”切换
- 阶段五：日志与验证
  - 打点读写、合并、同步各环节
  - 外部客户端读取验证与回归测试

## 日志点位
### 读取链路
- 解析路径与权限结果
- 解密参数与版本信息
- 条目数量与分组数量
- 解析失败原因与异常栈

### 写入链路
- 写入前后文件大小与时间戳
- 序列化耗时与加密耗时
- 写入失败原因与异常栈

### 去重展示
- 合并前后条目数量
- 去重命中数量与匹配规则命中统计

### 同步与冲突
- 文件变更检测次数
- 冲突条目数量与处理结果

## 验证计划
1. 读取验证：切换 KeePass 后展示全部条目
2. 写入验证：新增条目后外部客户端可见
3. 修改/删除一致性：外部客户端同步一致
4. 完整性验证：外部客户端可正常打开无错误
5. 回归测试：不影响 Monica 本地库功能
