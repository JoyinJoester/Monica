// 简单的测试：使用官方 Bitwarden 的测试向量验证加密实现
// 这是一个草稿，用于理解问题

import android.util.Base64
import java.nio.charset.StandardCharsets

// 测试数据
val testPassword = "test"
val testEmail = "test@test.com"
val expectedMasterKeyBase64 = "已知值"

// 派生 Master Key
val passwordBytes = testPassword.toByteArray(StandardCharsets.UTF_8)
val saltBytes = testEmail.lowercase().toByteArray(StandardCharsets.UTF_8)

// PBKDF2-SHA256
