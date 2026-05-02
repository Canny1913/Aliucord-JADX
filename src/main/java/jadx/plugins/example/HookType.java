package jadx.plugins.example;

public enum HookType {
	BEFORE,
	AFTER,
	INSTEAD;

	public String asString(CodegenLanguage codegen) {
		switch (codegen) {
			case JAVA: switch (this) {
				case BEFORE:
					return "PreHook";
				case AFTER:
					return "Hook";
				case INSTEAD:
					return "InsteadHook";
			}
			case KOTLIN: return name().toLowerCase();
			default: return null;
		}
	}
}
