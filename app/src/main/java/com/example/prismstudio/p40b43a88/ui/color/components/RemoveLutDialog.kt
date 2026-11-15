package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.windows.AlertDialog

/**
 * Confirmation of the `remove-lut` transition: the library is shared by every project and holds the only
 * copy the app keeps, so taking a LUT out is asked about first.
 */
@Composable
fun RemoveLutDialog(lutName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CountsAsOpenSheet()
    AlertDialog(
        properties = BackDismissSheet,
        onDismissRequest = onDismiss,
        title = { Text("从 LUT 库移除“$lutName”？") },
        content = {
            Text("所有项目里用到它的调色都会显示 LUT 缺失，画面不再套用它；重新导入同一个 .cube 文件即可恢复。")
        },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.error,
                    ),
                ) { Text("移除") }
            }
        },
    )
}
