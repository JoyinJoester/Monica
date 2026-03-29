package takagi.ru.monica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import takagi.ru.monica.data.PasswordListTopModule

internal data class PasswordCategoryMenuModuleDragState(
    val moduleOrder: List<PasswordListTopModule>,
    val onModuleOrderChange: (List<PasswordListTopModule>) -> Unit,
    val draggingModule: PasswordListTopModule?,
    val onDraggingModuleChange: (PasswordListTopModule?) -> Unit,
    val settlingModule: PasswordListTopModule?,
    val onSettlingModuleChange: (PasswordListTopModule?) -> Unit,
    val moduleDragOffset: Offset,
    val onModuleDragOffsetChange: (Offset) -> Unit,
    val moduleReorderEpoch: Int,
    val onModuleReorderEpochChange: (Int) -> Unit,
    val moduleSettleOffset: Animatable<Offset, AnimationVector2D>,
    val modulePlacementOffsets: MutableMap<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>,
    val previousModuleBounds: MutableMap<PasswordListTopModule, Rect>,
    val lastModuleAnimatedEpoch: MutableMap<PasswordListTopModule, Int>,
    val moduleBounds: MutableMap<PasswordListTopModule, Rect>
)

@Composable
internal fun rememberCategoryMenuModuleDragState(
    topModulesOrder: List<PasswordListTopModule>
): PasswordCategoryMenuModuleDragState {
    var moduleOrder by remember(topModulesOrder) {
        mutableStateOf(PasswordListTopModule.sanitizeOrder(topModulesOrder))
    }
    var draggingModule by remember { mutableStateOf<PasswordListTopModule?>(null) }
    var settlingModule by remember { mutableStateOf<PasswordListTopModule?>(null) }
    var moduleDragOffset by remember { mutableStateOf(Offset.Zero) }
    var moduleReorderEpoch by remember { mutableIntStateOf(0) }
    val moduleSettleOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val modulePlacementOffsets = remember {
        mutableMapOf<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>()
    }
    val previousModuleBounds = remember { mutableMapOf<PasswordListTopModule, Rect>() }
    val lastModuleAnimatedEpoch = remember { mutableMapOf<PasswordListTopModule, Int>() }
    val moduleBounds = remember { mutableMapOf<PasswordListTopModule, Rect>() }

    return PasswordCategoryMenuModuleDragState(
        moduleOrder = moduleOrder,
        onModuleOrderChange = { moduleOrder = it },
        draggingModule = draggingModule,
        onDraggingModuleChange = { draggingModule = it },
        settlingModule = settlingModule,
        onSettlingModuleChange = { settlingModule = it },
        moduleDragOffset = moduleDragOffset,
        onModuleDragOffsetChange = { moduleDragOffset = it },
        moduleReorderEpoch = moduleReorderEpoch,
        onModuleReorderEpochChange = { moduleReorderEpoch = it },
        moduleSettleOffset = moduleSettleOffset,
        modulePlacementOffsets = modulePlacementOffsets,
        previousModuleBounds = previousModuleBounds,
        lastModuleAnimatedEpoch = lastModuleAnimatedEpoch,
        moduleBounds = moduleBounds
    )
}
