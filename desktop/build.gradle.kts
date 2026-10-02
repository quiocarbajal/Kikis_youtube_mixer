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
        "--app-version", "1.0.0",
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
        val generatedDmg = file("${outputDir.absolutePath}/kiki's youtube mixer-1.0.0.dmg")
        val targetDmg = file("${project.rootDir}/kiki's youtube mixer-0.0.1.dmg")
        if (generatedDmg.exists()) {
            generatedDmg.copyTo(targetDmg, overwrite = true)
        }
    }
}
