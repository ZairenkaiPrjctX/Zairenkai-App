import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Integration metadata (inventory, lock) is copied into the APK's assets so the app can show
// every repository's disposition and the revisions it was built from.
val integrationMeta = layout.buildDirectory.dir("generated/integration-meta")
val copyIntegrationMeta = tasks.register<Copy>("copyIntegrationMeta") {
    from(rootProject.file("integration/organization-repositories.json"), rootProject.file("integration/repository-lock.json"))
    into(integrationMeta.map { it.dir("integration") })
}

// Components built by integration/scripts/build-components.sh at the locked commits.
val componentsDir = rootProject.file("integration/out")
val allowMissingComponents = providers.gradleProperty("zairenkai.allowMissingComponents").map { it.toBoolean() }.orElse(false)
val verifyIntegrationComponents = tasks.register("verifyIntegrationComponents") {
    val required = listOf("arm64-v8a", "armeabi-v7a", "x86_64").map { "jniLibs/$it/libzart.so" } +
        listOf("assets/afid/device-candidates.json", "assets/components.json")
    val allow = allowMissingComponents
    doLast {
        val missing = required.filterNot { componentsDir.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            val msg = "Integration components missing: $missing. Run integration/scripts/build-components.sh, " +
                "or pass -Pzairenkai.allowMissingComponents=true to build an APK whose ART and device-database " +
                "features report NATIVE_COMPONENT_UNAVAILABLE / INITIALIZATION_FAILED."
            if (allow.get()) logger.warn("WARNING: $msg") else throw GradleException(msg)
        }
    }
}

android {
    namespace = "com.zairenkaiprjctx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zairenkaiprjctx.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            // The ABIs zart is built for (integration/repository-lock.json).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            // No release signing configuration exists: assembleRelease produces an unsigned APK.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // zart is an executable, not a JNI library: it must be extracted to nativeLibraryDir
            // so it can be executed (app data directories are not executable for targetSdk >= 29).
            useLegacyPackaging = true
        }
    }

    sourceSets["main"].apply {
        jniLibs.srcDir(componentsDir.resolve("jniLibs"))
        assets.srcDirs(componentsDir.resolve("assets"), integrationMeta)
    }

    androidResources {
        // Declares English and Indonesian for the per-app language setting (Android 13+).
        generateLocaleConfig = true
        // Only the app's own languages: otherwise library strings in ~80 other languages would
        // mix with English app text on, for example, a German device.
        localeFilters += listOf("en", "in")
    }

    bundle {
        // The language is chosen in the app (AppLanguage), not by the system language, so an App
        // Bundle install must carry both languages instead of only the device's.
        language { enableSplit = false }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric downloads its android-all jars itself; Maven Central rate-limits this
                // build environment, so allow overriding the repository from the environment.
                System.getenv("ROBOLECTRIC_REPO_URL")?.let { url -> it.systemProperty("robolectric.dependency.repo.url", url) }
            }
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") { dependsOn(copyIntegrationMeta) }

androidComponents {
    // Compose UI tests need the debug-only ui-test-manifest activity; run unit tests on debug.
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}
tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }.configureEach {
    dependsOn(verifyIntegrationComponents)
}

dependencies {
    implementation(project(":integration-core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.annotation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
