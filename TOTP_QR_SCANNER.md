# TOTP验证器 - QR码扫描功能

## 🎉 功能完成！

### ✅ 已实现的功能：

1. **QR码扫描器**
   - 实时相机预览
   - ML Kit条形码识别
   - 扫描框UI提示
   - 相机权限请求

2. **TOTP URI解析**
   - 支持标准 `otpauth://totp/` 格式
   - 自动解析密钥、发行者、账户名
   - 支持自定义参数（period、digits、algorithm）

3. **无缝集成**
   - 添加验证器页面新增扫描按钮
   - 扫描后自动填充表单
   - 支持手动输入和扫描两种方式

## 📱 使用方法：

### 方法1：扫描二维码（推荐）

1. 打开应用，切换到"验证器"标签
2. 点击右下角 **+** 按钮
3. 点击密钥输入框右侧的 **扫描图标** 📷
4. 授予相机权限（首次使用）
5. 将二维码对准扫描框
6. 自动识别并填充信息
7. 检查并补充名称等信息
8. 点击"保存"

### 方法2：手动输入

1. 打开应用，切换到"验证器"标签
2. 点击右下角 **+** 按钮
3. 手动输入：
   - 名称（必填）
   - 密钥（必填，Base32格式）
   - 发行者（可选）
   - 账户名（可选）
4. 点击"保存"

## 🧪 测试QR码

你可以使用以下测试URI生成QR码进行测试：

```
otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
```

### 在线QR码生成器：
- https://www.qr-code-generator.com/
- 将上面的URI粘贴进去，生成QR码
- 用应用扫描测试

## 🔧 技术实现：

- **相机**: CameraX
- **条形码识别**: ML Kit Barcode Scanning
- **权限管理**: Accompanist Permissions
- **URI解析**: Android Uri + 自定义解析器

## 📋 支持的QR码格式：

标准TOTP URI格式：
```
otpauth://totp/[LABEL]?secret=[SECRET]&issuer=[ISSUER]&algorithm=[ALGORITHM]&digits=[DIGITS]&period=[PERIOD]
```

参数说明：
- **LABEL**: 标签，格式为 `Issuer:AccountName` 或 `AccountName`
- **SECRET**: Base32编码的密钥（必需）
- **ISSUER**: 发行者名称（可选）
- **ALGORITHM**: 算法，支持 SHA1/SHA256/SHA512（默认SHA1）
- **DIGITS**: 验证码位数，通常6或8位（默认6）
- **PERIOD**: 时间周期，单位秒（默认30）

## 🎯 常见应用示例：

1. **Google账户**:
   ```
   otpauth://totp/Google:user@gmail.com?secret=XXXXX&issuer=Google
   ```

2. **GitHub**:
   ```
   otpauth://totp/GitHub:username?secret=XXXXX&issuer=GitHub
   ```

3. **Microsoft**:
   ```
   otpauth://totp/Microsoft:user@outlook.com?secret=XXXXX&issuer=Microsoft
   ```

## ⚠️ 注意事项：

1. **模拟器测试**: 模拟器可能无法访问相机，建议使用真机测试
2. **权限**: 首次使用需要授予相机权限
3. **光线**: 确保扫描环境光线充足
4. **距离**: 保持适当距离，让QR码完整显示在扫描框内

## 🚀 下一步功能：

- [ ] 支持从相册选择QR码图片
- [ ] 批量导入TOTP账户
- [ ] 导出备份功能
- [ ] 云同步支持
