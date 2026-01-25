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
- 原子写入：写入临时文件后替换原文件
- 写入校验：写入完成后立即重新解码验证
- 版本保护：保留最近一次成功写入的备份文件
- 错误恢复：校验失败则回滚到上一次有效文件
- 权限保障：写入前检查读写权限与可用空间

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
