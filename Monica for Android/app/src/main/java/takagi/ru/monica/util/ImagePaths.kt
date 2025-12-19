package takagi.ru.monica.util

/**
 * 图片路径数据类
 * 用于存储正面和背面图片的路径
 */
data class ImagePaths(
    val front: String? = null,
    val back: String? = null
) {
    /**
     * 检查是否为空（两个图片路径都为空或空字符串）
     */
    fun isEmpty(): Boolean {
        return (front.isNullOrEmpty() && back.isNullOrEmpty())
    }
    
    /**
     * 转换为列表格式
     */
    fun toList(): List<String> {
        return listOf(
            front ?: "",
            back ?: ""
        )
    }
    
    /**
     * 设置正面图片路径
     */
    fun withFront(frontPath: String): ImagePaths {
        return copy(front = frontPath.ifEmpty { null })
    }
    
    /**
     * 设置背面图片路径
     */
    fun withBack(backPath: String): ImagePaths {
        return copy(back = backPath.ifEmpty { null })
    }
    
    /**
     * 移除正面图片路径
     */
    fun removeFront(): ImagePaths {
        return copy(front = null)
    }
    
    /**
     * 移除背面图片路径
     */
    fun removeBack(): ImagePaths {
        return copy(back = null)
    }
    
    companion object {
        /**
         * 创建空的ImagePaths实例
         */
        fun empty(): ImagePaths {
            return ImagePaths()
        }
        
        /**
         * 从列表创建ImagePaths实例
         */
        fun fromList(list: List<String>): ImagePaths {
            return ImagePaths(
                front = list.getOrNull(0)?.takeIf { it.isNotEmpty() },
                back = list.getOrNull(1)?.takeIf { it.isNotEmpty() }
            )
        }
    }
}