package com.github.canny1913

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder

class AliucordJADXOptions: BasePluginOptionsBuilder() {
	var useFullName: Boolean = true
		private set
	lateinit var codegenLanguage: CodegenLanguage
		private set

	override fun registerOptions() {
		enumOption(AliucordJADX.PLUGIN_ID + ".codegen", CodegenLanguage.entries.toTypedArray(), CodegenLanguage::valueOf)
			.description("snippet code language")
			.defaultValue(CodegenLanguage.KOTLIN)
			.setter { codegenLanguage = it }

		boolOption(AliucordJADX.PLUGIN_ID + ".fullName")
			.description("use full name for identifiers")
			.defaultValue(true)
			.setter { useFullName = it }
	}
}
