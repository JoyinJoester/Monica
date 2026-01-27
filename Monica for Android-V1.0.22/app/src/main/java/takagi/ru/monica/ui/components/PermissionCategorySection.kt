package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.model.PermissionCategory
import takagi.ru.monica.data.model.PermissionInfo

/**
 * 权限分类区域组件
 * Permission category section component
 */
@Composable
fun PermissionCategorySection(
    category: PermissionCategory,
    permissions: List<PermissionInfo>,
    onPermissionClick: (PermissionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 分类标题
        Text(
            text = stringResource(category.titleResId),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 权限列表
        permissions.forEach { permission ->
            PermissionCard(
                permission = permission,
                onClick = { onPermissionClick(permission) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
