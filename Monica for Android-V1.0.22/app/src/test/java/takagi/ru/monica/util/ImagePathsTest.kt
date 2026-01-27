package takagi.ru.monica.util

import org.junit.Test
import org.junit.Assert.*

class ImagePathsTest {
    
    @Test
    fun testEmpty() {
        val empty = ImagePaths.empty()
        assertTrue(empty.isEmpty())
        assertNull(empty.front)
        assertNull(empty.back)
    }
    
    @Test
    fun testFromList() {
        // 测试空列表
        val emptyList = ImagePaths.fromList(emptyList())
        assertTrue(emptyList.isEmpty())
        
        // 测试只有一个元素的列表
        val singleList = ImagePaths.fromList(listOf("front.jpg"))
        assertFalse(singleList.isEmpty())
        assertEquals("front.jpg", singleList.front)
        assertNull(singleList.back)
        
        // 测试有两个元素的列表
        val twoList = ImagePaths.fromList(listOf("front.jpg", "back.jpg"))
        assertFalse(twoList.isEmpty())
        assertEquals("front.jpg", twoList.front)
        assertEquals("back.jpg", twoList.back)
        
        // 测试有空字符串的列表
        val emptyStringList = ImagePaths.fromList(listOf("", "back.jpg"))
        assertFalse(emptyStringList.isEmpty())
        assertNull(emptyStringList.front)
        assertEquals("back.jpg", emptyStringList.back)
    }
    
    @Test
    fun testToList() {
        // 测试空对象
        val empty = ImagePaths.empty()
        val emptyList = empty.toList()
        assertEquals(2, emptyList.size)
        assertEquals("", emptyList[0])
        assertEquals("", emptyList[1])
        
        // 测试只有正面图片
        val frontOnly = ImagePaths(front = "front.jpg")
        val frontList = frontOnly.toList()
        assertEquals(2, frontList.size)
        assertEquals("front.jpg", frontList[0])
        assertEquals("", frontList[1])
        
        // 测试只有背面图片
        val backOnly = ImagePaths(back = "back.jpg")
        val backList = backOnly.toList()
        assertEquals(2, backList.size)
        assertEquals("", backList[0])
        assertEquals("back.jpg", backList[1])
        
        // 测试两个图片都有
        val both = ImagePaths(front = "front.jpg", back = "back.jpg")
        val bothList = both.toList()
        assertEquals(2, bothList.size)
        assertEquals("front.jpg", bothList[0])
        assertEquals("back.jpg", bothList[1])
    }
    
    @Test
    fun testWithFront() {
        val empty = ImagePaths.empty()
        val withFront = empty.withFront("front.jpg")
        assertEquals("front.jpg", withFront.front)
        assertNull(withFront.back)
    }
    
    @Test
    fun testWithBack() {
        val empty = ImagePaths.empty()
        val withBack = empty.withBack("back.jpg")
        assertNull(withBack.front)
        assertEquals("back.jpg", withBack.back)
    }
    
    @Test
    fun testRemoveFront() {
        val both = ImagePaths(front = "front.jpg", back = "back.jpg")
        val withoutFront = both.removeFront()
        assertNull(withoutFront.front)
        assertEquals("back.jpg", withoutFront.back)
    }
    
    @Test
    fun testRemoveBack() {
        val both = ImagePaths(front = "front.jpg", back = "back.jpg")
        val withoutBack = both.removeBack()
        assertEquals("front.jpg", withoutBack.front)
        assertNull(withoutBack.back)
    }
}