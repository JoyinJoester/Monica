package takagi.ru.monica.generator

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.Locale

/**
 * 🔐 密码短语生成器（Diceware 风格）
 *
 * 参考 Keyguard `PasswordGeneratorDiceware` 实现。
 * - 支持自定义词表 / 默认 EFF 词表
 * - 支持自定义分隔符
 * - 支持首字母大写
 * - 支持在随机单词后追加数字
 * - 支持插入自定义单词
 */
class PassphraseGenerator(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    /**
     * 生成密码短语
     */
    fun generate(config: PassphraseConfig, context: Context? = null): String {
        require(config.length > 0) { "Passphrase length must be greater than zero." }

        val effectiveWordlist = config.wordlist
            ?.takeUnless { it.isEmpty() }
            ?: loadDefaultWordlist(context)

        require(effectiveWordlist.isNotEmpty()) {
            "Wordlist must not be empty."
        }

        // 随机选择插入自定义单词的位置
        val customWordIndex = if (config.customWord != null) {
            secureRandom.nextInt(config.length)
        } else {
            -1
        }

        val phrases = buildList {
            repeat(config.length) { index ->
                val rawWord = when {
                    index == customWordIndex && config.customWord != null -> config.customWord
                    else -> effectiveWordlist.random(secureRandom)
                }

                val capitalized = if (config.capitalize) {
                    rawWord.replaceFirstChar { char ->
                        if (char.isLowerCase()) {
                            char.titlecase(Locale.ROOT)
                        } else {
                            char.toString()
                        }
                    }
                } else {
                    rawWord
                }

                add(capitalized)
            }
        }

        // 如果需要附加数字，则挑选一个单词添加随机数字
        val finalPhrases = if (config.includeNumber) {
            val targetIndex = secureRandom.nextInt(phrases.size)
            val numberRange = when (config.length) {
                1 -> 1000..9999
                2 -> 100..999
                else -> 10..99
            }
            phrases.mapIndexed { index, word ->
                if (index == targetIndex) {
                    val number = numberRange.random(secureRandom)
                    "$word$number"
                } else {
                    word
                }
            }
        } else {
            phrases
        }

        return finalPhrases.joinToString(separator = config.delimiter)
    }

    private fun Iterable<String>.random(random: SecureRandom): String {
        val list = this.toList()
        val index = random.nextInt(list.size)
        return list[index]
    }

    private fun IntRange.random(random: SecureRandom): Int {
        val bound = last - first + 1
        val value = random.nextInt(bound)
        return first + value
    }

    private fun loadDefaultWordlist(context: Context?): List<String> {
        // 如果提供 Context，则尝试从 raw/eff_short_wordlist 中加载
        if (context != null) {
            val resId = context.resources.getIdentifier(
                "eff_short_wordlist",
                "raw",
                context.packageName
            )
            if (resId != 0) {
                val inputStream = context.resources.openRawResource(resId)
                return BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.filter { it.isNotBlank() }
                        .map { line ->
                            // 词表格式为 "11111	eff-word"，需要剥离骰子序列
                            val parts = line.split('\t', ' ', limit = 2)
                            if (parts.size == 2) parts[1].trim() else parts.first().trim()
                        }
                        .toList()
                }
            }
        }

        // 如果没有资源或 context，则使用内置最小词表作为后备
        return DEFAULT_FALLBACK_WORDLIST
    }

    companion object {
        private val DEFAULT_FALLBACK_WORDLIST = listOf(
            "alpha", "bravo", "charlie", "delta", "echo",
            "foxtrot", "golf", "hotel", "india", "juliet",
            "kilo", "lima", "mike", "november", "oscar",
            "papa", "quebec", "romeo", "sierra", "tango",
            "uniform", "victor", "whiskey", "xray", "yankee", "zulu"
        )
    }
}

/**
 * 密码短语配置
 */
data class PassphraseConfig(
    val length: Int = 4,
    val delimiter: String = "-",
    val capitalize: Boolean = false,
    val includeNumber: Boolean = false,
    val customWord: String? = null,
    val wordlist: List<String>? = null
)
