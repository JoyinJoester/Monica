package takagi.ru.monica.wear.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.screens.PinLockScreen

/**
 * 修改PIN码全屏界面
 */
@Composable
fun ChangeLockDialog(
    securityManager: WearSecurityManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(Step.VERIFY) }
    
    // 使用全屏Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (currentStep) {
            Step.VERIFY -> {
                // 验证当前PIN码
                PinLockScreen(
                    isFirstTime = false,
                    onPinEntered = { pin ->
                        if (securityManager.verifyPin(pin)) {
                            currentStep = Step.SET_NEW
                        } else {
                            Toast.makeText(context, "PIN码错误", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Step.SET_NEW -> {
                // 设置新PIN码
                PinLockScreen(
                    isFirstTime = true,
                    onPinEntered = { pin ->
                        securityManager.setPin(pin)
                        Toast.makeText(context, "PIN码设置成功", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private enum class Step {
    VERIFY,
    SET_NEW
}
