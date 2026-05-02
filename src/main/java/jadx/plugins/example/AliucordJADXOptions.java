package jadx.plugins.example;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AliucordJADXOptions extends BasePluginOptionsBuilder {

	private CodegenLanguage codegenLanguage;
	private boolean useFullName;

	@Override
	public void registerOptions() {
		enumOption(AliucordJADX.PLUGIN_ID + ".codegen", CodegenLanguage.values(), CodegenLanguage::valueOf)
				.description("snippet code language")
				.defaultValue(CodegenLanguage.KOTLIN)
				.setter(v -> codegenLanguage = v);

		boolOption(AliucordJADX.PLUGIN_ID + ".fullName")
				.description("use full name for identifiers")
				.defaultValue(true)
				.setter(v -> useFullName = v);
	}

	public CodegenLanguage getCodegenLanguage() {
		return codegenLanguage;
	}
	public boolean useFullName() { return useFullName; }
}
