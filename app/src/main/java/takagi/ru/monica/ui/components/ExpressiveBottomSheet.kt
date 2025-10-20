package takagi.ru.monica.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import kotlin.math.abs

/**
 * M3 Expressive 风格的 Bottom Sheet
 * 可上拉展开显示所有导航项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveBottomSheet(
    currentTab: BottomNavContentTab,
    visibleTabs: List<BottomNavContentTab>,
    allTabs: List<BottomNavContentTab>,
    onTabSelected: (BottomNavContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    // 底部栏高度
    val collapsedHeight = 80.dp
    val expandedHeight = 500.dp
    
    // 动画高度
    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) expandedHeight else collapsedHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "height"
    )
    
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 背景遮罩（展开时）
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isExpanded = false }
            )
        }
        
        // Bottom Sheet 内容
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 拖拽手柄区域 - 增强可见性和交互性
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    // 根据累计拖动距离决定展开或收起
                                    if (abs(dragOffset) > 50) {
                                        isExpanded = if (dragOffset < 0) true else false
                                    }
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        }
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    // 更明显的拖拽手柄：更大、更不透明
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    )
                }
                
                // 底部导航栏（可见项）
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    visibleTabs.forEach { tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = getTabIcon(tab),
                                    contentDescription = getTabLabel(tab)
                                )
                            },
                                label = {
                                    Text(
                                        text = getTabLabel(tab),
                                        fontSize = 12.sp
                                    )
                                },
                                selected = currentTab == tab,
                                onClick = {
                                    onTabSelected(tab)
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // 展开内容（所有项）- 只在展开时显示
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                    Text(
                        text = stringResource(R.string.all_navigation_items),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // 显示所有导航项（包括隐藏的）
                    allTabs.forEach { tab ->
                        val isVisible = tab in visibleTabs
                        val isSelected = currentTab == tab
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onTabSelected(tab)
                                    isExpanded = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            tonalElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getTabIcon(tab),
                                    contentDescription = getTabLabel(tab),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                    text = getTabLabel(tab),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (!isVisible) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            text = stringResource(R.string.hidden),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getTabIcon(tab: BottomNavContentTab): ImageVector {
    return when (tab) {
        BottomNavContentTab.PASSWORDS -> Icons.Default.Lock
        BottomNavContentTab.AUTHENTICATOR -> Icons.Default.Security
        BottomNavContentTab.DOCUMENTS -> Icons.Default.Description
        BottomNavContentTab.BANK_CARDS -> Icons.Default.CreditCard
        BottomNavContentTab.GENERATOR -> Icons.Default.AutoAwesome
    }
}

@Composable
private fun getTabLabel(tab: BottomNavContentTab): String {
    return when (tab) {
        BottomNavContentTab.PASSWORDS -> stringResource(R.string.nav_passwords)
        BottomNavContentTab.AUTHENTICATOR -> stringResource(R.string.nav_authenticator)
        BottomNavContentTab.DOCUMENTS -> stringResource(R.string.nav_documents)
        BottomNavContentTab.BANK_CARDS -> stringResource(R.string.nav_bank_cards)
        BottomNavContentTab.GENERATOR -> stringResource(R.string.nav_generator)
    }
}
