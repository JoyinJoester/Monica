# Wear EncryptionHelper 实现完成

## ✅ 已完成功能

### 核心解密功能
- **AES-256-GCM 解密算法**
  - 使用与原项目相同的加密参数
  - KEY_SIZE: 256 bits
  - GCM_TAG_LENGTH: 128 bits
  - GCM_IV_LENGTH: 12 bytes

- **PBKDF2 密钥派生**
  - 算法: PBKDF2WithHmacSHA256
  - 迭代次数: 100,000 次（高强度）
  - 盐值长度: 32 bytes

### 主要方法

#### 1. `isEncryptedFile(file: File): Boolean`
检测文件是否已加密
- 通过文件扩展名检查 (`.enc.zip`)
- 通过文件头魔数检查 (`MONICA_ENC_V1`)
- 详细日志输出

#### 2. `decryptFile(inputFile, outputFile, password): Result<File>`
解密加密文件
- 验证文件头魔数
- 提取盐值、IV和加密数据
- 使用PBKDF2从密码派生密钥
- AES-GCM解密
- 错误处理：密码错误、文件损坏等

#### 3. `testPassword(encryptedFile, password): Boolean`
测试密码是否正确
- 创建临时文件进行解密测试
- 自动清理临时文件
- 返回密码是否正确

#### 4. `decryptIfNeeded(file, password): Result<File>`
智能解密（新增）
- 自动检测文件是否加密
- 如果不加密，直接返回原文件
- 如果加密，执行解密并返回解密后的文件
- 适合与WebDAV同步集成使用

## 🔧 技术细节

### 加密文件格式
```
[MAGIC]              13 bytes  "MONICA_ENC_V1"
[SALT]               32 bytes  PBKDF2盐值
[IV]                 12 bytes  GCM初始化向量
[ENCRYPTED_DATA]     N bytes   AES-GCM加密数据
```

### 安全特性
- ✅ AES-256-GCM 认证加密（防篡改）
- ✅ PBKDF2 密钥派生（防暴力破解）
- ✅ 100,000 次迭代（高强度）
- ✅ 随机盐值和IV（防彩虹表攻击）
- ✅ 文件头验证（防格式错误）

### 错误处理
- **AEADBadTagException**: 密码错误或文件损坏
- **Exception**: 通用错误（文件格式、IO错误等）
- 详细的日志记录（TAG: "WearEncryptionHelper"）

## 🔗 集成说明

### 在 WearWebDavHelper 中使用

```kotlin
// downloadAndImportLatestBackup 方法中
val downloadedFile = File(cacheDir, latestBackup.name)
// ... 下载文件 ...

// 使用 decryptIfNeeded 智能处理
val decryptResult = EncryptionHelper.decryptIfNeeded(
    downloadedFile,
    encryptionPassword
)

if (decryptResult.isFailure) {
    Log.e(TAG, "Decryption failed: ${decryptResult.exceptionOrNull()?.message}")
    return false
}

val fileToImport = decryptResult.getOrThrow()
// 继续处理解压和导入...
```

### 密码配置
在 `SettingsViewModel` 中：
```kotlin
// 配置加密密码
fun configureEncryptionPassword(password: String) {
    webDavHelper.configureEncryption(password)
}
```

在 `WearWebDavHelper` 中：
```kotlin
// 保存加密密码
fun configureEncryption(password: String) {
    prefs.edit().putString(PREF_ENCRYPTION_PASSWORD, password).apply()
}
```

## 📊 与原项目的兼容性

### ✅ 完全兼容
- 使用相同的加密参数
- 使用相同的文件格式
- 使用相同的PBKDF2参数
- 可以解密原项目加密的备份文件

### 差异说明
- **Wear版本只实现解密**，不实现加密功能
- 原项目可以加密+解密
- Wear版本只下载和导入，不上传备份

## 🧪 测试建议

### 单元测试场景
1. **正确密码解密**
   - 使用正确密码解密加密文件
   - 验证解密结果与原始文件一致

2. **错误密码处理**
   - 使用错误密码尝试解密
   - 验证返回 `AEADBadTagException`

3. **非加密文件处理**
   - `isEncryptedFile()` 返回 false
   - `decryptIfNeeded()` 直接返回原文件

4. **损坏文件处理**
   - 文件头错误
   - 文件大小不足
   - 验证返回适当错误

### 集成测试场景
1. **完整同步流程**
   - 配置WebDAV
   - 配置加密密码
   - 执行同步
   - 验证TOTP数据导入成功

2. **密码错误场景**
   - 配置错误的加密密码
   - 执行同步
   - 验证显示密码错误提示

## 📝 日志追踪

所有关键操作都有详细日志：
```
WearEncryptionHelper: File encrypted check (by header): true
WearEncryptionHelper: Starting decryption: backup.enc.zip -> decrypted.zip
WearEncryptionHelper: Read 12345 bytes from encrypted file
WearEncryptionHelper: File header verified
WearEncryptionHelper: Extracted salt (32 bytes)
WearEncryptionHelper: Extracted IV (12 bytes)
WearEncryptionHelper: Extracted encrypted data (12288 bytes)
WearEncryptionHelper: Deriving key from password...
WearEncryptionHelper: Cipher initialized
WearEncryptionHelper: Decrypted 12000 bytes
WearEncryptionHelper: File decrypted successfully: decrypted.zip (12000 bytes)
```

## ✅ 完成状态

- ✅ EncryptionHelper 创建完成
- ✅ AES-256-GCM 解密实现
- ✅ PBKDF2 密钥派生实现
- ✅ 文件格式验证
- ✅ 密码测试功能
- ✅ 智能解密功能 (`decryptIfNeeded`)
- ✅ 详细日志记录
- ✅ 错误处理
- ✅ 与原项目完全兼容

现在可以在 WebDAV 同步流程中使用加密备份了！
