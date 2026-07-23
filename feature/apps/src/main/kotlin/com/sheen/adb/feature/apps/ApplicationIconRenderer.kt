package com.sheen.adb.feature.apps

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sheen.adb.core.ApplicationIconPayload
import com.sheen.adb.core.ApplicationMetadataStatus
import com.sheen.adb.core.RemoteApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface AppsIconPresentation {
    data object Placeholder : AppsIconPresentation
    data class Encoded(val payload: ApplicationIconPayload) : AppsIconPresentation
}

internal data class AppsApplicationPresentation(
    val primaryLabel: String,
    val packageLabel: String?,
    val metadataMessage: String?,
    val icon: AppsIconPresentation,
)

internal object AppsPresentation {
    const val MAX_DISPLAY_NAME_CHARS = 120
    private const val MAX_ICON_BYTES = 1024 * 1024
    private const val MAX_ICON_DIMENSION = 1024

    fun present(
        application: RemoteApplication,
        metadata: AppsApplicationMetadata?,
    ): AppsApplicationPresentation {
        val safeName = metadata?.displayName
            ?.filterNot(::isBidirectionalControl)
            ?.trim()
            ?.take(MAX_DISPLAY_NAME_CHARS)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val icon = metadata?.icon?.takeIf(::isBoundedIcon)?.let(AppsIconPresentation::Encoded)
            ?: AppsIconPresentation.Placeholder
        return AppsApplicationPresentation(
            primaryLabel = safeName ?: application.packageName,
            packageLabel = application.packageName.takeIf { safeName != null },
            metadataMessage = metadataStatusMessage(metadata?.status ?: ApplicationMetadataStatus.PENDING),
            icon = icon,
        )
    }

    private fun isBoundedIcon(icon: ApplicationIconPayload): Boolean =
        icon.width in 1..MAX_ICON_DIMENSION &&
            icon.height in 1..MAX_ICON_DIMENSION &&
            icon.encodedBytes.size in 1..MAX_ICON_BYTES

    private fun metadataStatusMessage(status: ApplicationMetadataStatus): String? = when (status) {
        ApplicationMetadataStatus.PENDING -> "正在读取名称和图标"
        ApplicationMetadataStatus.AVAILABLE -> null
        ApplicationMetadataStatus.UNAVAILABLE -> "名称或图标不可用"
        ApplicationMetadataStatus.TOO_LARGE -> "应用信息超过安全上限"
        ApplicationMetadataStatus.PARSE_FAILED -> "应用信息解析失败"
        ApplicationMetadataStatus.SESSION_CHANGED -> "应用信息已随连接失效"
        ApplicationMetadataStatus.TIMED_OUT -> "应用信息读取超时"
    }

    private fun isBidirectionalControl(character: Char): Boolean = character.code in BIDI_CONTROL_RANGES

    private val BIDI_CONTROL_RANGES = setOf(
        0x061c,
        0x200e,
        0x200f,
        0x202a,
        0x202b,
        0x202c,
        0x202d,
        0x202e,
        0x2066,
        0x2067,
        0x2068,
        0x2069,
    )
}

@Composable
internal fun ApplicationIconRenderer(
    icon: AppsIconPresentation,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    when (icon) {
        AppsIconPresentation.Placeholder -> ApplicationIconPlaceholder(modifier)
        is AppsIconPresentation.Encoded -> {
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null,
                key1 = icon.payload,
            ) {
                value = withContext(Dispatchers.Default) { decodeBoundedIcon(icon.payload) }
            }
            val decoded = image
            if (decoded == null) {
                ApplicationIconPlaceholder(modifier)
            } else {
                Image(
                    bitmap = decoded,
                    contentDescription = contentDescription,
                    modifier = modifier.size(48.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun ApplicationIconPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text("应用", style = MaterialTheme.typography.labelSmall)
    }
}

private fun decodeBoundedIcon(payload: ApplicationIconPayload): androidx.compose.ui.graphics.ImageBitmap? {
    val bytes = payload.encodedBytes
    if (bytes.isEmpty() || bytes.size > 1024 * 1024) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != payload.width || bounds.outHeight != payload.height) return null
    if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
    if (bounds.outWidth.toLong() * bounds.outHeight > 1024L * 1024) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
