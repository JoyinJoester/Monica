# Monica WebDAV 备份格式规范

## 1. 概述
Monica 的 WebDAV 备份机制会将用户选定的数据打包成一个 ZIP 压缩包（`.zip` 或 加密的 `.enc.zip`）。该包内部仅包含 `.json` 数据文件、`.csv` 汇总文件和媒体资源文件的集合，结构清晰，便于未来的跨平台解析。

## 2. 备份包目录结构
一个标准的备份 ZIP 包解压后的目录结构如下：

```
/
├── passwords/                  # 密码条目数据
│   ├── password_1_1700000.json
│   └── password_2_1700001.json
├── notes/                      # 笔记条目数据
│   └── note_5_1700002.json
├── images/                     # 图片资源（如证件照）
│   └── image_20240101.jpg
├── trash/                      # 回收站数据
│   ├── trash_passwords.json
│   └── trash_secure_items.json
├── categories.json             # 分类信息
├── Monica_20240101_password.csv # 密码汇总(兼容用)
├── Monica_20240101_totp.csv     # TOTP汇总
├── common_account.json         # 常用账号配置
├── webdav_config.json          # WebDAV 连接配置
├── timeline_history.json       # 操作日志(时间线)
└── generated_history.json      # 密码生成器历史
```

## 3. 详细文件格式规范

### 3.1 密码条目 (`passwords/password_{id}_{timestamp}.json`)
每个密码条目单独存储为一个 JSON 文件。

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 原始数据库 ID |
| `title` | String | 标题 |
| `username` | String | 用户名 |
| `password` | String | 密码（明文或加密，取决于是否启用了备份加密） |
| `website` | String | 网址 |
| `notes` | String | 备注 |
| `isFavorite` | Boolean | 是否收藏 |
| `categoryId` | Long? | 分类 ID |
| `categoryName` | String? | 分类名称（用于跨设备恢复时重建分类） |
| `authenticatorKey` | String | 绑定的 TOTP 密钥 |
| `loginType` | String | `PASSWORD` 或 `SSO` |
| `ssoRefEntryId` | Long? | SSO 关联的父条目 ID |

**示例:**
```json
{
  "id": 1,
  "title": "Google",
  "username": "user@gmail.com",
  "loginType": "PASSWORD",
  "categoryName": "Social",
  ...
}
```

### 3.2 笔记条目 (`notes/note_{id}_{timestamp}.json`)

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `notes` | String | 笔记正文 |
| `imagePaths` | String | 关联的图片文件名列表（逗号分隔） |

### 3.3 分类信息 (`categories.json`)
存储所有分类及其排序顺序。

```json
[
  { "id": 1, "name": "Work", "sortOrder": 0 },
  { "id": 2, "name": "Social", "sortOrder": 1 }
]
```

### 3.4 回收站 (`trash/`)
*   `trash_passwords.json`: 包含所有已软删除的密码条目，结构同普通密码条目，但包含 `deletedAt` 时间戳。
*   `trash_secure_items.json`: 包含已删除的笔记、银行卡等其他项目。

### 3.5 常用账号 (`common_account.json`)
存储用户在设置中配置的用于自动填充的个人信息。

```json
{
  "email": "myemail@example.com",
  "phone": "13800138000",
  "autoFillEnabled": true
}
```

### 3.6 WebDAV 配置 (`webdav_config.json`)
为了方便迁移到新设备，此文件备份了当前的 WebDAV 连接信息。
> **注意**: 为安全起见，`encryptedPassword`（WebDAV 连接密码）本身会被二重加密（使用备份密码）。

### 3.7 其他 CSV 文件 (`*.csv`)
这些文件主要用于兼容性或人工查阅，内容是扁平化的表格数据。恢复过程主要依赖 JSON 文件。

## 4. 加密机制
如果用户在“备份设置”中启用了“备份加密”：
1.  整个 ZIP 文件的数据会被视为二进制流。
2.  使用 `EncryptionHelper` 进行 **AES-256-GCM** 加密。
3.  生成的文件扩展名为 `.enc.zip`。
4.  文件头会包含 `MONICA_ENC_V1` 标识、Salt 和 IV。

## 5. 跨设备恢复逻辑
恢复时，App 会解压 ZIP 包 -> 读取 JSON -> 插入数据库。
*   **ID 冲突处理**: 如果原 ID 已存在，会生成新 ID。
*   **关联修复**: 自动扫描并修复 `ssoRefEntryId` 和 TOTP 绑定关系（将旧 ID 映射为新生成的 ID）。
*   **图片恢复**: 自动将 `images/` 目录下的图片复制到应用私有目录。
