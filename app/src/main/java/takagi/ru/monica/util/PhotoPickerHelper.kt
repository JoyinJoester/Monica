package takagi.ru.monica.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.content.pm.PackageManager

/**
 * 照片选择助手类
 * 使用传统Intent方式处理图片拍照和从相册选择，完全避免requestCode问题
 */
object PhotoPickerHelper {
    const val REQUEST_CODE_CAMERA = 2001
    const val REQUEST_CODE_GALLERY = 2002
    
    // 当前标签，用于区分正面和背面
    var currentTag: String = ""
    
    // 回调接口
    interface PhotoPickerCallback {
        fun onPhotoSelected(imagePath: String?)
        fun onError(error: String)
    }
    
    // 当前回调实例
    private var currentCallback: PhotoPickerCallback? = null
    private var context: Context? = null
    
    // 临时照片文件
    private var tempPhotoFile: File? = null
    
    /**
     * 设置回调
     */
    fun setCallback(context: Context, callback: PhotoPickerCallback) {
        this.context = context
        currentCallback = callback
    }
    
    /**
     * 检查是否有相机应用
     * @param activity 调用的Activity
     * @return 是否有相机应用
     */
    private fun hasCameraApp(activity: Activity): Boolean {
        return try {
            // 方法1: 使用resolveActivity检查
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolveInfo = activity.packageManager.resolveActivity(intent, 0)
            
            // 方法2: 检查系统功能
            val hasSystemFeature = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            
            // 方法3: 查询所有可以处理拍照Intent的应用
            val activities = activity.packageManager.queryIntentActivities(intent, 0)
            val hasActivities = activities.isNotEmpty()
            
            // 如果任一方法返回true，则认为有相机应用
            resolveInfo != null || hasSystemFeature || hasActivities
        } catch (e: Exception) {
            // 出现异常时，默认认为有相机应用，让系统去处理
            true
        }
    }
    
    /**
     * 拍照
     * @param activity 调用的Activity
     */
    fun takePhoto(activity: Activity) {
        try {
            // 保存context引用
            this.context = activity
            
            // 创建临时文件
            val photoFile = createTempPhotoFile(activity)
            tempPhotoFile = photoFile
            
            // 创建URI
            val photoURI = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile
            )
            
            // 授予相机应用对URI的临时权限
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // 使用更可靠的检测方法
            if (hasCameraApp(activity)) {
                try {
                    activity.startActivityForResult(intent, REQUEST_CODE_CAMERA)
                } catch (e: Exception) {
                    // 即使检测到有相机应用，启动时仍可能出错，这时使用图库作为备选
                    currentCallback?.onError("启动相机失败: ${e.message}，将使用图库选择")
                    pickFromGallery(activity)
                }
            } else {
                // 如果没有相机应用，使用图库作为替代方案
                currentCallback?.onError("设备上没有检测到相机应用，将使用图库选择")
                pickFromGallery(activity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentCallback?.onError("启动相机失败: ${e.message}")
        }
    }
    
    /**
     * 从相册选择照片
     * @param activity 调用的Activity
     */
    fun pickFromGallery(activity: Activity) {
        try {
            // 保存context引用
            this.context = activity
            
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            
            // 检查是否有应用可以处理这个Intent
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivityForResult(intent, REQUEST_CODE_GALLERY)
            } else {
                // 尝试使用其他Intent action
                val alternativeIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                
                if (alternativeIntent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivityForResult(alternativeIntent, REQUEST_CODE_GALLERY)
                } else {
                    currentCallback?.onError("设备上没有可用的图库应用")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentCallback?.onError("启动相册失败: ${e.message}")
        }
    }
    
    /**
     * 创建临时照片文件
     */
    private fun createTempPhotoFile(context: Context): File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val imageFileName = "JPEG_${timeStamp}_"
        // 确保使用正确的目录，与file_paths.xml中的配置匹配
        val storageDir = File(context.cacheDir, "temp_photos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    /**
     * 处理拍照结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回数据
     */
    fun handleCameraResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                tempPhotoFile?.let { file ->
                    // 检查文件是否存在且不为空
                    if (file.exists() && file.length() > 0) {
                        currentCallback?.onPhotoSelected(file.absolutePath)
                    } else {
                        // 尝试等待一段时间再检查（相机可能还在写入文件）
                        try {
                            Thread.sleep(500) // 等待500毫秒
                        } catch (e: InterruptedException) {
                            // 忽略中断异常
                        }
                        
                        if (file.exists() && file.length() > 0) {
                            currentCallback?.onPhotoSelected(file.absolutePath)
                        } else {
                            currentCallback?.onError("照片文件为空或不存在")
                        }
                    }
                } ?: run {
                    currentCallback?.onError("临时照片文件不存在")
                }
            } else {
                currentCallback?.onPhotoSelected(null)
            }
            tempPhotoFile = null
            return true
        }
        return false
    }
    
    /**
     * 处理相册选择结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回数据
     */
    fun handleGalleryResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_GALLERY) {
            if (resultCode == Activity.RESULT_OK) {
                val uri: Uri? = data?.data
                if (uri != null) {
                    // 将URI转换为文件路径
                    val imagePath = copyImageToFile(uri)
                    if (imagePath != null) {
                        currentCallback?.onPhotoSelected(imagePath)
                    } else {
                        currentCallback?.onError("保存图片失败")
                    }
                } else {
                    currentCallback?.onError("未选择图片")
                }
            } else {
                currentCallback?.onPhotoSelected(null)
            }
            return true
        }
        return false
    }
    
    /**
     * 将URI复制到临时文件
     */
    private fun copyImageToFile(uri: Uri): String? {
        return try {
            context?.let { ctx ->
                // 创建临时文件
                val tempFile = createTempPhotoFile(ctx)
                
                // 从URI读取图片数据
                val inputStream: InputStream = ctx.contentResolver.openInputStream(uri)
                    ?: run {
                        return null
                    }
                
                // 解码图片
                val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                // 检查bitmap是否成功解码
                if (bitmap == null) {
                    return null
                }
                
                // 将图片保存到临时文件
                val outputStream = FileOutputStream(tempFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.close()
                
                // 返回临时文件路径
                tempFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}