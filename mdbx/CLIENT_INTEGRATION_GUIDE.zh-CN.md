# MDBX 客户端接入指南

本文面向准备在其他客户端接入 Monica MDBX 的实现者。

目标不是重复完整 schema 规范，而是回答三个问题：

- 一个客户端怎样才算“正确接入 MDBX”。
- 用户界面必须提供哪些管理能力。
- 哪些实现捷径会破坏同步、历史、快照或跨客户端一致性。

更底层的格式规范请同时阅读：

- `../mdbx-doc/01-product-spec.zh-CN.md`
- `../mdbx-doc/02-storage-sync-spec.zh-CN.md`
- `../mdbx-doc/03-security-spec.zh-CN.md`
- `../mdbx-doc/06-sqlite-schema-v1.zh-CN.md`

## 1. 接入边界

MDBX 不是“把密码表塞进一个 SQLite 文件”。

一个合格客户端必须把 MDBX 当成完整 vault 格式处理，包括：

- vault 元信息
- unlock / key epoch
- Tiga 安全模式
- 项目、文件夹、条目、附件
- tombstone 删除链路
- commit DAG
- object version
- snapshot
- conflict
- sync state
- 诊断与维护入口

客户端可以只做“只读浏览器”，但只要支持写入，就必须维护历史、删除标记、快照和冲突元数据。

## 2. 推荐接入层级

### 2.1 L0：只读查看器

只读查看器 MAY 只实现：

- 打开 `.mdbx` 文件
- 解锁 vault
- 读取项目 / 文件夹 / 条目
- 读取附件元数据
- 显示当前 head 状态

只读查看器 MUST NOT：

- 修改 SQLite 表
- 清理 tombstone
- 生成 commit
- 伪造快照
- 自动修复冲突

只读查看器 SHOULD 显示“只读模式”，避免用户误以为修改会保存。

### 2.2 L1：基础读写客户端

基础读写客户端 MUST 实现：

- 创建 vault
- 打开 / 解锁 vault
- 新增、修改、删除条目
- 新增、修改、删除文件夹或项目容器
- 写入 tombstone
- 为每次用户级变更生成 commit
- 更新 object version
- 更新 device head / branch head
- 维护基本快照
- 刷新本地显示缓存

基础读写客户端 MUST NOT 逐条对象创建不必要的 commit。

例如用户一次批量移动 100 条密码到 MDBX，应该是一个用户级操作。实现 SHOULD 生成一个 batch commit，并在 commit 的 changed object list 中记录全部对象，而不是生成 100 个独立 commit 和 100 个自动快照。

### 2.3 L2：同步客户端

同步客户端 MUST 额外实现：

- sync state 读取和写入
- commit DAG 合并
- parent commit 校验
- 并发修改检测
- conflict 记录
- 三方合并或字段级合并
- tombstone 防复活
- 附件 chunk / external hash ref 校验
- 上传待处理写入
- 下载后重放或应用远端状态

同步客户端 MUST NOT 只按更新时间覆盖整库。

### 2.4 L3：完整 Monica 兼容客户端

完整客户端 SHOULD 实现：

- Monica 本地分类 / 快捷文件夹语义映射
- 嵌套文件夹创建、移动、复制
- 快照结构预览
- 当前版本与快照版本结构对比
- 提交历史详情
- 字段级变更展示
- 冲突合并页面
- 数据库诊断 / 维护页面
- WebDAV / OneDrive / 本地外部文件兼容
- 后台预加载当前选中 vault，但不能一次性预加载所有 vault

## 3. 推荐代码入口

当前 Rust workspace 按职责拆分：

- `crates/mdbx-core`
  - 核心领域类型。
- `crates/mdbx-crypto`
  - 加密、KDF、密钥材料处理。
- `crates/mdbx-sync`
  - 同步 payload / object payload 模型。
- `crates/mdbx-storage`
  - SQLite schema、vault 初始化、repo、搜索、快照、冲突、恢复。

客户端 SHOULD 优先通过 storage / repo API 写入，而不是直接拼 SQL。

除非正在实现底层库，否则客户端代码 SHOULD NOT 直接写这些表：

- `commits`
- `commit_parents`
- `object_versions`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `device_heads`
- `branches`

直接写这些表很容易制造“看起来保存成功，但其他客户端数量不一致、删除链路错误、历史爆炸、快照不可回滚”的问题。

## 4. 写入规则

### 4.1 用户级操作对应 commit

commit 粒度应该按“用户意图”划分，而不是按“内部对象数量”划分。

MUST 合并成单个 commit 的典型操作：

- 批量删除
- 批量移动
- 批量复制
- 批量导入
- 从 KDBX 导入一个文件夹
- 从 Monica 本地迁移一组条目
- 文件夹及其子项一起移动

MAY 拆成多个 commit 的操作：

- 用户明确分多次保存
- 长事务被用户中断后继续
- 客户端为了内存限制分批提交，并且 UI 明确显示为多批操作

### 4.2 删除必须走 tombstone

删除对象时 MUST：

- 标记对象 deleted 或移除当前可见索引
- 写入 tombstone
- 写入 commit
- 写入 object version
- 更新 device head

同步客户端 MUST 使用 tombstone 防止旧客户端或远端旧状态把已删除对象复活。

客户端 MUST NOT 只从当前列表里删掉行。

### 4.3 文件夹和路径

客户端 MUST 保留文件夹稳定 ID，而不是只依赖标题或路径字符串。

嵌套文件夹 MUST 保留 parent 关系。进入 `a/b/c` 时，面包屑或路径显示必须能恢复完整链路，而不是只显示 `a/c`。

文件夹列表展示 SHOULD：

- 文件夹排在普通项目前面。
- 同级项目保持稳定排序。
- 嵌套层级使用缩进或线条指示。
- 折叠 / 展开状态只影响 UI，不应改变存储结构。

移动、复制、新建条目时 MUST 能选择 MDBX 文件夹目标，不应只能选择数据库根目录。

### 4.4 附件

附件是 MDBX 一等对象。

客户端 MUST：

- 保留附件 ID
- 保留 attachment 与 project / entry 的归属
- 校验 content hash
- 支持 chunk 元数据
- 区分嵌入、chunk、external hash ref

客户端 MUST NOT 在修改条目标题或密码时重写无关附件内容。

### 4.5 快照

快照用于恢复和结构对比，不是普通日志。

客户端 SHOULD：

- 支持手动快照
- 支持自动快照
- 支持清理自动快照
- 支持回滚快照，并要求二次确认
- 显示快照结构预览

批量操作 SHOULD 避免生成大量自动快照。

## 5. 必备用户管理面板

其他客户端只要允许用户管理 MDBX，就 SHOULD 提供以下面板。

### 5.1 MDBX 格式管理首页

用途：按存储位置管理 vault。

必须显示：

- 本地 MDBX
- WebDAV MDBX
- OneDrive / 云端 MDBX，如客户端支持
- 每类 vault 数量
- 创建 vault
- 打开已有 vault

进入“MDBX 格式管理”时 SHOULD 先进入管理首页，而不是自动跳进上次打开的某个数据库详情页。

可以记住用户当前使用的 vault 用于密码页预加载，但管理入口本身应保持中立。

### 5.2 数据库详情页

用途：对单个 vault 做常规管理。

必须显示：

- vault 名称
- 存储路径
- 存储类型
- Tiga 模式
- 是否默认
- 同步状态
- 健康状态
- 提交数量
- 快照数量
- tombstone 数量
- 附件数量与大小

必须提供：

- 同步
- 冲突管理
- 快照
- 提交历史
- 诊断 / 维护
- 删除 vault

普通用户界面 SHOULD NOT 暴露开发者高级工具，例如 raw bundle 导入导出、benchmark、底层 chunk 调试等。它们可以保留为开发者模式或内部工具。

### 5.3 文件夹 / 结构管理页

用途：管理 vault 内部组织结构。

必须支持：

- 根目录
- 嵌套文件夹
- 创建子文件夹
- 重命名文件夹
- 移动文件夹
- 删除文件夹
- 展开 / 折叠
- 面包屑路径
- 快捷状态栏

当用户在某个 MDBX 子文件夹里新建密码时，新建页面 SHOULD 默认选中该 MDBX 数据库和当前文件夹。

### 5.4 移动 / 复制目标选择页

用途：把条目移动或复制到其他分类或 vault。

推荐交互：

1. 先选择存储类别或数据库。
2. 再选择目标文件夹。
3. 最后确认操作。

必须支持 MDBX 文件夹目标。

选择目标后 SHOULD 收起多选菜单，并用快捷状态栏或后台任务状态显示进度。不要让用户以为操作还没开始。

### 5.5 冲突管理页

用途：处理并发编辑。

必须显示：

- 冲突对象标题
- 对象类型
- 本地版本
- 远端 / incoming 版本
- 冲突字段
- 创建时间
- 相关 commit

必须支持：

- 保留本地
- 使用远端
- 字段级合并，如客户端支持
- 合并后写入新 commit

冲突展示 SHOULD 使用字段化 diff，而不是把 JSON 或 SQL 当代码块丢给用户。

### 5.6 提交历史页

用途：解释“发生了什么变更”。

必须显示：

- commit 序号或短 ID
- commit 时间
- 设备 ID
- 操作类型
- 影响对象数量
- 变更摘要

点进详情后 SHOULD 显示字段级 unified diff 风格：

```text
标题:
-   null
+   example.com

用户名:
-   old@example.com
+   new@example.com
```

注意：这里是 unified diff 的结构，不是代码视图。UI 应解析字段名和字段值，降低普通用户理解成本。

删除对象 SHOULD 显示为“删除了密码条目 / 文件夹”，不应把“删除状态 true/false”作为主要字段变更展示。

### 5.7 快照页

用途：恢复和结构检查。

必须显示：

- 手动快照
- 自动快照
- 创建时间
- 创建设备
- 基准 commit
- 完整 / 增量标识
- 清理自动快照
- 创建快照
- 回滚快照

回滚快照 MUST 二次确认。

### 5.8 快照结构预览页

用途：像文件资源管理器一样查看快照结构。

必须支持：

- 文件夹显示
- 文件夹排在普通项目前面
- 展开 / 折叠
- 嵌套层级线条
- 当前路径标题
- 快照版本节点状态

横屏或宽屏模式 SHOULD 支持当前版本与快照版本并排对比：

- 左侧：当前版本
- 右侧：快照版本
- 中间用分割线即可，不需要厚重卡片包裹

### 5.9 诊断 / 维护页

用途：给用户和支持人员判断 vault 是否健康。

必须显示关键指标：

- 是否可读
- 同步状态
- 待同步数量
- 未解决冲突数
- commit 数
- snapshot 数
- tombstone 数
- entry 数
- folder / project 数
- 附件数量与大小
- 文件路径

必须显示高级细节：

- format version
- Tiga 默认模式
- active key epoch
- branch 数
- device head 数
- dangling parent
- dangling branch head
- dangling device head
- attachment chunk mismatch
- external hash ref 数量

必须提供维护操作：

- 刷新诊断
- 同步
- 上传待处理写入
- 校验附件 chunk
- 清理自动快照

诊断页 SHOULD 简洁，低频细节放在二级区域。不要把 benchmark、raw bundle、底层 payload 全部堆到普通用户面前。

### 5.10 解锁与安全页

必须支持：

- 密码解锁
- Tiga 模式显示
- Tiga 模式选择或 vault 默认模式说明
- 错误次数 / 锁定提示，如客户端实现
- 生物识别或系统凭据包装，如平台支持

客户端 MUST 明确区分：

- 用户看到的解锁方式
- 底层实际参与加密的 key material

客户端 MUST NOT 把主密码、派生密钥、epoch key 写入日志。

## 6. 性能要求

### 6.1 启动和打开

客户端 SHOULD：

- 只预加载当前选中的 vault
- 避免同时打开所有配置过的 vault
- 对列表页使用 stale-while-revalidate 缓存
- 刷新时避免先清空列表再重新插入，造成闪空和排序跳变

如果用户管理十几个 MDBX vault，客户端 MUST NOT 启动时全部解锁、全部读历史、全部扫附件。

### 6.2 写入

客户端 SHOULD：

- 批量写入
- 单事务提交
- 单用户动作单 commit
- 写完后增量刷新 UI

客户端 SHOULD NOT：

- 每个条目单独打开 / 关闭 vault
- 每个条目单独生成快照
- 删除整张 UI cache 再重建

### 6.3 同步

同步 SHOULD 在后台执行，并通过状态栏或任务面板显示进度。

同步状态至少包括：

- 等待中
- 上传中
- 下载中
- 合并中
- 冲突待处理
- 完成
- 失败

## 7. 兼容性要求

### 7.1 format version

客户端打开 vault 时 MUST 检查 `format_version`。

遇到未知 critical extension MUST 拒绝写入，最多只读打开。

### 7.2 ID 稳定性

客户端 MUST 保留以下 ID：

- vault ID
- device ID
- branch ID
- project / folder ID
- entry ID
- attachment ID
- commit ID
- snapshot ID

客户端 MUST NOT 用标题、路径、排序号重新生成对象 ID。

### 7.3 时间和排序

客户端 SHOULD 使用 ISO-8601 UTC 时间。

列表排序 SHOULD 稳定。刷新数据时不要让同一批项目因为重新导入而随机换序。

## 8. 最低测试清单

其他客户端接入前至少应通过这些场景：

- 创建 vault，关闭后重新打开。
- 创建根目录条目。
- 创建嵌套文件夹中的条目。
- 在子文件夹里新建条目，目标仍是该 MDBX 文件夹。
- 批量移动 100 条到 MDBX，只产生一个用户级 commit。
- 批量删除 100 条，只产生一个用户级 commit，并写入 tombstone。
- 两个客户端打开同一 vault，数量一致。
- 一个客户端删除，另一个客户端同步后不会复活。
- 并发修改同一字段，产生冲突。
- 并发修改不同字段，可以自动合并或清晰提示。
- 创建手动快照。
- 清理自动快照。
- 回滚快照需要二次确认。
- 快照结构显示文件夹，并且文件夹排在条目前面。
- 附件 chunk 校验失败时能在诊断页看到。
- 打开 MDBX 格式管理首页，不自动跳进上次数据库详情页。
- 普通用户界面不暴露 raw 高级工具。

## 9. 常见错误

### 9.1 只写当前表，不写历史表

结果：

- 提交历史空白
- 快照不可用
- 冲突无法判断
- 删除可能被复活

### 9.2 每条数据一个 commit

结果：

- 批量操作后历史暴涨
- 快照暴涨
- 同步变慢
- 管理页不可读

### 9.3 文件夹只按路径字符串保存

结果：

- 重名文件夹冲突
- 移动后路径断裂
- 面包屑显示错误
- 跨客户端选择目标失败

### 9.4 管理页自动跳进上次 vault

结果：

- 用户点击“格式管理”却看不到格式管理首页
- 用户误以为只能管理一个数据库
- 多 vault 场景混乱

正确做法：

- 密码页可以记住当前 vault。
- 格式管理入口应总是进入 MDBX 管理首页。
- 数据库详情页只能由用户明确点击进入。

### 9.5 把开发工具暴露给普通用户

结果：

- 用户看到 benchmark、raw bundle、chunk payload 后无法理解。
- 容易误操作导入错误 payload。
- 管理页信息噪音过高。

正确做法：

- 普通用户只看同步、冲突、快照、历史、诊断 / 维护。
- raw bundle、benchmark、底层 chunk 调试放到开发者模式。

## 10. 接入完成标准

一个客户端可以宣称“支持 Monica MDBX”，至少必须满足：

- 可以打开 Monica 创建的 MDBX vault。
- 可以正确显示文件夹、条目、附件元数据。
- 可以在嵌套文件夹中新建、移动、复制条目。
- 可以写入 commit、object version、tombstone。
- 可以显示提交历史。
- 可以显示和回滚快照。
- 可以检测并展示冲突。
- 可以显示诊断 / 维护页面。
- 批量操作不会制造大量无意义 commit。
- 两个客户端读同一 vault 时项目数量一致。

如果只满足读取，不满足写入历史链路，应标注为“MDBX 只读支持”，不能标注为完整支持。
