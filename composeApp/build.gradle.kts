import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
        }
        jvmMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/secrets/kotlin/jvmMain"))
        }
    }
}

val generateBuildSecrets by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/secrets/kotlin/jvmMain/com/example/aiadventchalengetestllmapi")
    val outputFile = outputDir.map { it.file("BuildSecrets.kt") }
    val secretsFile = rootProject.file("secrets.properties")

    outputs.file(outputFile)
    if (secretsFile.exists()) {
        inputs.file(secretsFile)
    }

    doLast {
        val secrets = Properties()
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { secrets.load(it) }
        }

        fun escapeForKotlin(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

        fun readSecret(key: String): String = secrets.getProperty(key)?.trim().orEmpty()

        val deepSeek = escapeForKotlin(readSecret("DEEPSEEK_API_KEY"))
        val openAi = escapeForKotlin(readSecret("OPENAI_API_KEY"))
        val gigaChat = escapeForKotlin(readSecret("GIGACHAT_ACCESS_TOKEN"))
        val proxyApi = escapeForKotlin(readSecret("PROXYAPI_API_KEY"))

        val content = """
            package com.example.aiadventchalengetestllmapi

            internal object BuildSecrets {
                private const val DEEPSEEK_API_KEY: String = "$deepSeek"
                private const val OPENAI_API_KEY: String = "$openAi"
                private const val GIGACHAT_ACCESS_TOKEN: String = "$gigaChat"
                private const val PROXYAPI_API_KEY: String = "$proxyApi"

                fun apiKeyFor(envVar: String): String = when (envVar) {
                    "DEEPSEEK_API_KEY" -> DEEPSEEK_API_KEY
                    "OPENAI_API_KEY" -> OPENAI_API_KEY
                    "GIGACHAT_ACCESS_TOKEN" -> GIGACHAT_ACCESS_TOKEN
                    "PROXYAPI_API_KEY" -> PROXYAPI_API_KEY
                    else -> ""
                }
            }
        """.trimIndent()

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateBuildSecrets)
}


compose.desktop {
    application {
        mainClass = "com.example.aiadventchalengetestllmapi.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.aiadventchalengetestllmapi"
            packageVersion = "1.0.0"
        }
    }
}
