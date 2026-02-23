package takagi.ru.monica.ui.common.dialog

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.utils.BiometricHelper

/**
 * 支持指纹验证的删除确认对话框
 *
 * @param itemTitle 要删除的项目标题
 * @param itemType 项目类型描述（如"密码"、"验证器"、"证件"）
 * @param onDismiss 取消删除的回调
 * @param onConfirmWithPassword 使用密码确认删除的回调
 * @param onConfirmWithBiometric 使用指纹确认删除的回调
 */
@Composable
fun DeleteConfirmDialog(
    itemTitle: String,
    itemType: String = "Item",
    biometricEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirmWithPassword: (String) -> Unit,
    onConfirmWithBiometric: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var passwordInput by remember { mutableStateOf("") }
    val biometricHelper = remember { BiometricHelper(context) }
    val isBiometricAvailable = remember(biometricEnabled) {
        biometricEnabled && biometricHelper.isBiometricAvailable()
    }

    val biometricAction = if (isBiometricAvailable && activity != null) {
        {
            biometricHelper.authenticate(
                activity = activity,
                title = context.getString(R.string.verify_identity),
                subtitle = context.getString(R.string.verify_to_delete),
                description = context.getString(R.string.biometric_login_description),
                onSuccess = {
                    onConfirmWithBiometric()
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                },
                onFailed = {}
            )
        }
    } else {
        null
    }
    M3IdentityVerifyDialog(
        title = stringResource(R.string.delete_item_title, itemType),
        message = stringResource(R.string.delete_item_message, itemType, itemTitle),
        passwordValue = passwordInput,
        onPasswordChange = { passwordInput = it },
        onDismiss = onDismiss,
        onConfirm = {
            if (passwordInput.isNotEmpty()) {
                onConfirmWithPassword(passwordInput)
            }
        },
        confirmText = stringResource(R.string.delete),
        destructiveConfirm = true,
        onBiometricClick = biometricAction,
        biometricHintText = if (biometricAction == null) {
            context.getString(R.string.biometric_not_available)
        } else {
            null
        }
    )
}
