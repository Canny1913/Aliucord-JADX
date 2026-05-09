import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Locale

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
	id("se.patrikerdes.use-latest-versions") version "0.2.19"
	id("com.github.ben-manes.versions") version "0.54.0"
	kotlin("jvm") version "2.3.0"
}

dependencies {
	val jadxVersion = "1.5.5"
	val isJadxSnapshot = jadxVersion.endsWith("-SNAPSHOT")

    compileOnly("io.github.skylot:jadx-core:$jadxVersion") {
        isChanging = isJadxSnapshot
    }
	implementation(kotlin("stdlib-jdk8"))
}

kotlin {
	jvmToolchain(11)
}
repositories {
	mavenLocal()
    mavenCentral()
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    google()
}

version = System.getenv("VERSION") ?: "dev"

tasks {
    val shadowJar = withType(ShadowJar::class) {
        archiveClassifier.set("") // remove '-all' suffix
    }

    // copy result jar into "build/dist" directory
    register<Copy>("dist") {
		group = "jadx-plugin"
        dependsOn(shadowJar)
        dependsOn(withType(Jar::class))

        from(shadowJar)
        into(layout.buildDirectory.dir("dist"))
    }
}


tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
	rejectVersionIf {
		// disallow release candidates as upgradable versions from stable versions
		isNonStable(candidate.version) && !isNonStable(currentVersion)
	}
}

fun isNonStable(version: String): Boolean {
	val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.getDefault()).contains(it) }
	val regex = "^[0-9,.v-]+(-r)?$".toRegex()
	val isStable = stableKeyword || regex.matches(version)
	return isStable.not()
}
