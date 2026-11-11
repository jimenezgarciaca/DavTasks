@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    id("dagger.hilt.android.plugin")
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}

val appVersionCode: Int by extra
val appVersionName: String by extra

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        moduleName.set("tasks_app")
    }
}

android {
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        lintConfig = file("lint.xml")
        textOutput = File("stdout")
        textReport = true
    }

    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        testApplicationId = "${libs.versions.applicationId.get()}.test"
        applicationId = libs.versions.applicationId.get()
        versionCode = appVersionCode
        versionName = appVersionName
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "org.tasks.TestRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "org.tasks"
        resValue("string", "opentasks_authority", "$applicationId.opentasks")
    }

    signingConfigs {
        create("release") {
            val tasksKeyAlias: String? by project
            val tasksStoreFile: String? by project
            val tasksStorePassword: String? by project
            val tasksKeyPassword: String? by project

            keyAlias = tasksKeyAlias
            storeFile = file(tasksStoreFile ?: "none")
            storePassword = tasksStorePassword
            keyPassword = tasksKeyPassword
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions += listOf("store")

    @Suppress("LocalVariableName")
    buildTypes {
        debug {
            val tasks_mapbox_key_debug: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key_debug ?: "")
            enableUnitTestCoverage = project.hasProperty("coverage")
            enableAndroidTestCoverage = project.hasProperty("coverage")
        }
        release {
            val tasks_mapbox_key: String? by project
            resValue("string", "mapbox_key", tasks_mapbox_key ?: "")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.pro")
            optimization.keepRules {
                ignoreFrom("com.github.franmontiel:PersistentCookieJar")
                ignoreFrom("com.github.tasks:ical4android")
            }
            signingConfig = signingConfigs.getByName("release")
        }
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    productFlavors {
        create("generic") {
            dimension = "store"
            proguardFile("proguard-generic.pro")
        }
    }
    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/INDEX.LIST")
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Shared test helpers replace Android-test symlinks, which are not portable to Windows.
        getByName("test").java.directories.add("src/sharedTest/java")
        getByName("androidTest").java.directories.add("src/sharedTest/java")
    }

    namespace = "org.tasks"
}

androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.sources.java?.addStaticSourceDirectory("src/release/java")
        variant.sources.res?.addStaticSourceDirectory("src/release/res")
        variant.flavorName?.takeIf { it.isNotEmpty() }?.let { flavor ->
            variant.sources.res?.addStaticSourceDirectory("src/${flavor}Release/res")
        }
    }
}

configurations.all {
    exclude(group = "org.apache.httpcomponents")
    exclude(group = "org.checkerframework")
    exclude(group = "com.google.code.findbugs")
    exclude(group = "com.google.errorprone")
    exclude(group = "com.google.j2objc")
    exclude(group = "com.google.http-client", module = "google-http-client-apache-v2")
    exclude(group = "com.google.http-client", module = "google-http-client-jackson2")
}

val genericImplementation by configurations

dependencies {
    implementation(projects.data)
    implementation(projects.kmp)
    baselineProfile(projects.baselineprofile)
    implementation(libs.kermit)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.adaptive.navigation.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.bitfire.ical4android) {
        exclude(group = "commons-logging")
        exclude(group = "org.json", module = "json")
        exclude(group = "org.codehaus.groovy", module = "groovy")
        exclude(group = "org.codehaus.groovy", module = "groovy-dateutil")
        exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
    implementation(libs.dmfs.opentasks.provider) {
        exclude("com.github.tasks.opentasks", "opentasks-contract")
    }
    implementation(libs.dmfs.rfc5545.datetime)
    implementation(libs.dmfs.recur)
    implementation(libs.dmfs.jems)

    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.appfunctions.compiler)
    implementation(libs.androidx.hilt.navigation)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.core.remoteviews)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.room3)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.appfunctions)
    implementation(libs.markwon)
    implementation(libs.markwon.editor)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation(libs.kotlin.reflect)

    implementation(libs.kotlin.jdk8)
    implementation(libs.kotlinx.immutable)
    implementation(libs.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.persistent.cookiejar)
    implementation(libs.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.timber)
    implementation(libs.dashclock.api)
    implementation(libs.locale) {
        isTransitive = false
    }
    implementation(libs.jchronic) {
        isTransitive = false
    }
    implementation(libs.shortcut.badger)
    implementation(libs.androidx.work)
    implementation(libs.colorpicker)
    implementation(libs.osmdroid)
    implementation(libs.androidx.recyclerview)

    implementation(platform(libs.androidx.compose))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    implementation(libs.ktor)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    implementation(libs.accompanist.permissions)


    androidTestImplementation(libs.dagger.hilt.testing)
    kspAndroidTest(libs.dagger.hilt.compiler)
    kspAndroidTest(libs.androidx.hilt.compiler)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.make.it.easy)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.make.it.easy)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.xpp3)
}
