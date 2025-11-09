# 自动保存设备兼容性需求文档

## 简介

本文档定义了 Monica 密码管理器自动保存功能的需求，特别关注在不同 Android 设备上的兼容性问题。当前问题是自动保存功能在某些设备上无法正常工作，但在原生虚拟机上运行正常。

## 术语表

- **AutofillService**: Android 系统提供的自动填充服务基类
- **SaveInfo**: 定义哪些字段需要保存的配置对象
- **SaveRequest**: 系统在用户提交表单时发送的保存请求
- **FillResponse**: 自动填充服务返回给系统的响应对象
- **AssistStructure**: 包含应用界面结构信息的对象
- **ROM**: 设备制造商定制的 Android 系统版本（如 MIUI、ColorOS 等）

## 需求

### 需求 1: 实现基础自动保存功能

**用户故事**: 作为密码管理器用户，我希望在登录新账户时系统能自动提示保存密码，这样我就不需要手动添加密码条目。

#### 验收标准

1. WHEN 用户在任何应用中输入用户名和密码并提交表单，THE AutofillService SHALL 接收到保存请求
2. WHEN AutofillService 接收到保存请求，THE AutofillService SHALL 解析表单数据并提取用户名和密码字段
3. WHEN 表单数据解析成功，THE AutofillService SHALL 显示保存确认对话框给用户
4. WHEN 用户确认保存，THE AutofillService SHALL 将密码条目保存到数据库中
5. WHEN 保存操作完成，THE AutofillService SHALL 记录成功日志并通知用户

### 需求 2: 在 FillResponse 中添加 SaveInfo

**用户故事**: 作为系统开发者，我需要在填充响应中包含保存信息，这样系统才知道哪些字段需要在提交时保存。

#### 验收标准

1. WHEN AutofillService 构建 FillResponse 对象，THE AutofillService SHALL 检测表单中的用户名和密码字段
2. WHEN 检测到凭据字段，THE AutofillService SHALL 创建 SaveInfo 对象并指定需要保存的字段 ID
3. WHEN 创建 SaveInfo 对象，THE AutofillService SHALL 设置保存类型为 SAVE_DATA_TYPE_USERNAME 或 SAVE_DATA_TYPE_PASSWORD
4. WHEN SaveInfo 对象创建完成，THE AutofillService SHALL 将其添加到 FillResponse 中
5. WHEN 用户偏好设置禁用自动保存，THE AutofillService SHALL 不添加 SaveInfo 到 FillResponse

### 需求 3: 设备特定兼容性处理

**用户故事**: 作为使用国产 ROM 设备的用户，我希望自动保存功能能够正常工作，即使我的设备制造商对系统进行了定制。

#### 验收标准

1. WHEN AutofillService 在小米设备（MIUI/HyperOS）上运行，THE AutofillService SHALL 使用适配的 SaveInfo 标志配置
2. WHEN AutofillService 在华为设备（EMUI/HarmonyOS）上运行，THE AutofillService SHALL 检测系统版本并应用兼容性修复
3. WHEN AutofillService 在 OPPO/Vivo 设备上运行，THE AutofillService SHALL 使用更宽松的字段匹配策略
4. WHEN AutofillService 在原生 Android 设备上运行，THE AutofillService SHALL 使用标准的 SaveInfo 配置
5. WHEN 设备 ROM 类型无法识别，THE AutofillService SHALL 使用保守的兼容性配置

### 需求 4: SaveInfo 标志优化

**用户故事**: 作为系统开发者，我需要根据不同设备特性配置 SaveInfo 标志，这样可以提高保存功能的成功率。

#### 验收标准

1. WHEN 设备支持延迟保存提示，THE AutofillService SHALL 设置 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE 标志
2. WHEN 设备不支持延迟保存，THE AutofillService SHALL 不设置 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE 标志
3. WHEN 表单包含可选字段，THE AutofillService SHALL 设置 FLAG_DONT_SAVE_ON_FINISH 标志以允许用户编辑
4. WHEN 需要在保存前验证数据，THE AutofillService SHALL 使用自定义保存 UI
5. WHEN SaveInfo 标志配置完成，THE AutofillService SHALL 记录配置详情到诊断日志

### 需求 5: 保存请求处理

**用户故事**: 作为密码管理器用户，我希望在保存密码时能够编辑和确认信息，这样可以确保保存的数据准确无误。

#### 验收标准

1. WHEN AutofillService 接收到 onSaveRequest 回调，THE AutofillService SHALL 提取所有已填充字段的值
2. WHEN 字段值提取完成，THE AutofillService SHALL 验证用户名和密码字段不为空
3. WHEN 数据验证通过，THE AutofillService SHALL 启动保存确认 Activity 显示提取的数据
4. WHEN 用户在确认界面修改数据，THE AutofillService SHALL 保存修改后的值
5. WHEN 用户取消保存操作，THE AutofillService SHALL 丢弃数据并记录取消事件

### 需求 6: 重复密码检测

**用户故事**: 作为密码管理器用户，我不希望保存重复的密码条目，这样可以保持密码库的整洁。

#### 验收标准

1. WHEN AutofillService 准备保存新密码，THE AutofillService SHALL 检查数据库中是否存在相同的用户名和域名组合
2. WHEN 检测到重复条目，THE AutofillService SHALL 提示用户选择更新现有条目或创建新条目
3. WHEN 用户选择更新现有条目，THE AutofillService SHALL 更新密码字段并保留其他信息
4. WHEN 用户选择创建新条目，THE AutofillService SHALL 创建独立的密码条目
5. WHEN 没有检测到重复，THE AutofillService SHALL 直接创建新的密码条目

### 需求 7: 诊断和日志记录

**用户故事**: 作为开发者，我需要详细的日志来诊断自动保存在不同设备上的问题，这样可以快速定位和修复兼容性问题。

#### 验收标准

1. WHEN AutofillService 接收到保存请求，THE AutofillService SHALL 记录设备信息（制造商、型号、ROM 类型）
2. WHEN 解析保存请求数据，THE AutofillService SHALL 记录检测到的字段类型和数量
3. WHEN 保存操作成功或失败，THE AutofillService SHALL 记录结果和耗时到诊断系统
4. WHEN 发生错误，THE AutofillService SHALL 记录完整的错��堆栈和上下文信息
5. WHEN 用户查看诊断报告，THE AutofillService SHALL 显示保存功能的统计数据（成功率、失败原因等）

### 需求 8: 用户偏好设置

**用户故事**: 作为密码管理器用户，我希望能够控制自动保存功能的行为，这样可以根据个人需求定制体验。

#### 验收标准

1. WHEN 用户打开自动填充设置界面，THE AutofillService SHALL 显示"启用自动保存"开关选项
2. WHEN 用户禁用自动保存，THE AutofillService SHALL 不在 FillResponse 中添加 SaveInfo
3. WHEN 用户启用自动保存，THE AutofillService SHALL 在所有 FillResponse 中包含 SaveInfo
4. WHEN 用户修改设置，THE AutofillService SHALL 立即应用新配置无需重启应用
5. WHEN 设置保存到存储，THE AutofillService SHALL 使用 DataStore 持久化偏好设置
