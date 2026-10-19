import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Optional default Supabase credentials, read from local.properties (or env vars).
// They can also be entered in the app's Settings screen, which overrides these.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val defaultSupabaseUrl = localProperties.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL") ?: ""
val defaultSupabaseKey = localProperties.getProperty("supabase.key") ?: System.getenv("SUPABASE_KEY") ?: ""

val generateAppConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/appconfig/kotlin")
    val url = defaultSupabaseUrl
    val key = defaultSupabaseKey
    inputs.property("url", url)
    inputs.property("key", key)
    outputs.dir(outDir)
    doLast {
        fun String.escaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val file = outDir.get().file("config/AppConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package config
            |
            |/** Generated at build time from local.properties. Do not edit. */
            |internal object AppConfig {
            |    const val DEFAULT_SUPABASE_URL = "${url.escaped()}"
            |    const val DEFAULT_SUPABASE_KEY = "${key.escaped()}"
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain {
            kotlin.srcDir(generateAppConfig)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.postgrest)
                implementation(libs.sqldelight.coroutines)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.cio)
        }
        desktopTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("WorkoutDatabase") {
            packageName.set("db")
        }
    }
}

android {
    namespace = "app.repvane"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.repvane"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.1"
    }
    signingConfigs {
        // A fixed key committed to the repo, so every build (local or CI) can be installed as an update
        // over the previous one. Fine for a personal sideloaded app; don't use it for a Play Store release.
        create("sideload") {
            storeFile = file("release.keystore")
            storePassword = "workout-tracker"
            keyAlias = "workout"
            keyPassword = "workout-tracker"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/DEPENDENCIES",
            )
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Repvane"
            packageVersion = "1.5.1"
            description = "Repvane: workout tracker with smart progression and analytics"
            modules("java.sql")
            // Icons generated by scripts/generate_icons.py
            windows {
                iconFile.set(project.file("icons/repvane.ico"))
                menu = true
                shortcut = true
                upgradeUuid = "5f3c8a52-6a4e-4d7e-9a1b-2b8f0c7d4e11"
            }
            linux {
                iconFile.set(project.file("icons/repvane.png"))
            }
            macOS {
                iconFile.set(project.file("icons/repvane.icns"))
            }
        }
    }
}
