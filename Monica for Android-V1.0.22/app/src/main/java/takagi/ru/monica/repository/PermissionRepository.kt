package takagi.ru.monica.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.core.content.ContextCompat
import takagi.ru.monica.R
import takagi.ru.monica.data.model.PermissionCategory
import takagi.ru.monica.data.model.PermissionImportance
import takagi.ru.monica.data.model.PermissionInfo
import takagi.ru.monica.data.model.PermissionStats
import takagi.ru.monica.data.model.PermissionStatus

/**
 * 权限管理Repository
 * Permission management repository
 */
class PermissionRepository(private val context: Context) {

    private var cachedPermissions: List<PermissionInfo>? = null
    private var cacheTimestamp: Long = 0
    private val cacheValidityMs = 5000L // 5秒缓存

    /**
     * 获取所有权限信息
     * Get all permissions with current status
     */
    fun getAllPermissions(forceRefresh: Boolean = false): List<PermissionInfo> {
        val now = System.currentTimeMillis()

        if (!forceRefresh &&
            cachedPermissions != null &&
            (now - cacheTimestamp) < cacheValidityMs
        ) {
            return cachedPermissions!!
        }

        val permissions = loadPermissions()
        cachedPermissions = permissions
        cacheTimestamp = now

        return permissions
    }

    /**
     * 加载所有权限并检测状态
     * Load all permissions and check their status
     */
    private fun loadPermissions(): List<PermissionInfo> {
        return listOf(
            createBiometricPermission(),
            createCameraPermission(),
            createStoragePermission(),
            createInternetPermission(),
            createNetworkStatePermission(),
            createVibratePermission(),
            createNotificationPermission(),
            createPhoneStatePermission(),
            createAutofillPermission()
        ).map { permission ->
            try {
                val status = checkPermissionStatus(permission)
                permission.copy(status = status)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check permission: ${permission.id}", e)
                permission.copy(status = PermissionStatus.UNKNOWN)
            }
        }
    }

    /**
     * 检测权限状态
     * Check permission status
     */
    private fun checkPermissionStatus(permission: PermissionInfo): PermissionStatus {
        return when (permission.id) {
            "BIOMETRIC" -> checkBiometricStatus()
            "AUTOFILL" -> checkAutofillStatus()
            "INTERNET", "NETWORK_STATE", "VIBRATE" -> {
                // 这些权限在安装时自动授予
                PermissionStatus.GRANTED
            }
            "NOTIFICATION" -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    val result = ContextCompat.checkSelfPermission(
                        context,
                        permission.androidPermission
                    )
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        PermissionStatus.GRANTED
                    } else {
                        PermissionStatus.DENIED
                    }
                } else {
                    PermissionStatus.GRANTED
                }
            }
            else -> {
                val result = ContextCompat.checkSelfPermission(
                    context,
                    permission.androidPermission
                )
                if (result == PackageManager.PERMISSION_GRANTED) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            }
        }
    }

    /**
     * 检测生物识别状态
     * Check biometric authentication status
     */
    private fun checkBiometricStatus(): PermissionStatus {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> PermissionStatus.GRANTED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> PermissionStatus.UNAVAILABLE
                else -> PermissionStatus.DENIED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check biometric status", e)
            PermissionStatus.UNKNOWN
        }
    }

    /**
     * 检测自动填充服务状态
     * Check autofill service status
     */
    private fun checkAutofillStatus(): PermissionStatus {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val autofillManager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
                if (autofillManager?.hasEnabledAutofillServices() == true) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            } else {
                PermissionStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check autofill status", e)
            PermissionStatus.UNKNOWN
        }
    }

    /**
     * 按分类分组权限
     * Group permissions by category
     */
    fun getPermissionsByCategory(): Map<PermissionCategory, List<PermissionInfo>> {
        return getAllPermissions().groupBy { it.category }
    }

    /**
     * 获取权限统计信息
     * Get permission statistics
     */
    fun getPermissionStats(): PermissionStats {
        val allPermissions = getAllPermissions()
        val requiredPermissions = allPermissions.filter {
            it.importance == PermissionImportance.REQUIRED
        }
        val grantedRequired = requiredPermissions.count {
            it.status == PermissionStatus.GRANTED
        }

        return PermissionStats(
            totalRequired = requiredPermissions.size,
            grantedRequired = grantedRequired,
            totalPermissions = allPermissions.size,
            grantedPermissions = allPermissions.count {
                it.status == PermissionStatus.GRANTED
            }
        )
    }

    // 创建各个权限信息的私有方法
    // Private methods to create permission info

    private fun createBiometricPermission() = PermissionInfo(
        id = "BIOMETRIC",
        androidPermission = Manifest.permission.USE_BIOMETRIC,
        icon = Icons.Default.Fingerprint,
        nameResId = R.string.permission_biometric_name,
        descriptionResId = R.string.permission_biometric_description,
        category = PermissionCategory.SECURITY,
        importance = PermissionImportance.RECOMMENDED
    )

    private fun createCameraPermission() = PermissionInfo(
        id = "CAMERA",
        androidPermission = Manifest.permission.CAMERA,
        icon = Icons.Default.CameraAlt,
        nameResId = R.string.permission_camera_name,
        descriptionResId = R.string.permission_camera_description,
        category = PermissionCategory.DEVICE,
        importance = PermissionImportance.RECOMMENDED
    )

    private fun createStoragePermission() = PermissionInfo(
        id = "STORAGE",
        androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
        icon = Icons.Default.Storage,
        nameResId = R.string.permission_storage_name,
        descriptionResId = R.string.permission_storage_description,
        category = PermissionCategory.STORAGE,
        importance = PermissionImportance.RECOMMENDED
    )

    private fun createInternetPermission() = PermissionInfo(
        id = "INTERNET",
        androidPermission = Manifest.permission.INTERNET,
        icon = Icons.Default.Language,
        nameResId = R.string.permission_internet_name,
        descriptionResId = R.string.permission_internet_description,
        category = PermissionCategory.NETWORK,
        importance = PermissionImportance.OPTIONAL
    )

    private fun createNetworkStatePermission() = PermissionInfo(
        id = "NETWORK_STATE",
        androidPermission = Manifest.permission.ACCESS_NETWORK_STATE,
        icon = Icons.Default.NetworkCheck,
        nameResId = R.string.permission_network_state_name,
        descriptionResId = R.string.permission_network_state_description,
        category = PermissionCategory.NETWORK,
        importance = PermissionImportance.RECOMMENDED
    )

    private fun createVibratePermission() = PermissionInfo(
        id = "VIBRATE",
        androidPermission = Manifest.permission.VIBRATE,
        icon = Icons.Default.Vibration,
        nameResId = R.string.permission_vibrate_name,
        descriptionResId = R.string.permission_vibrate_description,
        category = PermissionCategory.DEVICE,
        importance = PermissionImportance.OPTIONAL
    )

    private fun createNotificationPermission() = PermissionInfo(
        id = "NOTIFICATION",
        androidPermission = if (Build.VERSION.SDK_INT >= 33) {
            "android.permission.POST_NOTIFICATIONS"
        } else {
            "android.permission.POST_NOTIFICATIONS"
        },
        icon = Icons.Default.Notifications,
        nameResId = R.string.permission_notification_name,
        descriptionResId = R.string.permission_notification_description,
        category = PermissionCategory.DEVICE,
        importance = PermissionImportance.RECOMMENDED
    )

    private fun createPhoneStatePermission() = PermissionInfo(
        id = "PHONE_STATE",
        androidPermission = Manifest.permission.READ_PHONE_STATE,
        icon = Icons.Default.PhoneAndroid,
        nameResId = R.string.permission_phone_state_name,
        descriptionResId = R.string.permission_phone_state_description,
        category = PermissionCategory.DEVICE,
        importance = PermissionImportance.OPTIONAL
    )

    private fun createAutofillPermission() = PermissionInfo(
        id = "AUTOFILL",
        androidPermission = "android.permission.BIND_AUTOFILL_SERVICE",
        icon = Icons.Default.AutoAwesome,
        nameResId = R.string.permission_autofill_name,
        descriptionResId = R.string.permission_autofill_description,
        category = PermissionCategory.SECURITY,
        importance = PermissionImportance.RECOMMENDED
    )

    companion object {
        private const val TAG = "PermissionRepository"
    }
}
