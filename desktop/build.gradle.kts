import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

application {
    mainClass.set("com.quio.ytm.desktop.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.gson)

    testImplementation(libs.junit)
}

tasks.named<ProcessResources>("processResources") {
    val localPropsFile = rootProject.file("local.properties")
    doLast {
        val props = if (localPropsFile.exists()) {
            Properties().apply { localPropsFile.inputStream().use { stream -> this.load(stream) } }
        } else null

        val clientId = props?.getProperty("google.client.id")
            ?: System.getenv("GOOGLE_CLIENT_ID")
            ?: ""
        val clientSecret = props?.getProperty("google.client.secret")
            ?: System.getenv("GOOGLE_CLIENT_SECRET")
            ?: ""

        if (clientId.isNotBlank()) {
            val targetFile = destinationDir.resolve("oauth_config.properties")
            targetFile.writeText("google.client.id=$clientId\ngoogle.client.secret=$clientSecret\n")
        }
    }
}

val appVersion = "0.0.2"
val jpackageVersion = "1.0.2" // macOS jpackage requires the first number to be >= 1

tasks.register<Exec>("packageDmg") {
    group = "distribution"
    description = "Packages the desktop application into a standalone macOS DMG installer using jpackage"
    dependsOn("installDist")

    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jpackageExec = if (javaHome != null && file("$javaHome/bin/jpackage").exists()) {
        "$javaHome/bin/jpackage"
    } else {
        "jpackage"
    }

    val outputDir = layout.buildDirectory.dir("dist").get().asFile
    val inputDir = layout.buildDirectory.dir("install/desktop/lib").get().asFile
    val iconFile = file("${project.rootDir}/figures/app_icon.icns")

    doFirst {
        outputDir.mkdirs()
    }

    val argsList = mutableListOf(
        jpackageExec,
        "--type", "dmg",
        "--name", "kiki's youtube mixer",
        "--app-version", jpackageVersion,
        "--input", inputDir.absolutePath,
        "--main-jar", "desktop.jar",
        "--main-class", "com.quio.ytm.desktop.MainKt",
        "--dest", outputDir.absolutePath
    )

    if (iconFile.exists()) {
        argsList.add("--icon")
        argsList.add(iconFile.absolutePath)
    }

    commandLine(argsList)

    doLast {
        val generatedDmg = file("${outputDir.absolutePath}/kiki's youtube mixer-${jpackageVersion}.dmg")
        val targetDmg = file("${project.rootDir}/kiki's youtube mixer-${appVersion}.dmg")
        if (generatedDmg.exists()) {
            generatedDmg.copyTo(targetDmg, overwrite = true)
        }
    }
}
