package takagi.ru.monica.util

import org.junit.Test
import org.junit.Assert.*
import com.lambdaworks.crypto.SCrypt

class AegisDecryptorComprehensiveTest {

    @Test
    fun testScryptKeyDerivation() {
        // 测试scrypt密钥派生是否正确
        val password = "testpassword"
        val salt = "1c067320a3efbbab13b7fed54c883ae07755ef279f66d6fa5644d0df716046da"
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        try {
            val derivedKey = SCrypt.scrypt(
                password.toByteArray(Charsets.UTF_8),
                saltBytes,
                32768, // N
                8,     // r
                1,     // p
                32     // key length
            )
            
            // 验证派生密钥长度
            assertEquals(32, derivedKey.size)
        } catch (e: Exception) {
            fail("SCrypt密钥派生失败: ${e.message}")
        }
    }
    
    @Test
    fun testHexToByteArrayConversion() {
        // 创建一个AegisDecryptor实例来测试私有方法
        val decryptor = AegisDecryptor()
        
        // 通过反射访问私有方法
        val method = AegisDecryptor::class.java.getDeclaredMethod("hexStringToByteArray", String::class.java)
        method.isAccessible = true
        
        // 测试十六进制到字节数组的转换
        val hexString = "48656c6c6f" // "Hello"的十六进制表示
        try {
            val result = method.invoke(decryptor, hexString) as ByteArray
            val expected = "Hello".toByteArray()
            assertArrayEquals(expected, result)
        } catch (e: Exception) {
            fail("十六进制转换失败: ${e.message}")
        }
    }
}