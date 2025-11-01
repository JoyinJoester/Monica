# WebDAV 加密备份功能说明

## 概述

Monica 现在支持使用 AES-256-GCM 算法对 WebDAV 备份进行加密。此功能可确保您的敏感数据在云端存储时得到强加密保护,同时完全兼容未加密的旧备份。

## 加密技术规格

### 加密算法
- **算法**: AES-256-GCM (Galois/Counter Mode)
- **密钥长度**: 256 位
- **认证标签**: 128 位
- **IV 长度**: 12 字节 (GCM 推荐值)

### 密钥派生
- **算法**: PBKDF2-HMAC-SHA256
- **迭代次数**: 100,000 次 (高安全性)
- **盐值长度**: 32 字节 (随机生成)

### 文件格式
加密文件结构:
```
[文件头标识: "MONICA_ENC_V1"] (13 字节)
[盐值 Salt] (32 字节)
[初始化向量 IV] (12 字节)
[加密数据 + GCM 认证标签]
```

## 功能特性

### 1. 完全兼容性
- ✅ 可以读取未加密的旧备份 (`.zip`)
- ✅ 可以读取加密的新备份 (`.enc.zip`)
- ✅ 自动检测备份文件类型
- ✅ 无需手动指定加密状态

### 2. 安全特性
- ✅ AES-256 军用级加密
- ✅ GCM 模式提供认证,防篡改
- ✅ PBKDF2 高强度密钥派生
- ✅ 每次备份使用不同的随机盐值和 IV
- ✅ 密码错误时立即检测失败

### 3. 用户体验
- ✅ 简单的开关控制
- ✅ 可选的加密密码
- ✅ 密码强度验证 (最少 8 字符)
- ✅ 密码确认防止输入错误
- ✅ 备份列表显示加密状态标识

## 使用方法

### 配置加密

```kotlin
// 在 WebDAV 配置界面启用加密
val webDavHelper = WebDavHelper(context)

// 启用加密并设置密码
webDavHelper.configureEncryption(
    enable = true,
    encPassword = "your_strong_password"
)

// 检查加密状态
if (webDavHelper.isEncryptionEnabled()) {
    // 加密已启用
}
```

### 创建加密备份

```kotlin
// 创建并上传加密备份
val result = webDavHelper.createAndUploadBackup(
    passwords = passwordList,
    secureItems = secureItemList
)

result.onSuccess { fileName ->
    // 备份成功,文件名为: monica_backup_20241101_153045.enc.zip
    Log.d("Backup", "Encrypted backup created: $fileName")
}
```

### 恢复加密备份

```kotlin
// 下载并恢复备份 (自动检测是否加密)
val result = webDavHelper.downloadAndRestoreBackup(
    backupFile = selectedBackup,
    decryptPassword = if (selectedBackup.isEncrypted()) userPassword else null
)

result.onSuccess { backupContent ->
    val passwords = backupContent.passwords
    val secureItems = backupContent.secureItems
    // 处理恢复的数据
}

result.onFailure { error ->
    // 处理错误 (可能是密码错误)
    Log.e("Restore", "Failed: ${error.message}")
}
```

### 检测文件加密状态

```kotlin
// 方法 1: 通过 BackupFile 对象
if (backupFile.isEncrypted()) {
    // 这是加密文件
    showPasswordDialog()
}

// 方法 2: 通过文件名
if (fileName.endsWith(".enc.zip")) {
    // 这是加密文件
}

// 方法 3: 通过文件内容
if (EncryptionHelper.isEncryptedFile(file)) {
    // 这是加密文件
}
```

## API 参考

### EncryptionHelper

#### `encryptFile(inputFile: File, outputFile: File, password: String): Result<File>`
加密文件

**参数:**
- `inputFile`: 明文文件
- `outputFile`: 加密后的输出文件
- `password`: 加密密码

**返回:** 成功时返回加密文件,失败时返回错误

#### `decryptFile(inputFile: File, outputFile: File, password: String): Result<File>`
解密文件

**参数:**
- `inputFile`: 加密文件
- `outputFile`: 解密后的输出文件
- `password`: 解密密码

**返回:** 成功时返回解密文件,失败时返回错误

#### `isEncryptedFile(file: File): Boolean`
检测文件是否加密

**参数:**
- `file`: 要检测的文件

**返回:** 如果文件已加密返回 true

#### `testPassword(encryptedFile: File, password: String): Boolean`
测试密码是否正确

**参数:**
- `encryptedFile`: 加密文件
- `password`: 要测试的密码

**返回:** 密码正确返回 true

### WebDavHelper (新增方法)

#### `configureEncryption(enable: Boolean, encPassword: String = "")`
配置加密设置

**参数:**
- `enable`: 是否启用加密
- `encPassword`: 加密密码 (启用时必填)

#### `isEncryptionEnabled(): Boolean`
检查是否启用加密

**返回:** 如果启用加密返回 true

#### `hasEncryptionPassword(): Boolean`
检查是否设置了加密密码

**返回:** 如果已设置密码返回 true

#### `downloadAndRestoreBackup(backupFile: BackupFile, decryptPassword: String? = null): Result<BackupContent>`
下载并恢复备份 (兼容加密和未加密)

**参数:**
- `backupFile`: 备份文件信息
- `decryptPassword`: 解密密码 (可选,加密文件需要)

**返回:** 成功时返回备份内容,失败时返回错误

## 安全建议

1. **使用强密码**: 建议使用至少 12 个字符的密码,包含大小写字母、数字和符号
2. **定期更换密码**: 建议每 3-6 个月更换一次加密密码
3. **备份密码**: 请务必记住或安全保存加密密码,丢失密码将无法恢复数据
4. **测试恢复**: 创建加密备份后,建议先测试恢复以确保密码正确
5. **离线保存**: 如果可能,建议将加密密码离线保存在安全的地方

## 性能说明

- **加密速度**: 对于 1MB 的备份文件,加密通常需要 50-100ms
- **解密速度**: 对于 1MB 的备份文件,解密通常需要 50-100ms
- **密钥派生**: 由于使用 100,000 次 PBKDF2 迭代,首次验证密码可能需要 100-200ms
- **存储开销**: 加密后的文件会增加 57 字节 (文件头 + 盐值 + IV) + 16 字节 (GCM 标签)

## 错误处理

常见错误及解决方案:

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| "加密密码不能为空" | 启用加密但未设置密码 | 设置一个强密码 |
| "密码长度至少8个字符" | 密码太短 | 使用更长的密码 |
| "密码不匹配" | 两次输入的密码不同 | 重新输入确保密码一致 |
| "解密失败: 密码错误或文件已损坏" | 密码错误或文件损坏 | 检查密码是否正确 |
| "无效的加密文件格式" | 文件不是有效的加密文件 | 确保文件完整下载 |
| "备份文件已加密,请提供解密密码" | 尝试恢复加密备份但未提供密码 | 输入正确的解密密码 |

## 迁移指南

### 从未加密备份迁移到加密备份

1. 启用加密功能并设置密码
2. 创建新的加密备份
3. 验证加密备份可以正常恢复
4. (可选) 删除旧的未加密备份

### 禁用加密

1. 关闭加密开关
2. 创建新的未加密备份
3. 旧的加密备份仍然可以使用密码恢复

## 技术细节

### 为什么选择 AES-GCM?

1. **性能**: GCM 模式可以并行化,速度快
2. **安全**: 提供加密和认证,防止篡改攻击
3. **标准**: NIST 推荐,广泛使用
4. **支持**: Android 原生支持,无需第三方库

### 为什么使用 PBKDF2?

1. **抗暴力破解**: 高迭代次数增加破解难度
2. **标准**: NIST 推荐的密钥派生函数
3. **兼容性**: 广泛支持,稳定可靠
4. **安全**: 使用随机盐值防止彩虹表攻击

## 版本历史

- **v1.0.7** (2024-11-01)
  - ✨ 新增 WebDAV 备份加密功能
  - ✨ 使用 AES-256-GCM 加密算法
  - ✨ 完全兼容未加密的旧备份
  - ✨ 支持多语言界面 (中文、英文、日文、越南语)

## 许可证

本功能遵循 Monica 应用的 GPL-3.0 许可证。

## 贡献

欢迎提交问题和改进建议! 如果您发现安全问题,请通过私密渠道报告。
