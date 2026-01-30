# Monica Browser Extension

一个强大、安全的浏览器密码管理器扩展，支持自动填充、2FA 验证器、加密笔记等功能。

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

### 方法 2：从 Chrome Web Store 安装（如已发布）

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

### 2. 使用自动填充

#### 填充已保存的密码

1. 访问需要登录的网站（如 `github.com/login`）
2. 点击用户名或密码输入框
3. 你会看到输入框旁边出现 Monica 图标
4. 点击图标，从弹出的列表中选择密码
5. 用户名和密码会自动填充

#### 使用 2FA 验证码

1. 访问需要 2FA 验证的网站
2. 在验证码输入框中，点击旁边的 Monica 图标
3. 输入主密码进行验证（如果之前已验证，会直接显示列表）
4. 从列表中选择对应的验证器
5. 验证码会自动填充到输入框

### 3. 保存新密码

1. 在网站上输入新的用户名和密码
2. 点击登录按钮
3. 页面右上角会弹出 Monica 保存提示
4. 点击"保存"按钮
5. 密码已保存到 Monica Vault

## 📱 使用指南

### 密码管理

#### 查看密码列表
- 扩展主页面默认显示所有密码
- 点击密码卡片可以查看详细信息
- 支持按标题、用户名搜索

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
4. 点击"保存"

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

#### 导入密码

1. 点击导航栏的"备份"选项卡
2. 点击"导入"
3. 选择导入格式：
   - **KeePass (.kdbx)**：从 KeePass 导入
   - **JSON**：从其他 Monica 实例导入
4. 选择文件并确认导入

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
│   │   ├── common/       # 通用组件
│   │   └── layout/       # 布局组件
│   ├── features/          # 功能模块
│   │   ├── passwords/    # 密码管理
│   │   ├── notes/        # 笔记管理
│   │   ├── documents/    # 文档管理
│   │   ├── authenticator/ # 2FA 验证器
│   │   ├── backup/      # 备份功能
│   │   ├── settings/    # 设置
│   │   └── import/      # 导入功能
│   ├── contexts/         # React Context
│   │   └── MasterPasswordContext.tsx
│   ├── theme/            # 主题和样式
│   ├── utils/            # 工具函数
│   ├── types/            # TypeScript 类型
│   ├── App.tsx          # 主应用
│   ├── background.ts     # Background Service Worker
│   └── content.ts       # Content Script
├── public/              # 静态资源
│   ├── icons/           # 图标
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

## 📞 联系我们

- **GitHub Issues**: [https://github.com/aiguozhi123456/Monica/issues](https://github.com/aiguozhi123456/Monica/issues)
- **Email**: [待添加]

## 🙏 致谢

- [React](https://react.dev/) - React 框架
- [Vite](https://vite.dev/) - 构建工具
- [Styled Components](https://styled-components.com/) - CSS-in-JS
- [Lucide Icons](https://lucide.dev/) - 图标库
- [OTPAuth](https://github.com/hectorm/otpauth) - TOTP 验证器

---

**Monica Browser Extension** - 让密码管理更简单、更安全！
