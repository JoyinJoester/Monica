# Monica Browser Extension

一个强大、安全的浏览器密码管理器扩展，支持自动填充、2FA 验证器、加密笔记、Quick Actions 等功能。

## ✨ 功能特性

### 🔐 密码管理
- **自动检测登录表单** - 智能识别用户名和密码字段
- **一键自动填充** - 点击输入框旁边的图标，快速填充凭据
- **智能匹配** - 基于当前网站域名自动匹配密码
- **密码保存提示** - 登录后自动提示保存新密码

### 🔢 2FA/TOTP 验证器
- **自动识别验证码输入框** - 智能检测 2FA/OTP 字段
- **一键填充验证码** - 点击图标选择并填充验证码
- **实时验证码生成** - 30秒自动更新验证码
- **支持多账户** - 管理多个 TOTP 账户

### 📝 其他功能
- **加密笔记** - 安全存储敏感信息
- **文档管理** - 管理身份证、银行卡等证件信息
- **WebDAV 备份** - 支持云备份到坚果云、Nextcloud 等
- **密码导入/导出** - 支持从其他密码管理器导入
- **多语言支持** - 支持中文、英文等语言
- **Quick Actions（快速操作）** - 提供便捷的密码管理快捷操作，包括快速查看、复制、编辑、删除和搜索功能

## 📋 技术栈

Monica Browser 扩展使用现代化的前端技术栈：
- **框架**：React + Vite
- **状态管理**：React Context (MasterPasswordContext)
- **样式**：Styled Components
- **图标**：Lucide Icons
- **构建工具**：Vite + TypeScript
- **国际化**：i18next (支持中文、英文、日文、越南语)

## 🔒 安全架构

### 加密
- **AES-256-GCM** - 使用军用级加密算法
- **PBKDF2** - 密钥派生函数，增加暴力破解难度
- **零知识架构** - 主密码仅本地存储，不上传服务器

### 隐私保护
- **本地存储** - 所有数据存储在浏览器本地
- **开源** - 代码完全开源，可审计
- **无追踪** - 不收集任何用户数据

## 📦 安装方法

### 方法 1：开发者模式安装（推荐用于开发）

1. **克隆或下载源码**
   ```bash
   git clone https://github.com/aiguozhi123456/Monica.git
   cd "Monica-main/Monica for Browser"
   ```

2. **安装依赖**
   ```bash
   npm install
   ```

3. **构建项目**
   ```bash
   npm run build
   ```

4. **加载扩展**
   - Chrome/Edge: 打开 `chrome://extensions/`
   - Firefox: 打开 `about:debugging#/runtime/`

5. **启用开发者模式**
   - Chrome/Edge: 点击右上角"开发者模式"开关
   - Firefox: 点击"临时载入附加组件"

6. **加载已解压的扩展**
   - Chrome/Edge: 点击"加载已解压的扩展程序"，选择 `dist` 文件夹
   - Firefox: 选择 `dist/manifest.json` 文件

### 方法 2：使用已经编译好的压缩包[release](https://github.com/aiguozhi123456/Monica/releases)

1. **下载并解压**
   - 下载最新的压缩包，解压至合适的位置

2. **加载扩展**
   - Chrome/Edge: 打开 `chrome://extensions/`
   - Firefox: 打开 `about:debugging#/runtime/`

3. **启用开发者模式**
   - Chrome/Edge: 点击右上角"开发者模式"开关
   - Firefox: 点击"临时载入附加组件"

4. **加载已解压的扩展**
   - Chrome/Edge: 点击"加载已解压的扩展程序"，选择 `dist` 文件夹
   - Firefox: 选择 `dist/manifest.json` 文件

### 方法 3：从 Chrome Web Store 安装（尚未发布）

1. 访问 [Chrome Web Store](https://chrome.google.com/webstore)
2. 搜索 "Monica Password Manager"
3. 点击"添加至 Chrome"按钮

## 🚀 快速开始

### 1. 首次使用

1. **设置主密码**
   - 点击浏览器工具栏的 Monica 图标
   - 输入并确认主密码（请牢记此密码！）
   - 点击"创建"按钮

2. **添加第一个密码**
   - 点击页面底部的 "+" 按钮
   - 填写以下信息：
     - **标题**：密码名称（如"微信"）
     - **用户名**：账号
     - **密码**：密码
     - **网站**：网站地址（如 `weixin.qq.com`）
   - 点击"保存"

3. **使用自动填充**
   - **填充已保存的密码**
     - 访问需要登录的网站（如 `github.com/login`）
     - 点击用户名或密码输入框
     - 你会看到输入框旁边出现 Monica 图标
     - 点击图标，从弹出的列表中选择密码
     - 用户名和密码会自动填充

   **使用 2FA 验证码**
     - 访问需要 2FA 验证的网站
     - 在验证码输入框中，点击旁边的 Monica 图标
     - 输入主密码进行验证（如果之前已验证，会直接显示列表）
     - 从列表中选择对应的验证器
     - 验证码会自动填充到输入框

4. **保存新密码**
   - 在网站上输入新的用户名和密码
   - 点击登录按钮
   - 页面右上角会弹出 Monica 保存提示
   - 点击"保存"按钮
   - 密码已保存到 Monica Vault

## 📱 使用指南

### 密码管理

#### 查看密码列表
- 扩展主页面默认显示所有密码
- 点击密码项目或卡片进入详情页面
- 支持按标题、用户名、网站搜索
- 使用 Quick Actions 可以快速查看、复制和删除密码

#### 编辑密码
- 点击密码卡片进入详情页
- 修改任意字段
- 点击"保存"按钮

#### 删除密码
- 在密码详情页点击"删除"按钮
- 确认删除操作

### 2FA 验证器管理

#### 添加验证器
1. 点击导航栏的"验证器"选项卡
2. 点击"+"按钮
3. 填写信息：
   - **标题**：验证器名称（如"GitHub"）
   - **发行者**：服务提供商（如"GitHub"）
   - **账号**：账户名/邮箱
   - **密钥**：扫描 QR 码或手动输入 Base32 密钥
   - **算法**：默认 SHA-1，可选 SHA-256
   - **位数**：默认 6 位，可选 8 位
   - **周期**：默认 30 秒
   - 点击"保存"

#### 扫描 QR 码
- 点击"扫描 QR 码"按钮
- 允许摄像头权限
- 对准 QR 码进行扫描

### 备份和恢复

#### WebDAV 备份
1. 点击导航栏的"备份"选项卡
2. 点击"备份设置"
3. 配置 WebDAV 服务器信息：
   - **服务器地址**：如 `https://dav.jianguoyun.com`
   - **用户名**：你的账号
   - **密码**：你的密码
   - **路径**：备份路径（如 `/MonicaBackup`）
4. 点击"测试连接"
5. 连接成功后，返回备份页面点击"立即备份"

### 导入功能

Monica Browser 扩展支持从其他密码管理器导入数据，确保数据迁移的便利性。

#### 支持的导入格式

**1. Chrome Password CSV**
- **适用场景**：从 Chrome 浏览器导出的密码文件
- **数据字段**：name, url, username, password, note

**2. Monica CSV**
- **适用场景**：Monica 实例之间的数据交换
- **数据字段**：完整的 Monica 导出格式，包含所有类型（密码、笔记、文档、TOTP 等）

**3. Aegis JSON**
- **适用场景**：从 Aegis 验证器应用导出的 TOTP 数据
- **数据字段**：TOTP 相关信息，包含 issuer、account、secret、algorithm、period、digits 等

**4. KeePass (.kdbx)** ⚠️ 暂不支持
- **适用场景**：从 KeePass 导出的密码数据库
- **状态**：功能开发中，暂不支持导入
- **替代方案**：可以使用 KeePass 的 CSV 导出功能，然后使用 Monica 的 CSV 导入

#### 导入流程

1. 选择要导入的文件（支持 .csv 和 .json）
2. 格式检测：系统自动检测文件格式类型
3. 数据解析：解析文件内容并转换为 Monica 内部格式
4. 预览确认：浏览即将导入的内容列表
5. 开始导入：执行导入操作，显示进度
6. 结果报告：显示导入统计（总数、成功数、跳过数、错误数）

#### 特性

- **自动格式检测**：根据文件扩展名或内容自动识别格式类型
- **去重处理**：自动跳过重复的条目
- **错误处理**：友好的错误提示，帮助用户快速定位和解决问题
- **进度显示**：实时显示导入进度和结果

### 设置

#### 通用设置
- **语言**：选择界面语言
- **主题**：浅色/深色主题
- **自动锁定**：设置超时时间后自动锁定

#### 自动填充设置
- **启用自动填充**：开启/关闭自动填充功能
- **保存新密码**：登录时自动提示保存
- **2FA 自动填充**：开启/关闭 2FA 自动填充

## 🔧 开发指南

### 项目结构

```
Monica for Browser/
├── src/                    # 源代码
│   ├── components/         # React 组件
│   │   ├── auth/         # 认证相关
│   │   │   └── UnlockScreen.tsx    # 主密码解锁界面
│   │   ├── common/       # 通用组件
│   │   │   ├── Button.tsx           # 按钮组件
│   │   │   ├── Card.tsx              # 卡片容器
│   │   │   ├── Input.tsx            # 输入框
│   │   │   └── layout/       # 布局组件
│   │   │       └── Layout.tsx            # 主布局
│   ├── features/          # 功能模块
│   │   ├── passwords/    # 密码管理
│   │   ├── notes/        # 笔记管理
│   │   ├── documents/    # 文档管理
│   │   ├── authenticator/ # 2FA 验证器
│   │   ├── backup/      # 备份功能
│   │   │   ├── BackupPage.tsx         # 备份主页
│   │   │   ├── WebDavSettings.tsx      # WebDAV 设置
│   │   │   └── index.ts              # 备份入口文件
│   │   ├── settings/    # 设置
│   │   ├── quickAction/  # 快速操作
│   │   │   └── QuickActionPage.tsx    # 快速操作页面
│   │   └── import/      # 导入功能
│   │       ├── ImportPage.tsx         # 导入页面
│   │       └── index.ts              # 导入入口文件
│   ├── contexts/         # React Context
│   │   └── MasterPasswordContext.tsx    # 主密码上下文
│   ├── theme/            # 主题和样式
│   ├── utils/            # 工具函数
│   │   ├── webdav/       # WebDAV 相关
│   │   │   ├── WebDavClient.tsx          # WebDAV 客户端
│   │   │   ├── EncryptionHelper.tsx     # 加密助手
│   │   │   ├── BackupManager.tsx       # 备份管理器
│   │   │   ├── storage.ts               # 存储抽象层
│   │   ├── ImportManager.tsx         # 导入管理器
│   ├── types/            # TypeScript 类型
│   ├── App.tsx          # 主应用
│   ├── background.ts     # Background Service Worker
│   ├── content.ts       # Content Script
│   ├── i18n.ts         # 国际化配置
│   ├── index.css         # 全局样式
│   ├── index.html        # HTML 入口
├── public/              # 静态资源
│   ├── icons/           # 应用图标
│   └── manifest.json    # 扩展清单
├── package.json         # 依赖配置
├── tsconfig.json       # TypeScript 配置
└── vite.config.ts      # Vite 配置
```

### 本地开发

1. **安装依赖**
   ```bash
   npm install
   ```

2. **启动开发服务器**
   ```bash
   npm run dev
   ```

3. **加载扩展**
   - 打开 `chrome://extensions/`
   - 启用开发者模式
   - 点击"重新加载"按钮（如果在开发时已加载）

4. **修改代码后**
   - 代码会自动重新编译
   - 在扩展管理页面点击"重新加载"按钮

### 构建

```bash
# 生产构建
npm run build

# 预览构建结果
npm run preview
```

## 🔒 安全特性

### 加密
- **AES-256-GCM** - 使用军用级加密算法
- **PBKDF2** - 密钥派生函数，增加暴力破解难度
- **零知识架构** - 主密码仅本地存储，不上传服务器

### 隐私
- **本地存储** - 所有数据存储在浏览器本地
- **开源** - 代码完全开源，可审计
- **无追踪** - 不收集任何用户数据

## 🐛 故障排查

### 自动填充不工作

1. **检查扩展是否启用**
   - 打开 `chrome://extensions/`
   - 确认 Monica 扩展已启用

2. **检查网站权限**
   - 点击扩展图标
   - 如果提示需要权限，点击"允许"

3. **检查密码是否已保存**
   - 打开扩展主页面
   - 确认该网站的密码已保存

4. **刷新页面**
   - 按 F5 或 Ctrl+R 刷新页面

### 2FA 不显示

1. **确认已添加验证器**
   - 打开扩展
   - 进入"验证器"选项卡
   - 确认验证器已添加

2. **检查输入框类型**
   - 确认输入框是数字类型（6位）
   - 某些网站可能需要手动点击输入框

3. **刷新页面**
   - 按 F5 或 Ctrl+R 刷新页面

### 备份失败

1. **检查 WebDAV 配置**
   - 确认服务器地址、用户名、密码正确
   - 点击"测试连接"

2. **检查网络连接**
   - 确认设备已连接互联网
   - 尝试访问 WebDAV 服务器

3. **查看控制台日志**
   - 右键点击扩展图标
   - 选择"检查"
   - 查看错误信息

## 📄 许可证

[GPL-3.0](../../LICENSE)

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

**Monica Browser Extension** - 让密码管理更简单、更安全！
