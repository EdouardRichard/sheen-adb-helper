package com.sheen.adb.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small, local vector set matching the Material Symbols semantics used by the
 * reviewed HTML. Keeping the paths in source avoids a runtime font/CDN.
 */
object SheenIcons {
    val Menu: ImageVector by lazy { icon("Menu") {
        rect(3f, 5f, 21f, 7f); rect(3f, 11f, 21f, 13f); rect(3f, 17f, 21f, 19f)
    } }
    val Close: ImageVector by lazy { icon("Close") {
        polygon(5.4f, 4f, 12f, 10.6f, 18.6f, 4f, 20f, 5.4f, 13.4f, 12f, 20f, 18.6f, 18.6f, 20f, 12f, 13.4f, 5.4f, 20f, 4f, 18.6f, 10.6f, 12f, 4f, 5.4f)
    } }
    val Link: ImageVector by lazy { outlineIcon("Link") {
        moveTo(10f, 13f); lineTo(14f, 9f)
        moveTo(8.5f, 15.5f); lineTo(6.8f, 17.2f)
        curveTo(5.3f, 18.7f, 2.8f, 18.7f, 1.3f, 17.2f)
        curveTo(-.2f, 15.7f, -.2f, 13.2f, 1.3f, 11.7f)
        lineTo(5f, 8f)
        curveTo(6.5f, 6.5f, 9f, 6.5f, 10.5f, 8f)
        moveTo(13.5f, 16f)
        curveTo(15f, 17.5f, 17.5f, 17.5f, 19f, 16f)
        lineTo(22.7f, 12.3f)
        curveTo(24.2f, 10.8f, 24.2f, 8.3f, 22.7f, 6.8f)
        curveTo(21.2f, 5.3f, 18.7f, 5.3f, 17.2f, 6.8f)
        lineTo(15.5f, 8.5f)
    } }
    val LinkOff: ImageVector by lazy { outlineIcon("LinkOff") {
        moveTo(7f, 17f); lineTo(5f, 19f)
        curveTo(3.3f, 20.7f, .6f, 20.7f, -1.1f, 19f)
        moveTo(3f, 11f); lineTo(6f, 8f)
        curveTo(7.2f, 6.8f, 9f, 6.4f, 10.5f, 7f)
        moveTo(13.5f, 17f)
        curveTo(15f, 17.6f, 16.8f, 17.2f, 18f, 16f)
        lineTo(21f, 13f)
        moveTo(15f, 7f); lineTo(17f, 5f)
        curveTo(18.7f, 3.3f, 21.4f, 3.3f, 23.1f, 5f)
        moveTo(3f, 3f); lineTo(21f, 21f)
    } }
    val Phone: ImageVector by lazy { outlineIcon("Phone") {
        roundedRect(7f, 2f, 17f, 22f, 2f)
        moveTo(10f, 5f); lineTo(14f, 5f)
        moveTo(11f, 19f); lineTo(13f, 19f)
    } }
    val Wireless: ImageVector by lazy { icon("Wireless") {
        ringArc(12f, 12f, 9f, 2f); ringArc(12f, 12f, 6f, 2f); circle(12f, 16f, 1.8f)
    } }
    val QrCodeScanner: ImageVector by lazy { icon("QrCodeScanner") {
        rect(3f, 3f, 9f, 5f); rect(3f, 3f, 5f, 9f)
        rect(15f, 3f, 21f, 5f); rect(19f, 3f, 21f, 9f)
        rect(3f, 19f, 9f, 21f); rect(3f, 15f, 5f, 21f)
        rect(15f, 19f, 21f, 21f); rect(19f, 15f, 21f, 21f)
        rect(8f, 8f, 11f, 11f); rect(13f, 8f, 16f, 11f)
        rect(8f, 13f, 11f, 16f); rect(13f, 13f, 16f, 16f)
    } }
    val PairingCode: ImageVector by lazy { icon("PairingCode") {
        ringArc(8f, 12f, 5f, 2f); ringArc(16f, 12f, 5f, 2f)
        rect(7f, 11f, 17f, 13f)
        rect(3f, 3f, 5f, 7f); rect(19f, 17f, 21f, 21f)
    } }
    val CellTower: ImageVector by lazy { icon("CellTower") {
        polygon(10f, 21f, 12f, 5f, 14f, 21f, 12f, 17f)
        circle(12f, 5f, 2f)
        polygon(7f, 7f, 5f, 5f, 3f, 8f, 3f, 12f, 5f, 15f, 7f, 13f, 5f, 11f, 5f, 9f)
        polygon(17f, 7f, 19f, 5f, 21f, 8f, 21f, 12f, 19f, 15f, 17f, 13f, 19f, 11f, 19f, 9f)
    } }
    val Remote: ImageVector by lazy { outlineIcon("Remote") {
        roundedRect(8f, 4f, 16f, 22f, 2f)
        moveTo(11f, 18f); lineTo(13f, 18f)
        moveTo(6f, 7f); curveTo(4f, 9f, 4f, 12f, 6f, 14f)
        moveTo(18f, 7f); curveTo(20f, 9f, 20f, 12f, 18f, 14f)
    } }
    val Folder: ImageVector by lazy { icon("Folder") {
        polygon(2f, 6f, 9f, 6f, 11f, 8f, 22f, 8f, 22f, 20f, 2f, 20f)
    } }
    val Apps: ImageVector by lazy { gridIcon("Apps", 3) }
    val Processes: ImageVector by lazy { icon("Processes") {
        repeat(3) { row -> circle(5f, 6f + row * 6f, 1.4f); rect(9f, 5f + row * 6f, 21f, 7f + row * 6f) }
    } }
    val Terminal: ImageVector by lazy { icon("Terminal") {
        roundedRect(2f, 4f, 22f, 20f, 2f); polygon(6f, 8f, 10f, 12f, 6f, 16f, 7.5f, 17.5f, 13f, 12f, 7.5f, 6.5f); rect(13f, 15f, 19f, 17f)
    } }
    val BugReport: ImageVector by lazy { icon("BugReport") {
        roundedRect(6f, 6f, 18f, 20f, 3f); circle(10f, 11f, 1f); circle(14f, 11f, 1f)
        rect(9f, 15f, 15f, 17f); rect(2f, 9f, 6f, 11f); rect(18f, 9f, 22f, 11f); rect(2f, 15f, 6f, 17f); rect(18f, 15f, 22f, 17f)
    } }
    val Screenshot: ImageVector by lazy { icon("Screenshot") {
        rect(3f, 3f, 10f, 5f); rect(3f, 3f, 5f, 10f); rect(14f, 3f, 21f, 5f); rect(19f, 3f, 21f, 10f)
        rect(3f, 19f, 10f, 21f); rect(3f, 14f, 5f, 21f); rect(14f, 19f, 21f, 21f); rect(19f, 14f, 21f, 21f)
        circle(12f, 12f, 3.5f)
    } }
    val ScreenRecord: ImageVector by lazy { icon("ScreenRecord") {
        ringArc(12f, 12f, 9f, 2f); circle(12f, 12f, 4f)
    } }
    val Power: ImageVector by lazy { icon("Power") {
        rect(11f, 2f, 13f, 13f); ringArc(12f, 12f, 9f, 2.4f)
    } }
    val Settings: ImageVector by lazy { icon("Settings") {
        ringArc(12f, 12f, 8f, 3f); circle(12f, 12f, 3f)
    } }
    val Info: ImageVector by lazy { icon("Info") {
        ringArc(12f, 12f, 10f, 2f); circle(12f, 7f, 1.2f); rect(11f, 10f, 13f, 18f)
    } }
    val Adb: ImageVector by lazy { icon("Adb") {
        roundedRect(5f, 7f, 19f, 20f, 3f); circle(9f, 12f, 1f); circle(15f, 12f, 1f)
        rect(7f, 4f, 9f, 8f); rect(15f, 4f, 17f, 8f)
    } }
    val Memory: ImageVector by lazy { icon("Memory") {
        roundedRect(5f, 5f, 19f, 19f, 2f); roundedRect(9f, 9f, 15f, 15f, 1f)
        repeat(4) { i -> rect(7f + i * 3f, 2f, 8f + i * 3f, 5f); rect(7f + i * 3f, 19f, 8f + i * 3f, 22f) }
    } }
    val Cpu: ImageVector by lazy { outlineIcon("Cpu") {
        roundedRect(6f, 6f, 18f, 18f, 2f); circle(12f, 12f, 3f)
        repeat(3) { index ->
            val offset = 8f + index * 4f
            moveTo(offset, 3f); lineTo(offset, 6f)
            moveTo(offset, 18f); lineTo(offset, 21f)
            moveTo(3f, offset); lineTo(6f, offset)
            moveTo(18f, offset); lineTo(21f, offset)
        }
    } }
    val MemoryAlt: ImageVector by lazy { outlineIcon("MemoryAlt") {
        roundedRect(5f, 6f, 19f, 18f, 2f)
        repeat(4) { index ->
            val offset = 7f + index * 3.25f
            moveTo(offset, 3f); lineTo(offset, 6f)
            moveTo(offset, 18f); lineTo(offset, 21f)
        }
        moveTo(9f, 9f); lineTo(9f, 15f)
        moveTo(12f, 9f); lineTo(12f, 15f)
        moveTo(15f, 9f); lineTo(15f, 15f)
    } }
    val Storage: ImageVector by lazy { outlineIcon("Storage") {
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(20f, 6f)
        lineTo(20f, 21f); lineTo(4f, 21f); lineTo(4f, 6f); close()
        moveTo(8f, 3f); lineTo(8f, 9f); lineTo(16f, 9f); lineTo(16f, 3f)
        moveTo(8f, 16f); lineTo(16f, 16f)
    } }
    val Battery: ImageVector by lazy { icon("Battery") {
        roundedRect(6f, 4f, 18f, 22f, 2f); rect(9f, 2f, 15f, 4f); rect(9f, 14f, 15f, 19f)
    } }
    val BatteryCharging: ImageVector by lazy { outlineIcon("BatteryCharging") {
        roundedRect(7f, 4f, 17f, 22f, 2f)
        moveTo(10f, 2f); lineTo(14f, 2f)
        moveTo(13f, 7f); lineTo(10f, 13f); lineTo(13f, 13f); lineTo(11f, 19f)
    } }
    val Temperature: ImageVector by lazy { icon("Temperature") {
        roundedRect(9f, 2f, 15f, 17f, 3f); circle(12f, 18f, 4f); rect(11f, 7f, 13f, 18f)
    } }

    private fun gridIcon(name: String, columns: Int): ImageVector = icon(name) {
        val cell = 4f
        val gap = 2f
        repeat(columns) { row ->
            repeat(columns) { column ->
                val left = 3f + column * (cell + gap)
                val top = 3f + row * (cell + gap)
                roundedRect(left, top, left + cell, top + cell, 1f)
            }
        }
    }

    private fun icon(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) { content() }
        }.build()

    private fun outlineIcon(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) { content() }
        }.build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.rect(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top); lineTo(right, top); lineTo(right, bottom); lineTo(left, bottom); close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        moveTo(left + radius, top); lineTo(right - radius, top); curveTo(right, top, right, top, right, top + radius)
        lineTo(right, bottom - radius); curveTo(right, bottom, right, bottom, right - radius, bottom)
        lineTo(left + radius, bottom); curveTo(left, bottom, left, bottom, left, bottom - radius)
        lineTo(left, top + radius); curveTo(left, top, left, top, left + radius, top); close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
        moveTo(cx + radius, cy)
        curveTo(cx + radius, cy + radius * .552f, cx + radius * .552f, cy + radius, cx, cy + radius)
        curveTo(cx - radius * .552f, cy + radius, cx - radius, cy + radius * .552f, cx - radius, cy)
        curveTo(cx - radius, cy - radius * .552f, cx - radius * .552f, cy - radius, cx, cy - radius)
        curveTo(cx + radius * .552f, cy - radius, cx + radius, cy - radius * .552f, cx + radius, cy)
        close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.ringArc(cx: Float, cy: Float, radius: Float, thickness: Float) {
        circle(cx, cy, radius)
        // The inner contour is reversed so the non-zero fill rule leaves a ring.
        val inner = (radius - thickness).coerceAtLeast(0.5f)
        moveTo(cx + inner, cy)
        curveTo(cx + inner, cy - inner * .552f, cx + inner * .552f, cy - inner, cx, cy - inner)
        curveTo(cx - inner * .552f, cy - inner, cx - inner, cy - inner * .552f, cx - inner, cy)
        curveTo(cx - inner, cy + inner * .552f, cx - inner * .552f, cy + inner, cx, cy + inner)
        curveTo(cx + inner * .552f, cy + inner, cx + inner, cy + inner * .552f, cx + inner, cy)
        close()
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.polygon(vararg points: Float) {
        if (points.size < 4 || points.size % 2 != 0) return
        moveTo(points[0], points[1])
        var index = 2
        while (index < points.size) {
            lineTo(points[index], points[index + 1])
            index += 2
        }
        close()
    }
}
