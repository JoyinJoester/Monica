package takagi.ru.monica.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件操作助手类
 * 使用传统Intent方式处理文件导出/导入，完全避免requestCode问题
 */
object FileOperationHelper {
    const val REQUEST_CODE_EXPORT = 1001
    const val REQUEST_CODE_IMPORT = 1002
    
    // 回调接口
    interface FileOperationCallback {
        fun onExportFileSelected(uri: Uri?)
        fun onImportFileSelected(uri: Uri?)
    }
    
    // 当前回调实例
    private var currentCallback: FileOperationCallback? = null
    
    /**
     * 设置回调
     */
    fun setCallback(callback: FileOperationCallback) {
        currentCallback = callback
    }
    
    /**
     * 导出数据到CSV文件
     * @param activity 调用的Activity
     * @param fileName 文件名
     */
    fun exportToCsv(activity: Activity, fileName: String = getDefaultExportFileName()) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }
    
    /**
     * 从CSV文件导入数据（普通数据和支付宝账单）
     * @param activity 调用的Activity
     */
    fun importFromCsv(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv", 
                "text/comma-separated-values", 
                "text/plain", 
                "application/csv"
            ))
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }
    
    /**
     * 从JSON文件导入数据（Aegis数据）
     * @param activity 调用的Activity
     */
    fun importFromJson(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "text/json",
                "text/plain"
            ))
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }
    
    /**
     * 从Steam .maFile文件导入数据
     * @param activity 调用的Activity
     */
    fun importFromMaFile(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // 使用通配符类型，允许所有文件
            type = "*/*"
            // 不限制MIME类型，这样.maFile文件就可以被选择
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    /**
     * 从 Stratum/AuthPro 备份文件导入数据
     * 支持 .stratum（二进制）、.txt、.html
     */
    fun importFromStratum(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",
                "text/plain",
                "text/html",
                "application/json"
            ))
        }

        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    /**
     * 从ZIP文件导入数据
     * @param activity 调用的Activity
     */
    fun importFromZip(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-compressed", 
                "multipart/x-zip"
            ))
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }
    
    /**
     * 从 KDBX 文件导入数据 (KeePass 格式)
     * @param activity 调用的Activity
     */
    fun importFromKdbx(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // 使用通用类型，因为 .kdbx 没有标准 MIME 类型
            type = "*/*"
        }
        
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }
    
    /**
     * 获取默认导出文件名
     */
    private fun getDefaultExportFileName(): String {
        return "Monica_Export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
    }
    
    /**
     * 处理导出结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回数据
     */
    fun handleExportResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_CODE_EXPORT && resultCode == Activity.RESULT_OK) {
            currentCallback?.onExportFileSelected(data?.data)
        } else if (requestCode == REQUEST_CODE_EXPORT) {
            currentCallback?.onExportFileSelected(null)
        }
    }
    
    /**
     * 处理导入结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回数据
     */
    fun handleImportResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
            currentCallback?.onImportFileSelected(data?.data)
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            currentCallback?.onImportFileSelected(null)
        }
    }
}
