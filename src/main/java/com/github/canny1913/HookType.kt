package com.github.canny1913

enum class HookType {
	BEFORE,
	AFTER,
	INSTEAD;

	fun asString(codegen: CodegenLanguage): String {
		return when (codegen) {
			CodegenLanguage.JAVA -> {
				when (this) {
					BEFORE -> "PreHook"
					AFTER -> "Hook"
					INSTEAD -> "InsteadHook"
				}
			}
			CodegenLanguage.KOTLIN -> this.name.lowercase()

		}
	}
}
