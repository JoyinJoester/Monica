package takagi.ru.monica.util

import org.junit.Test
import org.junit.Assert.*

class SimpleAegisDecryptorTest {

    @Test
    fun testAegisDecryptorCreation() {
        // 测试AegisDecryptor类是否可以正确创建
        val decryptor = AegisDecryptor()
        assertNotNull(decryptor)
    }

    @Test
    fun testKeyParamsCreation() {
        // 测试KeyParams数据类是否可以正确创建
        val keyParams = AegisDecryptor.KeyParams(
            nonce = "e4494339d17c0d9f33766d8f",
            tag = "5b62b2ad4c5d88819e37fb1755bec7ab"
        )
        assertNotNull(keyParams)
        assertEquals("e4494339d17c0d9f33766d8f", keyParams.nonce)
        assertEquals("5b62b2ad4c5d88819e37fb1755bec7ab", keyParams.tag)
    }
    
    @Test
    fun testMethodNames() {
        // 测试方法名是否正确
        val decryptor = AegisDecryptor()
        assertNotNull(decryptor::class.java.getMethod("decryptMasterKey", String::class.java, String::class.java, AegisDecryptor.KeyParams::class.java, String::class.java))
        assertNotNull(decryptor::class.java.getMethod("decryptWithKey", ByteArray::class.java, AegisDecryptor.KeyParams::class.java, String::class.java))
        assertNotNull(decryptor::class.java.getMethod("decryptWithKeyBase64", ByteArray::class.java, AegisDecryptor.KeyParams::class.java, String::class.java))
    }
}