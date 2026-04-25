package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.data.Category

class LocalCategoryMoveSupportTest {

    @Test
    fun buildLocalCategoryPathOptions_includesVirtualParentDirectories() {
        val options = buildLocalCategoryPathOptions(
            listOf(
                Category(id = 1, name = "应用分类/1.社交聊天"),
                Category(id = 2, name = "应用分类/2.生活服务")
            )
        )

        assertEquals(
            listOf("应用分类", "应用分类/1.社交聊天", "应用分类/2.生活服务"),
            options.map { it.path }
        )
        assertNull(options.first { it.path == "应用分类" }.category)
    }

    @Test
    fun buildLocalCategoryPathOptions_canReturnOnlyRealCategoryTargets() {
        val options = buildLocalCategoryPathOptions(
            categories = listOf(
                Category(id = 1, name = "应用分类/1.社交聊天"),
                Category(id = 2, name = "应用分类/2.生活服务")
            ),
            includeVirtualParents = false
        )

        assertEquals(
            listOf("应用分类/1.社交聊天", "应用分类/2.生活服务"),
            options.map { it.path }
        )
        assertEquals(listOf(1L, 2L), options.map { it.category?.id })
    }

    @Test
    fun localCategoryHierarchyLabel_usesLeafNameWithDepthIndent() {
        assertEquals("应用分类", localCategoryHierarchyLabel("应用分类"))
        assertEquals("|- 1.社交聊天", localCategoryHierarchyLabel("应用分类/1.社交聊天"))
        assertEquals("  |- 子目录", localCategoryHierarchyLabel("应用分类/1.社交聊天/子目录"))
    }
}
