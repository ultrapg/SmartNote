import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.ultrapg.smartnote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.ultrapg.smartnote"
        minSdk = 28
        targetSdk = 36
        versionCode = 18
        versionName = "1.0.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        noCompress += listOf("tflite")
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val candidatePropsFiles = listOf(
                file("${System.getProperty("user.home")}/.android/smartnote-keys/keystore.properties"),
                rootProject.file("keystore.properties")
            )
            candidatePropsFiles.firstOrNull { it.exists() }?.inputStream()?.use { props.load(it) }

            val resolvedStorePath = System.getenv("SMARTNOTE_KEYSTORE_PATH")
                ?: props.getProperty("storeFile")
            val resolvedStorePassword = System.getenv("SMARTNOTE_KEYSTORE_PASSWORD")
                ?: props.getProperty("storePassword")
            val resolvedKeyAlias = System.getenv("SMARTNOTE_KEY_ALIAS")
                ?: props.getProperty("keyAlias")
            val resolvedKeyPassword = System.getenv("SMARTNOTE_KEY_PASSWORD")
                ?: props.getProperty("keyPassword")

            if (!resolvedStorePath.isNullOrBlank() &&
                !resolvedStorePassword.isNullOrBlank() &&
                !resolvedKeyAlias.isNullOrBlank() &&
                !resolvedKeyPassword.isNullOrBlank()
            ) {
                val ksFile = file(resolvedStorePath)
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = resolvedStorePassword
                    keyAlias = resolvedKeyAlias
                    keyPassword = resolvedKeyPassword
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val relSigning = signingConfigs.getByName("release")
            if (relSigning.storeFile?.exists() == true) {
                signingConfig = relSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xskip-metadata-version-check"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.services.mlkit.image.labeling)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

configurations.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.sqlite:sqlite-bundled"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqliteBundled.get()}"))
        substitute(module("androidx.sqlite:sqlite-bundled-android"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqliteBundled.get()}"))
    }
}
