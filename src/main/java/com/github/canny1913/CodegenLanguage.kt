package com.github.canny1913

enum class CodegenLanguage {
	JAVA,
	KOTLIN;

	fun isJava() = this == JAVA
	fun isKotlin() = this == KOTLIN
}
