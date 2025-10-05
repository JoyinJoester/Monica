# 🔐 Monica Password Manager

<div align="center">

![Monica Logo](https://img.shields.io/badge/Monica-Password%20Manager-blue?style=for-the-badge)
[![Android](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

**一个安全、简洁、功能强大的 Android 密码管理应用**

[功能特性](#-功能特性) • [安装说明](#-安装说明) • [使用指南](#-使用指南) • [技术栈](#-技术栈) • [截图展示](#-截图展示) • [贡献指南](#-贡献指南)

</div>

---

## ✨ 功能特性

### 🔒 核心功能
- **密码管理** - 安全存储和管理您的所有账号密码
- **TOTP 两步验证** - 支持生成和管理 TOTP 验证码
- **文档存储** - 安全保存重要文档和笔记
- **银行卡管理** - 加密存储银行卡信息

### 🛡️ 安全特性
- **数字密码保护** - 6位纯数字主密码,简单安全
- **数据加密** - 所有敏感数据采用 AES-256 加密
- **安全问题** - 支持通过安全问题重置密码
- **屏幕截图保护** - 可选防止截图功能
- **自动锁定** - 应用后台时自动锁定

### 📱 实用功能
- **密码生成器** - 快速生成强密码
- **密码强度检测** - 实时显示密码安全等级
- **数据导入导出** - 支持 CSV 格式导入导出
  - ✅ Chrome 密码 CSV 兼容
  - ✅ 批量导入导出
- **QR 码扫描** - 快速扫描 TOTP 二维码
- **搜索功能** - 快速查找您的密码和信息
- **分组管理** - 按类型分类管理不同数据

### 🎨 界面设计
- **Material Design 3** - 现代化的 UI 设计
- **深色模式** - 支持浅色/深色主题
- **多语言支持** - 简体中文、英语、越南语
- **响应式布局** - 适配各种屏幕尺寸

---

## 📥 安装说明

### 系统要求
- Android 8.0 (API 26) 及以上版本
- 约 20MB 存储空间

### 下载安装

#### 方式一: 从 Release 下载
1. 前往 [Releases](https://github.com/JoyinJoester/Monica/releases) 页面
2. 下载最新版本的 APK 文件
3. 在 Android 设备上安装 APK

#### 方式二: 从源码编译
```bash
# 克隆仓库
git clone https://github.com/JoyinJoester/Monica.git
cd Monica

# 使用 Gradle 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

---

## 📖 使用指南

### 首次使用

1. **设置主密码**
   - 首次打开应用,设置 6 位纯数字主密码
   - 输入两次确认密码
   - 记住您的主密码,遗忘将无法恢复数据

2. **设置安全问题** (可选)
   - 进入设置页面
   - 设置安全问题和答案
   - 用于忘记密码时重置

### 密码管理

#### 添加密码
1. 点击主页的 ➕ 按钮
2. 填写以下信息:
   - 网站名称/应用名称
   - 用户名
   - 密码 (可使用密码生成器)
   - 网站 URL (可选)
   - 备注 (可选)
3. 点击保存

#### 查看和编辑
- 点击密码条目查看详情
- 点击编辑按钮修改信息
- 长按密码可快速复制

#### 删除密码
- 在密码详情页点击菜单
- 选择"删除"选项

### TOTP 两步验证

#### 添加 TOTP
1. 切换到 TOTP 标签页
2. 点击 ➕ 按钮
3. 扫描 QR 码或手动输入密钥
4. 填写账号名称和发行方
5. 保存

#### 使用 TOTP
- TOTP 码会自动刷新
- 点击验证码可复制
- 倒计时显示剩余时间

### 文档管理

1. 切换到"文档"标签页
2. 添加文档:
   - 标题
   - 内容
   - 标签 (可选)
3. 支持富文本编辑
4. 安全加密存储

### 银行卡管理

1. 切换到"银行卡"标签页
2. 添加银行卡信息:
   - 银行名称
   - 卡号
   - 持卡人姓名
   - 有效期
   - CVV (可选)
3. 所有信息加密存储

### 数据导入导出

#### 导出数据
1. 进入设置 → 数据导出
2. 输入主密码确认
3. 选择保存位置
4. 数据以加密 CSV 格式导出

#### 导入数据
1. 进入设置 → 数据导入
2. 选择 CSV 文件
3. 支持以下格式:
   - Monica 导出的 CSV
   - Chrome 密码 CSV
4. 确认导入

### 设置选项

#### 安全设置
- **更改主密码** - 修改登录密码
- **安全问题** - 设置或修改安全问题
- **屏幕截图保护** - 防止应用内截图

#### 外观设置
- **主题** - 浅色/深色/跟随系统
- **语言** - 简体中文/English/Tiếng Việt

#### 数据管理
- **导出数据** - 备份所有数据
- **导入数据** - 恢复或导入数据
- **清除所有数据** - 删除所有数据(需确认)

---

## 🛠️ 技术栈

### 核心技术
- **Kotlin** - 主要开发语言
- **Jetpack Compose** - 现代化 UI 框架
- **Material Design 3** - UI 设计规范

### 架构组件
- **Room Database** - 本地数据持久化
- **ViewModel & LiveData** - MVVM 架构
- **Kotlin Coroutines** - 异步编程
- **Kotlin Flow** - 响应式数据流

### 安全组件
- **AES-256 加密** - 数据加密标准
- **PBKDF2** - 密码哈希算法
- **SHA-256** - 安全哈希算法

### 第三方库
- **ZXing** - QR 码扫描
- **Commons Codec** - 编解码工具
- **CameraX** - 相机功能

### 构建工具
- **Gradle 8.7** - 构建系统
- **Android Gradle Plugin 8.5.0**
- **Kotlin 1.9.0**

---

## 📱 截图展示

<div align="center">

### 登录与主界面
| 登录界面 | 密码列表 | TOTP 验证 |
|---------|---------|----------|
| ![登录](screenshots/login.png) | ![密码](screenshots/passwords.png) | ![TOTP](screenshots/totp.png) |

### 功能界面
| 添加密码 | 密码详情 | 设置页面 |
|---------|---------|----------|
| ![添加](screenshots/add.png) | ![详情](screenshots/detail.png) | ![设置](screenshots/settings.png) |

</div>

---

## 🔐 安全说明

### 数据安全
- ✅ 所有敏感数据使用 AES-256 加密
- ✅ 主密码采用 PBKDF2 加密存储
- ✅ 数据仅存储在本地设备
- ✅ 不联网,无数据泄露风险

### 密码策略
- 主密码最少 6 位数字
- 支持密码强度检测
- 密码生成器可生成强密码

### 隐私保护
- 无需网络权限
- 无广告,无追踪
- 可选屏幕截图保护
- 开源代码,公开透明

---

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议!

### 如何贡献

1. **Fork 本仓库**
2. **创建特性分支** (`git checkout -b feature/AmazingFeature`)
3. **提交更改** (`git commit -m 'Add some AmazingFeature'`)
4. **推送到分支** (`git push origin feature/AmazingFeature`)
5. **创建 Pull Request**

### 报告问题

如果您发现 Bug 或有功能建议:
1. 前往 [Issues](https://github.com/JoyinJoester/Monica/issues) 页面
2. 搜索是否已有相关问题
3. 创建新 Issue 并详细描述

### 代码规范
- 遵循 Kotlin 官方代码风格
- 添加必要的注释
- 编写单元测试

---

## 📋 开发路线图

### 已完成 ✅
- [x] 密码管理基础功能
- [x] TOTP 两步验证
- [x] 文档和银行卡管理
- [x] Chrome CSV 导入支持
- [x] 数字密码系统
- [x] 多语言支持

### 开发中 🚧
- [ ] 云端备份同步
- [ ] 指纹识别支持
- [ ] 自动填充功能
- [ ] 密码泄露检测

### 计划中 📝
- [ ] 浏览器扩展
- [ ] iOS 版本
- [ ] 桌面版本
- [ ] 家庭共享功能

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

```
MIT License

Copyright (c) 2025 JoyinJoester

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 👨‍💻 作者

**JoyinJoester**
- Email: lichaoran8@gmail.com
- GitHub: [@JoyinJoester](https://github.com/JoyinJoester)

---

## 🙏 致谢

感谢所有为 Monica 做出贡献的开发者!

特别感谢:
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化 UI 框架
- [Material Design](https://m3.material.io/) - 设计规范
- [ZXing](https://github.com/zxing/zxing) - QR 码库

---

## 📞 支持

如果您觉得这个项目有帮助,请给一个 ⭐️ Star!

有问题或建议?
- 📧 Email: lichaoran8@gmail.com
- 💬 Issues: [GitHub Issues](https://github.com/JoyinJoester/Monica/issues)

---

## 💖 支持作者

如果这个项目对您有帮助,欢迎通过以下方式支持作者的持续开发:

<div align="center">

![支持作者](support_author.jpg)

**扫码支持 • 感谢您的慷慨 ❤️**

</div>

您的支持是作者持续更新和改进的最大动力!

---

<div align="center">

**用 ❤️ 和 Kotlin 打造**

[回到顶部](#-monica-password-manager)

</div>
