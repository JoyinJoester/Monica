# 安全分析功能说明

## 功能概述

在设置页面顶部右上角添加了一个盾牌图标🛡️，点击进入"安全分析"页面，自动分析并展示以下4个安全问题：

### 1. 重复使用的密码 🔄
- **问题**: 在多个账户使用相同密码会增加安全风险
- **展示**: 显示有多少个密码被重复使用，以及每个密码被使用的账户列表
- **建议**: 为每个账户使用唯一的密码

### 2. 重复的URL 🔗
- **问题**: 同一个网站有多个不同的账户条目
- **展示**: 显示有哪些URL出现了多次，以及对应的账户
- **用途**: 帮助用户整理和合并重复的账户条目

### 3. 泄露的密码 ⚠️
- **技术支持**: [Have I Been Pwned](https://haveibeenpwned.com/)
- **隐私保护**: 使用 k-Anonymity 模型，不会发送完整密码到服务器
- **工作原理**:
  1. 计算密码的 SHA-1 哈希值
  2. 只发送哈希值的前5位到API
  3. 服务器返回所有匹配前5位的哈希后缀
  4. 本地匹配完整哈希，确定密码是否泄露
- **展示**: 显示哪些密码已在数据泄露事件中出现过，以及泄露次数
- **建议**: 立即更改已泄露的密码

### 4. 未启用双重验证(2FA) 🔐
- **参考**: [2FA Directory](https://2fa.directory/cn/)
- **检测逻辑**: 
  - 内置了常见支持2FA网站的列表（Google、GitHub、微软等25+网站）
  - 分析账户URL，判断该网站是否支持2FA
  - 优先显示支持2FA但未启用的重要账户
- **展示**: 显示未启用2FA的账户，标注是否支持2FA
- **建议**: 为重要账户启用双重验证以提高安全性

## 功能特点

### 🎨 美观的UI设计
- **统计卡片**: 4个彩色卡片显示各类安全问题的数量
- **颜色编码**:
  - 🟢 绿色 = 安全（无问题）
  - 🟠 橙色 = 重复密码
  - 🔵 蓝色 = 重复URL
  - 🔴 红色 = 泄露密码
  - 🟣 紫色 = 未启用2FA

### 📊 进度显示
- 分析过程显示圆形进度条（0-100%）
- 实时更新分析进度

### 📑 Tab切换
- 4个Tab对应4类安全问题
- 可展开查看每个问题的详细信息
- 点击条目跳转到密码编辑页面

### ✨ 空状态提示
- 当某类问题为空时，显示鼓励性的消息
- 如："太好了！未发现重复密码"

## 技术实现

### 数据结构
```kotlin
data class SecurityAnalysisData(
    val duplicatePasswords: List<DuplicatePasswordGroup>,
    val duplicateUrls: List<DuplicateUrlGroup>,
    val compromisedPasswords: List<CompromisedPassword>,
    val no2FAAccounts: List<No2FAAccount>,
    val isAnalyzing: Boolean,
    val analysisProgress: Int,  // 0-100
    val error: String?
)
```

### Have I Been Pwned API
- **API地址**: `https://api.pwnedpasswords.com/range/{hash-prefix}`
- **隐私保护**: k-Anonymity模型
- **限流**: 每个请求间隔1.6秒（API建议1.5秒）
- **超时**: 连接超时10秒，读取超时10秒

### ViewModel 处理流程
1. **获取所有密码** (0%)
2. **分析重复密码** (0-25%)
3. **分析重复URL** (25-50%)
4. **检查泄露密码** (50-75%)
   - 批量检查，显示进度
   - 自动延迟避免API限流
5. **分析2FA状态** (75-100%)

### 支持2FA的网站列表
内置常见网站：
- 科技公司: Google, Microsoft, Apple, Facebook, Twitter/X
- 开发平台: GitHub, GitLab
- 云服务: Amazon, Dropbox, iCloud
- 社交媒体: Instagram, LinkedIn, Reddit, Discord
- 游戏平台: Steam, Epic, Battle.net, Riot
- 其他: PayPal, Netflix, Slack, Twitch

## 使用流程

### 用户操作
1. 打开"设置"页面
2. 点击右上角盾牌图标🛡️
3. 自动开始安全分析
4. 查看4个统计卡片
5. 切换Tab查看详细问题列表
6. 点击具体条目跳转到密码编辑页面进行修改
7. 点击右上角刷新图标重新分析

### 分析时间估算
假设有50个密码：
- 重复密码分析: <1秒
- 重复URL分析: <1秒
- 泄露检查: ~50个 × 1.6秒 = ~80秒
- 2FA分析: <1秒
- **总计**: 约1.5分钟

## 多语言支持

### 英文 (English)
- Security Analysis
- Duplicate Passwords
- Duplicate URLs
- Compromised Passwords
- No 2FA

### 中文 (简体中文)
- 安全分析
- 重复密码
- 重复URL
- 泄露密码
- 未启用2FA

## 文件清单

### 新增文件
1. `data/SecurityAnalysisData.kt` - 数据类
2. `utils/PwnedPasswordsChecker.kt` - HIBP API集成
3. `viewmodel/SecurityAnalysisViewModel.kt` - ViewModel
4. `ui/screens/SecurityAnalysisScreen.kt` - UI界面

### 修改文件
1. `navigation/Screens.kt` - 添加 SecurityAnalysis 路由
2. `MainActivity.kt` - 添加路由处理和ViewModel
3. `ui/screens/SettingsScreen.kt` - 添加盾牌图标入口
4. `res/values/strings.xml` - 英文字符串
5. `res/values-zh/strings.xml` - 中文字符串

## 隐私与安全

### 密码泄露检查
✅ **完全安全**:
- 不发送完整密码到服务器
- 只发送SHA-1哈希的前5位
- 服务器返回约500个可能的匹配
- 本地进行最终匹配
- 即使网络被监听，也无法得知原密码

### 数据处理
✅ **本地处理**:
- 所有分析都在本地进行
- 密码哈希用于分组（本地）
- 不会上传任何用户数据到第三方

### API限流
✅ **友好访问**:
- 每个请求间隔1.6秒
- 避免对API造成压力
- 连接超时保护

## 已知限制

1. **2FA检测**: 
   - 只能检测内置列表中的网站
   - 无法检测所有支持2FA的网站
   - 用户需要手动检查其他网站

2. **泄露检查速度**:
   - 大量密码需要较长时间
   - 受API限流影响
   - 建议在WiFi环境下使用

3. **网络要求**:
   - 泄露检查需要网络连接
   - 其他分析可以离线进行

## 未来改进

### 可能的功能扩展
- [ ] 缓存泄露检查结果（24小时）
- [ ] 支持用户自定义2FA网站列表
- [ ] 密码强度评分系统
- [ ] 生成安全报告PDF导出
- [ ] 定期自动分析并通知
- [ ] 集成更多安全检查（密码年龄、弱密码等）

## 使用建议

### 最佳实践
1. **定期检查**: 建议每月进行一次安全分析
2. **优先处理**: 先处理泄露密码，再处理重复密码
3. **启用2FA**: 为所有重要账户启用双重验证
4. **唯一密码**: 为每个账户使用唯一的强密码
5. **密码管理器**: 使用密码生成器创建强密码

### 安全建议
- ⚠️ 发现泄露密码立即更改
- 🔄 避免在多个账户使用相同密码
- 🔐 为重要账户启用2FA
- 💪 使用长度≥12位的强密码
- 🔄 定期更新重要账户的密码

---

**技术支持**:
- Have I Been Pwned API: https://haveibeenpwned.com/API/v3
- 2FA Directory: https://2fa.directory/

**开发日期**: 2025年10月6日
