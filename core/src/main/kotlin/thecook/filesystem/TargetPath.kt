package thecook.filesystem

import java.util.Locale

internal fun requireRelativePath(value: String, description: String = "relative") {
    val parts = value.split('/')
    require(value.isNotEmpty() && !value.contains('\\') && !value.contains(':') &&
        value.none { it.code < 32 || it.code == 127 } &&
        parts.none { it.isEmpty() || it == "." || it == ".." }) {
        "Unsafe $description path: $value"
    }
}

data class TargetPath(val value: String) {
    init {
        requireRelativePath(value, "output")
        require(value.substringBefore('/').lowercase(Locale.ROOT) !in setOf(".cook", ".git")) {
            "Unsafe output path: $value"
        }
    }
    override fun toString(): String = value
    companion object {
        @JvmStatic fun of(value: String): TargetPath = TargetPath(value)
    }
}
