import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cyclealarm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyclealarm.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 15
        versionName = "1.0.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

val asciiDebugUnitTestClassesDir = File(
    System.getProperty("user.home"),
    ".gradle/cyclealarm-test-classes/HospitalAlarm/debugUnitTest"
)

val syncDebugUnitTestClassesToAscii by tasks.registering(org.gradle.api.tasks.Copy::class) {
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"))
    into(asciiDebugUnitTestClassesDir)
    dependsOn("compileDebugKotlin")
    dependsOn("compileDebugUnitTestKotlin")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn(syncDebugUnitTestClassesToAscii)
        doFirst {
            classpath += files(asciiDebugUnitTestClassesDir)
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("cn.6tail:lunar:1.7.7")
    testImplementation("junit:junit:4.13.2")
}
