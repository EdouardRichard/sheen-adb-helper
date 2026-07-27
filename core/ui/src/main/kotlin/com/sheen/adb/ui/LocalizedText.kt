package com.sheen.adb.ui

enum class TextArgumentType {
    TEXT,
    NUMBER,
    VERBATIM,
}

data class TypedTextArgument(
    val name: String,
    val value: Any?,
    val type: TextArgumentType,
) {
    init {
        require(name.matches(Regex("[a-z][a-zA-Z0-9_]*"))) {
            "Text argument names must be stable semantic identifiers"
        }
        require(
            when (type) {
                TextArgumentType.TEXT,
                TextArgumentType.VERBATIM,
                -> value is String
                TextArgumentType.NUMBER -> value is Number
            },
        ) {
            "Text argument value does not match its declared type"
        }
    }
}

data class LocalizedTextRef(
    val owner: String,
    val semanticKey: String,
    val arguments: List<TypedTextArgument> = emptyList(),
) {
    init {
        require(owner.isNotBlank())
        require(semanticKey.isNotBlank())
        require(arguments.map(TypedTextArgument::name).distinct().size == arguments.size) {
            "Duplicate localized text argument"
        }
    }

    companion object {
        fun shared(
            key: V1SharedStringKey,
            vararg arguments: TypedTextArgument,
        ): LocalizedTextRef = LocalizedTextRef(
            owner = V1SharedStrings.OWNER,
            semanticKey = key.semanticKey,
            arguments = arguments.toList(),
        )
    }
}

sealed interface SafeVerbatimPolicy {
    val maxCodePoints: Int

    data class SingleLine(
        override val maxCodePoints: Int,
    ) : SafeVerbatimPolicy

    data class MultiLine(
        override val maxCodePoints: Int,
        val tabWidth: Int = 4,
    ) : SafeVerbatimPolicy {
        init {
            require(tabWidth in 1..8)
        }
    }
}

/**
 * Presentation-only projection. Device/user data must keep its original value
 * separately for identity, transfer, confirmation, export, or command use.
 */
data class SafeDisplayText internal constructor(
    val display: String,
    val truncated: Boolean,
    val replacementCount: Int,
)

object SafeVerbatimText {
    private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
    private const val POP_DIRECTIONAL_ISOLATE = '\u2069'
    private const val REPLACEMENT = '\uFFFD'
    private const val ELLIPSIS = '\u2026'

    fun render(raw: String, policy: SafeVerbatimPolicy): SafeDisplayText {
        require(policy.maxCodePoints > 0)
        var replacements = 0
        val normalized = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            val consumed = Character.charCount(codePoint)
            val isCrLf = codePoint == '\r'.code &&
                index + consumed < raw.length &&
                raw.codePointAt(index + consumed) == '\n'.code

            when {
                codePoint == '\r'.code || codePoint == '\n'.code -> {
                    if (policy is SafeVerbatimPolicy.MultiLine) {
                        normalized.append('\n')
                    } else {
                        normalized.append(' ')
                    }
                    if (isCrLf) index += Character.charCount('\n'.code)
                }
                codePoint == '\t'.code -> {
                    if (policy is SafeVerbatimPolicy.MultiLine) {
                        repeat(policy.tabWidth) { normalized.append(' ') }
                    } else {
                        normalized.append(' ')
                    }
                }
                isUnsafeControlOrBidi(codePoint) -> {
                    normalized.append(REPLACEMENT)
                    replacements += 1
                }
                else -> normalized.appendCodePoint(codePoint)
            }
            index += consumed
        }

        val available = normalized.codePointCount(0, normalized.length)
        val truncated = available > policy.maxCodePoints
        val bounded = if (truncated) {
            val contentLimit = (policy.maxCodePoints - 1).coerceAtLeast(0)
            val end = normalized.offsetByCodePoints(0, contentLimit)
            normalized.substring(0, end) + ELLIPSIS
        } else {
            normalized.toString()
        }
        return SafeDisplayText(
            display = "$LEFT_TO_RIGHT_ISOLATE$bounded$POP_DIRECTIONAL_ISOLATE",
            truncated = truncated,
            replacementCount = replacements,
        )
    }

    private fun isUnsafeControlOrBidi(codePoint: Int): Boolean =
        codePoint in 0x00..0x1F ||
            codePoint in 0x7F..0x9F ||
            codePoint in 0x202A..0x202E ||
            codePoint in 0x2066..0x2069
}
