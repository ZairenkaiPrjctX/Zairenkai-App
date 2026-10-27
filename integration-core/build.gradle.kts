// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.lint")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // Nullability annotations on the Java sources, read by the Kotlin compiler at call sites.
    compileOnly(libs.jetbrains.annotations)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
