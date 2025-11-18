package takagi.ru.monica.wear.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import takagi.ru.monica.wear.viewmodel.TotpItemState

private const val SEARCH_LOG_TAG = "WearSearchOverlay"

/**
 * 搜索覆盖层组件 - Bottom Sheet 样式
 * 纯色背景，从底部弹出
 */
@Composable
fun SearchOverlay(
    searchQuery: String,
    searchResults: List<TotpItemState>,
    onSearchQueryChange: (String) -> Unit,
    onResultClick: (TotpItemState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    
    LaunchedEffect(searchQuery, searchResults.size) {
        Log.d(
            SEARCH_LOG_TAG,
            "Search state updated: query='${'$'}searchQuery', results=${'$'}{searchResults.size}"
        )
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val dismissAction: () -> Unit = remember(onDismiss) {
        {
            Log.d(SEARCH_LOG_TAG, "Dismiss requested")
            onDismiss()
        }
    }
    val handleResultClick: (TotpItemState) -> Unit = remember(onResultClick) {
        { item ->
            Log.d(
                SEARCH_LOG_TAG,
                "Search result clicked: issuer='${'$'}{item.totpData.issuer}', account='${'$'}{item.totpData.accountName}'"
            )
            onResultClick(item)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 半透明遮罩，点击空白区域可关闭
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.5f))
                // 不再在遮罩上捕获拖拽手势，避免与输入法/候选词交互冲突
                .clickable(
                    onClick = dismissAction,
                    indication = null,
                    interactionSource = scrimInteraction
                )
        )

        // Bottom Sheet 容器
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .offset(y = dragOffset.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1F2937))  // 纯色深色背景
                .padding(20.dp)
        ) {
            // 顶部拖动指示器区域 - 可拖动关闭
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > 100) {
                                    Log.d(SEARCH_LOG_TAG, "Search sheet drag dismissed, offset=${'$'}dragOffset")
                                    dismissAction()
                                }
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val newOffset = dragOffset + dragAmount
                                if (newOffset >= 0) {
                                    dragOffset = newOffset
                                }
                                Log.v(
                                    SEARCH_LOG_TAG,
                                    "Search sheet dragging: dragAmount=${'$'}dragAmount, accumulated=${'$'}dragOffset"
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 拖动指示器
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 标题
            Text(
                text = "搜索验证器",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索框
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索结果列表
            if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                // 无结果提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配项",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                // 结果列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(searchResults) { item ->
                        SearchResultItem(
                            item = item,
                            onClick = { handleResultClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索框组件
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = { newValue ->
            Log.d(
                SEARCH_LOG_TAG,
                "Search input changed from '" + query + "' to '" + newValue + "'"
            )
            onQueryChange(newValue)
        },
        modifier = modifier
            .fillMaxWidth(),
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 14.sp
        ),
        placeholder = {
            Text(
                text = "输入名称搜索...",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF374151),
            unfocusedContainerColor = Color(0xFF374151),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF60A5FA),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

/**
 * 搜索结果项组件
 */
@Composable
private fun SearchResultItem(
    item: TotpItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF374151))  // 纯色背景
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 发行者
                if (item.totpData.issuer.isNotBlank()) {
                    Text(
                        text = item.totpData.issuer,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 账户名
                if (item.totpData.accountName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.totpData.accountName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 右侧：验证码预览
            Text(
                text = formatCode(item.code),
                color = Color(0xFF60A5FA),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 格式化验证码
 */
private fun formatCode(code: String): String {
    return when {
        code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        code.length == 8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}
