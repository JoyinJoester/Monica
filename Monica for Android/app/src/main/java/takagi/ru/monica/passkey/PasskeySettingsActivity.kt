package takagi.ru.monica.passkey

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.ui.screens.PasskeySettingsScreen
import takagi.ru.monica.ui.theme.MonicaTheme

/**
 * Passkey 设置入口 Activity
 * 用于系统设置中的 Credential Provider 页面跳转
 */
class PasskeySettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MonicaTheme {
                PasskeySettingsScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
