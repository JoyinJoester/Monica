# SwipeActions 调试指南

## 问题追踪
**日期**: 2025-10-12  
**问题**: 滑动后无效果，不弹出删除框和多选控件

## 已实施的修复

### 修复 1：移除 Card 上的 clickable
将 `clickable` 从 Card 移到内部的 Row/Column 上，避免在 Surface 层拦截触摸事件。

**修改的文件：**
1. `SimpleMainScreen.kt` - StackedPasswordGroup 堆叠卡片
2. `SimpleMainScreen.kt` - PasswordEntryCard 密码卡片  
3. `DocumentCard.kt` - 文档卡片

### 修复 2：添加调试日志
在 SwipeActions 组件中添加了详细的调试日志，追踪滑动事件。

## 测试步骤

### 1. 安装并运行 APK
```bash
# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 或者直接运行
.\gradlew installDebug
```

### 2. 查看日志输出
在另一个终端窗口运行：
```bash
adb logcat -s SwipeActions:D
```

### 3. 测试滑动操作

#### 测试场景 A：密码列表
1. 打开应用，进入密码列表
2. 在任意密码卡片上**从右向左滑动**（删除操作）
3. 观察：
   - [ ] 卡片是否跟随手指移动？
   - [ ] 背景是否显示红色？
   - [ ] 释放后是否弹出删除对话框？
   - [ ] 日志是否输出：`Drag started`, `Drag ended`, `Triggering LEFT swipe`

4. 在任意密码卡片上**从左向右滑动**（选择操作）
5. 观察：
   - [ ] 卡片是否跟随手指移动？
   - [ ] 背景是否显示蓝色？
   - [ ] 释放后是否进入多选模式？
   - [ ] 日志是否输出：`Triggering RIGHT swipe`

#### 测试场景 B：文档列表
重复场景 A 的步骤

#### 测试场景 C：TOTP 列表
重复场景 A 的步骤

## 预期的日志输出

### 成功的滑动（左滑删除）
```
D/SwipeActions: Drag started, cardWidth: 1080.0
D/SwipeActions: Drag ended, offsetX: -600.0, threshold: 540.0
D/SwipeActions: Triggering LEFT swipe (delete)
```

### 成功的滑动（右滑选择）
```
D/SwipeActions: Drag started, cardWidth: 1080.0
D/SwipeActions: Drag ended, offsetX: 600.0, threshold: 540.0
D/SwipeActions: Triggering RIGHT swipe (select)
```

### 滑动距离不足（取消）
```
D/SwipeActions: Drag started, cardWidth: 1080.0
D/SwipeActions: Drag ended, offsetX: -200.0, threshold: 540.0
D/SwipeActions: Swipe cancelled (not enough distance)
```

### 没有日志输出
如果完全没有日志输出，说明触摸事件被拦截了，SwipeActions 的 `pointerInput` 没有接收到事件。

## 可能的问题及解决方案

### 问题 1：没有任何日志输出
**原因**: 触摸事件被拦截，没有到达 SwipeActions 的 `pointerInput`

**解决方案 A** - 检查父容器是否有消费事件的修饰符：
```kotlin
// ❌ 错误：父容器拦截了事件
LazyColumn(
    modifier = Modifier.clickable { }  // 这会拦截所有触摸
) {
    items(...) { 
        SwipeActions(...) { ... }
    }
}

// ✅ 正确：父容器不拦截事件
LazyColumn {
    items(...) { 
        SwipeActions(...) { ... }
    }
}
```

**解决方案 B** - 使用 `Modifier.pointerInput` 在更外层：
在 LazyColumn 的 item 级别添加 `pointerInput`，而不是在 SwipeActions 内部。

### 问题 2：有日志但卡片不移动
**原因**: `animatedOffset` 没有正确更新 UI

**解决方案**: 检查 `graphicsLayer` 是否正确应用：
```kotlin
.graphicsLayer {
    translationX = animatedOffset.value  // 确保这行存在
}
```

### 问题 3：卡片移动但回调不触发
**原因**: 
- 滑动距离不足（< 50% 卡片宽度）
- 回调函数有错误

**解决方案**: 
1. 降低阈值进行测试：`val dynamicThreshold = cardWidth * 0.3f`
2. 在回调函数中添加日志确认是否执行

### 问题 4：回调触发但对话框不显示
**原因**: 状态更新或对话框组件有问题

**解决方案**: 检查状态更新和对话框代码：
```kotlin
// 确保状态正确更新
onSwipeLeft = { password ->
    haptic.performWarning()
    itemToDelete = password  // 这行必须执行
    deletedItemIds = deletedItemIds + password.id
    
    // 添加日志确认
    android.util.Log.d("PasswordList", "Set itemToDelete: ${password.title}")
}

// 确保对话框正确渲染
itemToDelete?.let { item ->
    android.util.Log.d("PasswordList", "Showing delete dialog for: ${item.title}")
    AlertDialog(...) { ... }
}
```

## 临时测试：降低阈值

如果您想快速测试滑动是否工作，可以临时降低阈值：

### 修改 SwipeActions.kt (Line ~220)
```kotlin
// 临时修改：从 50% 降低到 20%
val dynamicThreshold = cardWidth * 0.2f  // 原来是 0.5f

when {
    offsetX < -dynamicThreshold -> {
        // 触发删除
    }
    offsetX > dynamicThreshold -> {
        // 触发选择
    }
}
```

这样只需要滑动 20% 卡片宽度就能触发操作，更容易测试。

## 替代方案：使用 SwipeToDismiss

如果 `pointerInput` 方案仍然有问题，可以考虑使用 Material 3 的 `SwipeToDismiss` 组件：

```kotlin
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

@Composable
fun AlternativeSwipeCard() {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑删除
                    onSwipeLeft()
                    false  // 返回 false 不自动消失
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑选择
                    onSwipeRight()
                    false
                }
                else -> false
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { /* 背景内容 */ }
    ) {
        Card { /* 卡片内容 */ }
    }
}
```

## 下一步行动

1. **立即行动**: 运行应用，尝试滑动，查看日志输出
2. **报告结果**: 
   - 是否有日志输出？
   - 卡片是否移动？
   - 回调是否触发？
   - 对话框是否显示？
3. **根据日志调整**: 根据日志输出和实际行为，确定具体问题所在

---

**调试版本**: 1.0  
**包含日志**: ✅ 是  
**测试设备要求**: Android 7.0+  
**创建日期**: 2025-10-12
