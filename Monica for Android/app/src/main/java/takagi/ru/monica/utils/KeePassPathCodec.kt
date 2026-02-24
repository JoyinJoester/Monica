package takagi.ru.monica.utils

import android.net.Uri

const val KEEPASS_DISPLAY_PATH_SEPARATOR = " > "

fun encodeKeePassPathSegment(segment: String): String {
    return Uri.encode(segment.trim())
}

fun buildKeePassPathKey(parentPathKey: String?, segmentName: String): String {
    val normalizedParent = parentPathKey?.trim().orEmpty()
    val encodedSegment = encodeKeePassPathSegment(segmentName)
    return if (normalizedParent.isBlank()) encodedSegment else "$normalizedParent/$encodedSegment"
}

fun decodeKeePassPathSegments(pathKey: String?): List<String> {
    return pathKey
        ?.split('/')
        ?.map { Uri.decode(it).trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
}

fun decodeKeePassPathForDisplay(pathKey: String?): String {
    val segments = decodeKeePassPathSegments(pathKey)
    return if (segments.isEmpty()) pathKey.orEmpty() else segments.joinToString(KEEPASS_DISPLAY_PATH_SEPARATOR)
}
