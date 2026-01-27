package takagi.ru.monica.util

import org.junit.Test
import org.junit.Assert.*
import java.util.Base64

class AegisDecryptorTest {

    @Test
    fun testIsEncryptedAegisFile() {
        // 测试加密的Aegis文件
        val encryptedJson = """
        {
            "version": 1,
            "header": {
                "slots": [
                    {
                        "type": 1,
                        "uuid": "8ba4d26d-2e6e-4f18-8d87-127812e48335",
                        "key": "71b12d02fd5ac5a814ce53eb904ad0a729ab9fe15f499d2b789aca09c0bab797",
                        "key_params": {
                            "nonce": "e4494339d17c0d9f33766d8f",
                            "tag": "5b62b2ad4c5d88819e37fb1755bec7ab"
                        },
                        "n": 32768,
                        "r": 8,
                        "p": 1,
                        "salt": "1c067320a3efbbab13b7fed54c883ae07755ef279f66d6fa5644d0df716046da",
                        "repaired": true,
                        "is_backup": false
                    }
                ],
                "params": {
                    "nonce": "3b76850a6cbd48d257a2c92c",
                    "tag": "9a6105cc61139809670eb62e78e8bc53"
                }
            },
            "db": "7Ps1nGeLvuxGmu/Nk80UQj7nIAFEaDvQsD4tCsI6+EJye1zDZn7DCYmVVUbfLNau19t+C4AtnU6XMFKuhokGjFhWlJ+MISOGaR1Qadzs5lmQOCxa7Dcxlape7vFZ9Z6n4uAXCpbXqrS2e55gC8YHRgOmD5uLp8CBaQvgEf0n5SC3Ejm1rL5t/P0UYzof+EAtXYSmQ+iGGEyC3MGk+FjEsdlHFTntqhMdqw/6hNmKj3KfaIlXuAhy7HALqwWQJO7ud5KXvpM4hEtDITs6Obry9QjIAKRmrzctjIyX45AF+rgvxK5H8ppovi9kXhCcwMFa0JjVg8ZEVN/M45OjSLqZrbmvFZKoPWIXtmd8ufLeMHD3Hec/EhX4WpDUuCEPlU7XqPkHsGJ533ZQVe5duqTH7Lgn365qZJQPoqKH6m6cCzhx8RhaRvTr4w+r87/YNXl/a/Pjww2e5UB7SlOnzrKIWBq42yBLJ2deBFZrzlLFLjPNuZ0xL5Y87Rw8ylW83vpTR2h9PrpOjvRpxGbaGWL504rVo6VMKgKO0HAfD4Ggr45eyE7LVApYlXcmuGD31NrjvZyfLw5VSlym/vIwUl03hOyQ6AkpL0fUIYwuTlXW60KJF0CsXBVKy8TCcHjLjiPTgHvXyvVKAB6jhUYEbPS3+zb7LbE3GP+l04iEzAzotUjU6SHlTqRhOHZcEHiZ"
        }
        """.trimIndent()
        
        val decryptor = AegisDecryptor()
        assertTrue(decryptor.isEncryptedAegisFile(encryptedJson))
    }

    @Test
    fun testIsNotEncryptedAegisFile() {
        // 测试未加密的Aegis文件
        val unencryptedJson = """
        {
            "version": 1,
            "header": {
                "slots": null,
                "params": {
                    "nonce": "3b76850a6cbd48d257a2c92c",
                    "tag": "9a6105cc61139809670eb62e78e8bc53"
                }
            },
            "db": {
                "entries": []
            }
        }
        """.trimIndent()
        
        val decryptor = AegisDecryptor()
        assertFalse(decryptor.isEncryptedAegisFile(unencryptedJson))
    }
}