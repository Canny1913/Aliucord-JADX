package com.github.canny1913

import jadx.api.metadata.ICodeNodeRef
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.gui.JadxGuiContext
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxRuntimeException
import javax.swing.JOptionPane

class AliucordJADX : JadxPlugin {
	val options = AliucordJADXOptions()
	override fun getPluginInfo(): JadxPluginInfo? {
		return JadxPluginInfoBuilder.pluginId(PLUGIN_ID).name("Aliucord-JADX")
			.description("Aliucord helper plugin")
			.homepage("https://github.com/Canny1913/Aliucord-JADX")
			.requiredJadxVersion("1.5.1, r2333").build()
	}

	fun isHookActionEnabled(node: ICodeNodeRef) = node is MethodNode

	fun isFieldActionEnabled(node: ICodeNodeRef) =
		node is FieldNode && options.codegenLanguage.isKotlin()

	override fun init(context: JadxPluginContext?) {
		context?.registerOptions(options)
		val guiContext = context?.guiContext ?: return
		initGui(guiContext)
	}

	fun initGui(context: JadxGuiContext) {
		context.addPopupMenuAction(
			"Copy Aliucord hook snippet (before)", ::isHookActionEnabled, null
		) {
			snippetAction(context, it, HookType.BEFORE)
		}
		context.addPopupMenuAction(
			"Copy Aliucord hook snippet (after)", ::isHookActionEnabled, null
		) {
			snippetAction(context, it, HookType.AFTER)
		}
		context.addPopupMenuAction(
			"Copy Aliucord hook snippet (instead)", ::isHookActionEnabled, null
		) {
			snippetAction(context, it, HookType.INSTEAD)
		}
		context.addPopupMenuAction(
			"Copy Aliucord accessor snippet (field)", ::isFieldActionEnabled, null
		) {
			snippetAction(context, it)
		}
	}

	fun snippetAction(context: JadxGuiContext, node: ICodeNodeRef, hookType: HookType? = null) {
		try {
			val snippet = copySnippet(node, hookType)
			context.copyToClipboard(snippet)
		} catch (t: Throwable) {
			JOptionPane.showMessageDialog(
				context.mainFrame, t.localizedMessage, "Error", JOptionPane.ERROR_MESSAGE
			)

		}
	}

	fun copySnippet(node: ICodeNodeRef, hookType: HookType? = null): String {
		return when (node) {
			is MethodNode -> {
				copyMethodSnippet(node, hookType!!)
			}

			is FieldNode -> {
				copyFieldSnippet(node)
			}

			else -> {
				throw JadxRuntimeException("Unsupported node type.")
			}
		}
	}

	fun copyMethodSnippet(node: MethodNode, hookType: HookType): String {
		val language = options.codegenLanguage
		val method = node.methodInfo
		val args = node.argTypes.map(this::fixType)
		val clazz = node.declaringClass
		var methodName = method.name
		val hookTypeStr = hookType.asString(language)

		var className = if (options.useFullName) {
			clazz.rawName
		} else {
			clazz.name
		}
		if (language.isJava()) {
			className = "$className.class"
		}
		if (language.isKotlin()) {
			if (className.contains('$')) className = "`$className`"
			methodName = if (methodName.contains('$')) {
				"$$\"$methodName\""
			} else {
				"\"$methodName\""
			}
		}
		if (node.accessFlags.isAbstract) throw JadxRuntimeException("Cannot create a snippet of abstract method.")
		return if (method.isConstructor) {
			val formattedArgs = args.joinToString(",\n")
			if (options.codegenLanguage.isKotlin()) {
				"patcher.$hookTypeStr<$className>($formattedArgs) { param -> \n // code \n }"
			} else {
				"patcher.patch($className.getDeclaredConstructor($formattedArgs), new $hookTypeStr(param -> { \n // code \n }))"
			}
		} else {
			val formattedArgs = args.joinToString(",\n", ",\n")
			if (options.codegenLanguage.isKotlin()) {
				"patcher.$hookTypeStr<$className>($methodName$formattedArgs) { param -> \n // code \n }"
			} else {
				"patcher.patch($className.getDeclaredMethod($methodName$formattedArgs), new $hookTypeStr(param -> { \n // code \n }))"
			}
		}
	}

	fun copyFieldSnippet(node: FieldNode): String {
		val language = options.codegenLanguage
		val field = node.fieldInfo
		val fieldType = getFieldType(node.type)
		val clazz = node.declaringClass
		var fieldName = field.name

		var className = if (options.useFullName) {
			clazz.rawName
		} else {
			clazz.name
		}

		if (language.isKotlin()) {
			if (className.contains('$')) className = "`$className`"
			fieldName = if (fieldName.contains('$')) {
				"$$\"$fieldName\""
			} else {
				"\"$fieldName\""
			}
		}

		if (node.accessFlags.isAbstract) throw JadxRuntimeException("Cannot create a getter snippet of abstract field.")
		return "var $className.exampleField by accessField<$fieldType>($fieldName)"
	}

	fun getFieldType(type: ArgType): String {
		val language = options.codegenLanguage
		if (language.isKotlin()) {
			if (type.canBeArray()) {
				val arrayElementType = type.arrayElement
				val processedType = processTypeName(arrayElementType.toString(), false)
				return "Array<$processedType>"
			}
			if (type.isGenericType && type.isObject && type.isTypeKnown) {
				processTypeName("java.lang.Object", false)
			}
			if (type.isGeneric && type.isObject) {
				val generics = type.genericTypes.map { "*" }.joinToString { "," }
				val processedType = processTypeName(type.`object`, false)
				return "$processedType<$generics>"
			}
			if (type.isPrimitive) {
				val pt = type.primitiveType
				return PRIMITIVE_TYPE_MAPPING[pt.toString()]!!
			}
		}
		return processTypeName(type.toString(), false)
	}

	fun fixType(type: ArgType): String {
		val language = options.codegenLanguage
		if (type.isGeneric && type.isObject) {
			return if (type.innerType != null) {
				processTypeName(type.toString(), true)
			} else {
				processTypeName(type.`object`, true)
			}
		}
		if (type.isGenericType && type.isObject && type.isTypeKnown) {
			processTypeName("java.lang.Object", false)
		}
		if (type.isPrimitive) {
			if (language.isJava()) return "$type.class"
			val pt = type.primitiveType
			return when (pt) {
				PrimitiveType.BOOLEAN -> "Boolean::class.javaPrimitiveType!!"
				PrimitiveType.CHAR -> "Char::class.javaPrimitiveType!!"
				PrimitiveType.BYTE -> "Byte::class.javaPrimitiveType!!"
				PrimitiveType.SHORT -> "Short::class.javaPrimitiveType!!"
				PrimitiveType.INT -> "Int::class.javaPrimitiveType!!"
				PrimitiveType.FLOAT -> "Float::class.javaPrimitiveType!!"
				PrimitiveType.LONG -> "Long::class.javaPrimitiveType!!"
				PrimitiveType.DOUBLE -> "Double::class.javaPrimitiveType!!"
				PrimitiveType.OBJECT -> "Any::class.java!!"
				PrimitiveType.ARRAY -> "Array::class.java!!"
				PrimitiveType.VOID -> "Void::class.javaPrimitiveType!!"
				else -> throw JadxRuntimeException("Unknown or null primitive type: $type")
			}
		}
		return processTypeName(type.toString(), true)
	}

	fun processTypeName(type: String, asClass: Boolean): String {
		val useFullName = options.useFullName
		val language = options.codegenLanguage
		var newType = type
		if (!useFullName) {
			newType = type.substringAfterLast('.')
		}
		if (asClass) {
			if (language.isJava()) return "$newType.class"
			return if (type in BOXED_TYPE_MAPPING) {
				"$newType::class.javaObjectType"
			} else {
				"$newType:class.java"
			}
		} else {
			return newType
		}
	}

	internal companion object {
		const val PLUGIN_ID = "aliucord-jadx"

		private val PRIMITIVE_TYPE_MAPPING: Map<String, String> = mapOf(
			"int" to "Int",
			"byte" to "Byte",
			"short" to "Short",
			"long" to "Long",
			"float" to "Float",
			"double" to "Double",
			"char" to "Char",
			"boolean" to "Boolean"
		)
		private val BOXED_TYPE_MAPPING: Map<String, String> = mapOf(
			"java.lang.Integer" to "Int",
			"java.lang.Byte" to "Byte",
			"java.lang.Short" to "Short",
			"java.lang.Long" to "Long",
			"java.lang.Float" to "Float",
			"java.lang.Double" to "Double",
			"java.lang.Character" to "Char",
			"java.lang.Boolean" to "Boolean",
		)
	}
}

