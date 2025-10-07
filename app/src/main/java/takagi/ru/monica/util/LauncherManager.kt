package takagi.ru.monica.util

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest

/**
 * 全局Launcher管理器
 * 解决Android对ActivityResultLauncher数量限制的问题
 * 所有页面共享同一组launcher，避免创建过多实例导致崩溃
 */
object LauncherManager {
    // 相册选择回调
    private var galleryCallback: ((Uri?) -> Unit)? = null
    
    // 相机拍照回调
    private var cameraCallback: ((Boolean) -> Unit)? = null
    
    // 文件选择回调
    private var filePickerCallback: ((Uri?) -> Unit)? = null
    
    // 文件创建回调
    private var createDocumentCallback: ((Uri?) -> Unit)? = null
    
    // 权限请求回调
    private var permissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    
    // Launcher引用
    var galleryLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>? = null
    var cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>? = null
    var filePickerLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null
    var createDocumentLauncher: ManagedActivityResultLauncher<String, Uri?>? = null
    var permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null
    
    // 相机临时URI
    var tempCameraUri: Uri? = null
    
    /**
     * 启动相册选择
     */
    fun launchGallery(request: PickVisualMediaRequest, callback: (Uri?) -> Unit) {
        galleryCallback = callback
        galleryLauncher?.launch(request)
    }
    
    /**
     * 启动相机拍照
     */
    fun launchCamera(uri: Uri, callback: (Boolean) -> Unit) {
        tempCameraUri = uri
        cameraCallback = callback
        cameraLauncher?.launch(uri)
    }
    
    /**
     * 启动文件选择器
     */
    fun launchFilePicker(mimeTypes: Array<String>, callback: (Uri?) -> Unit) {
        filePickerCallback = callback
        filePickerLauncher?.launch(mimeTypes)
    }
    
    /**
     * 启动文件创建
     */
    fun launchCreateDocument(fileName: String, callback: (Uri?) -> Unit) {
        createDocumentCallback = callback
        createDocumentLauncher?.launch(fileName)
    }
    
    /**
     * 请求权限
     */
    fun requestPermissions(permissions: Array<String>, callback: (Map<String, Boolean>) -> Unit) {
        permissionCallback = callback
        permissionLauncher?.launch(permissions)
    }
    
    /**
     * 处理相册选择结果
     */
    fun handleGalleryResult(uri: Uri?) {
        galleryCallback?.invoke(uri)
        galleryCallback = null
    }
    
    /**
     * 处理相机拍照结果
     */
    fun handleCameraResult(success: Boolean) {
        cameraCallback?.invoke(success)
        cameraCallback = null
        tempCameraUri = null
    }
    
    /**
     * 处理文件选择结果
     */
    fun handleFilePickerResult(uri: Uri?) {
        filePickerCallback?.invoke(uri)
        filePickerCallback = null
    }
    
    /**
     * 处理文件创建结果
     */
    fun handleCreateDocumentResult(uri: Uri?) {
        createDocumentCallback?.invoke(uri)
        createDocumentCallback = null
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(result: Map<String, Boolean>) {
        permissionCallback?.invoke(result)
        permissionCallback = null
    }
}
