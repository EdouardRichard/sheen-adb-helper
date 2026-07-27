package com.sheen.adb.ui

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class V1LocalizationPrimitivesTest {
    @Test
    fun `shared Chinese and English catalogs expose identical typed schemas`() {
        assertEquals(
            V1SharedStrings.requiredKeys(UiLanguage.ZH_CN),
            V1SharedStrings.requiredKeys(UiLanguage.EN_US),
        )
        V1SharedStrings.requiredKeys(UiLanguage.ZH_CN).forEach { key ->
            val schema = V1SharedStrings.placeholderSchema(UiLanguage.ZH_CN, key)
            assertEquals(
                schema,
                V1SharedStrings.placeholderSchema(UiLanguage.EN_US, key),
                "placeholder mismatch for $key",
            )
            val arguments = schema.map { (name, type) ->
                TypedTextArgument(
                    name = name,
                    value = when (type) {
                        TextArgumentType.NUMBER -> 1
                        TextArgumentType.TEXT -> "value"
                        TextArgumentType.VERBATIM -> "raw-value"
                    },
                    type = type,
                )
            }.toTypedArray()
            assertTrue(
                V1SharedStrings.resolve(
                    UiLanguage.ZH_CN,
                    LocalizedTextRef.shared(key, *arguments),
                ).isNotBlank(),
            )
            assertTrue(
                V1SharedStrings.resolve(
                    UiLanguage.EN_US,
                    LocalizedTextRef.shared(key, *arguments),
                ).isNotBlank(),
            )
        }
    }

    @Test
    fun `fixed technical labels are identical in both languages`() {
        val expected = listOf("all", "debug", "info", "error", "Esc", "Tab", "Ctrl", "Alt")

        assertEquals(V1SharedStrings.fixedTechnicalLabels(UiLanguage.ZH_CN), expected)
        assertEquals(V1SharedStrings.fixedTechnicalLabels(UiLanguage.EN_US), expected)
    }

    @Test
    fun `single line safe rendering is bounded marked and bidi isolated without mutating raw`() {
        val raw = "prefix\u0000\u0085\u202E\uFFFDbidi\nnext\tcolumn-" + "x".repeat(80)
        val original = raw.toCharArray().copyOf()

        val rendered = SafeVerbatimText.render(
            raw = raw,
            policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 40),
        )

        assertEquals(raw.toCharArray().toList(), original.toList(), "raw technical value changed")
        assertTrue(rendered.display.startsWith("\u2066"), "missing leading bidi isolate")
        assertTrue(rendered.display.endsWith("\u2069"), "missing trailing bidi isolate")
        assertFalse(rendered.display.contains('\u0000'))
        assertFalse(rendered.display.contains('\u0085'))
        assertFalse(rendered.display.contains('\u202E'))
        assertFalse(rendered.display.contains('\n'))
        assertFalse(rendered.display.contains('\t'))
        assertTrue(rendered.display.contains('\uFFFD'), "replacement marker must remain explicit")
        assertTrue(rendered.truncated)
        assertTrue(rendered.replacementCount >= 3)
    }

    @Test
    fun `multiline safe rendering preserves line boundaries but normalizes tabs and controls`() {
        val rendered = SafeVerbatimText.render(
            raw = "first\r\nsecond\tvalue\u0007",
            policy = SafeVerbatimPolicy.MultiLine(maxCodePoints = 80, tabWidth = 4),
        )

        assertTrue(rendered.display.contains("first\nsecond    value"))
        assertFalse(rendered.display.contains('\r'))
        assertFalse(rendered.display.contains('\t'))
        assertFalse(rendered.display.contains('\u0007'))
    }

    @Test
    fun `safe display type is presentation only and cannot be treated as command text`() {
        val rendered = SafeVerbatimText.render(
            raw = "echo presentation-only",
            policy = SafeVerbatimPolicy.SingleLine(maxCodePoints = 80),
        )

        assertFalse(rendered is CharSequence)
        assertFalse(
            SafeDisplayText::class.java.methods.any {
                it.name.contains("command", ignoreCase = true) ||
                    it.name.contains("execute", ignoreCase = true)
            },
        )
    }
}
