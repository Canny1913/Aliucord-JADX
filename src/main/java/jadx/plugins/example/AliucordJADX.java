package jadx.plugins.example;

import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import jadx.api.metadata.ICodeNodeRef;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.plugins.gui.JadxGuiContext;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.utils.exceptions.JadxRuntimeException;

public class AliucordJADX implements JadxPlugin {
	public static final String PLUGIN_ID = "aliucord-jadx";

	private final AliucordJADXOptions options = new AliucordJADXOptions();

	Function<ICodeNodeRef, Boolean> isHookActionEnabled = (node) -> node instanceof MethodNode;
	Function<ICodeNodeRef, Boolean> isFieldActionEnabled = (node) -> node instanceof FieldNode && options.getCodegenLanguage() == CodegenLanguage.KOTLIN;

	private static final String KOTLIN_CONSTRUCTOR_TEMPLATE = "patcher.%1$s<%2$s>(%3$s) { param -> \n // code \n }";
	private static final String KOTLIN_METHOD_TEMPLATE = "patcher.%1$s<%2$s>(%3$s%4$s) { param -> \n // code \n }";
	private static final String KOTLIN_FIELD_TEMPLATE = "var %1$s.exampleField by accessField<%2$s>(%3$s)";
	private static final String JAVA_CONSTRUCTOR_TEMPLATE = "patcher.patch(%2$s.getDeclaredConstructor(%3$s), new %1$s(param -> { \n // code \n }))";
	private static final String JAVA_METHOD_TEMPLATE = "patcher.patch(%2$s.class.getDeclaredMethod(\"%3$s\", %4$s), new %1$s(param -> { \n // code \n }))";

	private final Map<String, String> PRIMITIVE_TYPE_MAPPING = Map.of("int", "Int", "byte", "Byte", "short", "Short", "long", "Long", "float", "Float", "double", "Double", "char", "Char", "boolean", "Boolean");
	private final Map<String, String> BOXED_TYPE_MAPPING = Map.of("java.lang.Integer", "Int", "java.lang.Byte", "Byte", "java.lang.Short", "Short", "java.lang.Long", "Long", "java.lang.Float", "Float", "java.lang.Double", "Double", "java.lang.Character", "Char", "java.lang.Boolean", "Boolean", "java.lang.Object", "Any");

	@Override
	public JadxPluginInfo getPluginInfo() {
		return JadxPluginInfoBuilder.pluginId(PLUGIN_ID).name("Aliucord-JADX").description("Aliucord helper plugin").homepage("https://github.com/Canny1913/Aliucord-JADX").requiredJadxVersion("1.5.1, r2333").build();
	}

	@Override
	public void init(JadxPluginContext context) {
		context.registerOptions(options);
		var guiContext = context.getGuiContext();
		if (guiContext != null) {
			initGui(guiContext);
		}
	}

	public void initGui(JadxGuiContext context) {
		context.addPopupMenuAction(
				"Copy Aliucord hook snippet (before)",
				isHookActionEnabled, null,
				(nodeRef) -> snippetAction(context, nodeRef, HookType.BEFORE)
		);
		context.addPopupMenuAction(
				"Copy Aliucord hook snippet (after)",
				isHookActionEnabled, null,
				(nodeRef) -> snippetAction(context, nodeRef, HookType.AFTER)
		);
		context.addPopupMenuAction(
				"Copy Aliucord hook snippet (instead)",
				isHookActionEnabled, null,
				(nodeRef) -> snippetAction(context, nodeRef, HookType.INSTEAD)
		);
		context.addPopupMenuAction(
				"Copy Aliucord accessor snippet (field)",
				isFieldActionEnabled, null,
				(nodeRef) -> snippetAction(context, nodeRef, null)
		);
	}

	public void snippetAction(JadxGuiContext context, ICodeNodeRef nodeRef, HookType hookType) {
		try {
			var snippet = copySnippet(nodeRef, hookType);
			context.copyToClipboard(snippet);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(context.getMainFrame(), e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	public String copySnippet(ICodeNodeRef node, HookType hookType) {
		if (node instanceof MethodNode) {
			return copyMethodSnippet((MethodNode) node, hookType);
		} else if (node instanceof FieldNode) {
			return copyFieldSnippet((FieldNode) node);
		} else {
			throw new JadxRuntimeException("Unsupported node type.");
		}
	}


	public String copyMethodSnippet(MethodNode node, HookType hookType) {
		var language = options.getCodegenLanguage();
		var method = node.getMethodInfo();
		var args = node.getArgTypes().stream().map(this::fixType);
		var clazz = node.getDeclaringClass();
		var methodName = method.getName();
		var hookTypeStr = hookType.asString(language);
		String className;

		if (options.useFullName()) {
			className = clazz.getRawName();
		} else {
			className = clazz.getName();
		}
		if (language == CodegenLanguage.JAVA) {
			className = String.format("%s.class", className);
		}
		if (language == CodegenLanguage.KOTLIN) {
			if (className.contains("$")) className = String.format("`%s`", className);
			if (methodName.contains("$")) {
				methodName = String.format("$$\"%s\"", methodName);
			} else {
				methodName = String.format("\"%s\"", methodName);
			}
		}
		if (node.getAccessFlags().isAbstract()) {
			throw new JadxRuntimeException("Cannot create a snippet of abstract method.");
		}

		String constructorTemplate;
		String methodTemplate;
		if (language == CodegenLanguage.KOTLIN) {
			constructorTemplate = KOTLIN_CONSTRUCTOR_TEMPLATE;
			methodTemplate = KOTLIN_METHOD_TEMPLATE;
		} else {
			constructorTemplate = JAVA_CONSTRUCTOR_TEMPLATE;
			methodTemplate = JAVA_METHOD_TEMPLATE;
		}

		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.ROOT);
		if (method.isConstructor()) {
			formatter.format(constructorTemplate, hookTypeStr, className, args.collect(Collectors.joining(",\n", ",\n", "")));
		} else {
			formatter.format(methodTemplate, hookTypeStr, className, methodName, args.collect(Collectors.joining(",\n", ",\n", "")));
		}
		return sb.toString();
	}

	public String copyFieldSnippet(FieldNode node) {
		var language = options.getCodegenLanguage();

		var field = node.getFieldInfo();
		var fieldType = getFieldType(node.getType());
		var clazz = node.getDeclaringClass();
		var fieldName = field.getName();
		String className;
		if (options.useFullName()) {
			className = clazz.getRawName();
		} else {
			className = clazz.getName();
		}

		if (language == CodegenLanguage.KOTLIN) {
			if (className.contains("$")) className = String.format("`%s`", className);
			if (fieldName.contains("$")) {
				fieldName = String.format("$$\"%s\"", fieldName);
			} else {
				fieldName = String.format("\"%s\"", fieldName);
			}
		}
		if (node.getAccessFlags().isAbstract()) {
			throw new JadxRuntimeException("Cannot create a getter snippet of abstract field.");
		}
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.ROOT);
		formatter.format(KOTLIN_FIELD_TEMPLATE, className, fieldType, fieldName);
		return sb.toString();
	}

	public String getFieldType(ArgType type) {
		var language = options.getCodegenLanguage();
		if (language == CodegenLanguage.KOTLIN) {
			if (type.canBeArray()) {
				var arrayElementType = type.getArrayElement();
				return String.format("Array<%s>", processTypeName(arrayElementType.toString(), false));
			}
			if (type.isGenericType() && type.isObject() && type.isTypeKnown()) {
				return processTypeName("java.lang.Object", false);
			}
			if (type.isGeneric() && type.isObject()) {
				var generics = type.getGenericTypes().stream().map((a) -> "*").collect(Collectors.joining(","));
				return String.format("%s<%s>", processTypeName(type.getObject(), false), generics);
			}
			if (type.isPrimitive()) {
				var pt = type.getPrimitiveType();
				return PRIMITIVE_TYPE_MAPPING.get(pt.toString());
			}
		}
		return processTypeName(type.toString(), false);
	}

	public String fixType(ArgType type) {
		var language = options.getCodegenLanguage();

		if (type.isGeneric() && type.isObject()) {
			if (type.getInnerType() != null) {
				return processTypeName(type.toString(), true);
			}
			return processTypeName(type.getObject(), true);
		}
		if (type.isGenericType() && type.isObject() && type.isTypeKnown()) {
			return processTypeName("java.lang.Object", true);
		}
		if (type.isPrimitive()) {
			if (language == CodegenLanguage.JAVA) {
				return String.format("%s.class", type);
			} else if (language == CodegenLanguage.KOTLIN) {
				var pt = type.getPrimitiveType();
				switch (pt) {
					case BOOLEAN:
						return "Boolean::class.javaPrimitiveType!!";
					case CHAR:
						return "Char::class.javaPrimitiveType!!";
					case BYTE:
						return "Byte::class.javaPrimitiveType!!";
					case SHORT:
						return "Short::class.javaPrimitiveType!!";
					case INT:
						return "Int::class.javaPrimitiveType!!";
					case FLOAT:
						return "Float::class.javaPrimitiveType!!";
					case LONG:
						return "Long::class.javaPrimitiveType!!";
					case DOUBLE:
						return "Double::class.javaPrimitiveType!!";
					case OBJECT:
						return "Any::class.java!!";
					case ARRAY:
						return "Array::class.java!!";
					case VOID:
						return "Void::class.javaPrimitiveType!!";
					default:
						throw new JadxRuntimeException("Unknown or null primitive type: " + type);
				}
			}
		}
		return processTypeName(type.toString(), true);
	}

	public String processTypeName(String type, boolean asClass) {
		var useFullName = options.useFullName();
		var language = options.getCodegenLanguage();
		String newType = type;
		if (!useFullName) {
			var lastIndex = newType.lastIndexOf(".");
			if (lastIndex != -1) newType = type.substring(lastIndex + 1);
		}
		if (asClass) {
			switch (language) {
				case KOTLIN:
					if (BOXED_TYPE_MAPPING.containsKey(type)) {
						return String.format("%s::class.javaObjectType", BOXED_TYPE_MAPPING.get(type));
					} else {
						return String.format("%s::class.java", newType);
					}
				case JAVA:
					return String.format("%s.class", newType);
			}
		}
		return newType;
	}
}
