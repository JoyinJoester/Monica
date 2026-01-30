# Monica Android 本地存储与加密技术文档

## 1. 概述
Monica Android 采用多层加密架构，确保用户的敏感信息（如密码、信用卡号、证件信息等）在本地存储和备份过程中的绝对安全。系统核心遵循“零知识保护”原则，即使设备丢失或数据库文件被提取，攻击者在没有主密码的情况下也无法获取明文数据。

## 2. 本地数据存储
Monica 使用 Android 官方推荐的 **Room Persistence Library** (基于 SQLite) 进行结构化数据存储。

*   **主要数据库文件**: `password_database.db`
*   **敏感字段处理**: 数据库中的敏感字段（如 `PasswordEntry` 的 `password` 字段）**不存储明文**。在存入数据库前，必须经过加密层处理；在读取时，经过解密层恢复。
*   **存储位置**: 应用私有目录 (`/data/data/takagi.ru.monica/databases/`)，外部应用无权访问。

## 3. 核心加密技术
### 3.1 加密算法
*   **主要算法**: **AES-256-GCM** (Advanced Encryption Standard with Galois/Counter Mode)。
*   **优势**: GCM 模式不仅提供保密性（加密），还提供完整性校验（认证加密，AEAD）。它能防止加密数据被恶意篡改（比特翻转攻击）。
*   **密钥长度**: 256 位强加密。

### 3.2 敏感数据加密流程 (SecurityManager)
对于存储在本地数据库中的单条数据：
1.  **加密**: 使用 AES-256-GCM 算法，配合 12 字节随机生成的 **IV** (Initialization Vector)。
2.  **存储格式**: 加密后的字符串通常以 `Base64(IV + CipherText)` 的形式存储在数据库中。
3.  **解密**: 从存储字符串中分离 IV，配合主密钥还原明文。

## 4. 主密码与密钥管理
### 4.1 主密码保护 (PBKDF2)
用户的 App 主密码并不直接用于加密数据，而是作为派生密钥的源：
*   **哈希算法**: **PBKDF2WithHmacSHA256**。
*   **迭代次数**: **100,000 次**（高强度迭代，显著增加暴力破解的成本）。
*   **盐值 (Salt)**: 每个用户生成 16 字节的随机盐值，防止彩虹表攻击。
*   **存储**: 主密码的哈希值存储在 **EncryptedSharedPreferences** 中，而不是普通文件。

### 4.2 密钥库安全 (Android KeyStore)
使用 Android Jetpack 提供的 **Security 组件** (EncryptedSharedPreferences & MasterKey)：
*   **硬件保护**: 加密密钥存储在 Android 硬件密钥库 (Android KeyStore) 中，通常受到 TEE (Trusted Execution Environment) 或 SE (Secure Element) 的保护。
*   **生物核验**: 支持通过生物识别（指纹/面部）授权访问主密钥，无需每次手动输入长密码。

## 5. 备份加密 (WebDAV)
在进行 WebDAV 备份时，Monica 会生成一个 ZIP 压缩包，并根据用户设置提供额外加密：
*   **算法**: AES-256-GCM。
*   **派生算法**: 同样使用 PBKDF2WithHmacSHA256 (100,000 迭代)。
*   **文件格式**: 包含固定的标识位符 (`MONICA_ENC_V1`)，以及随机生成的盐值和 IV，确保即使两个用户使用相同的备份密码，生成的备份文件也完全不同。

## 6. 总结
Monica Android 结合了 Android 系统硬件级安全特性 (KeyStore) 与行业标准加密算法 (AES-GCM, PBKDF2)，实现了银行级的本地数据保护方案。
