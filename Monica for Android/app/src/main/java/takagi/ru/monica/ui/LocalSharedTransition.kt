package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 减少动画模式 - 用于解决部分设备（如 HyperOS 2/Android 15）的动画卡顿问题
 * 当为 true 时，禁用共享元素动画，使用简单的淡入淡出效果
 */
val LocalReduceAnimations = staticCompositionLocalOf { false }
