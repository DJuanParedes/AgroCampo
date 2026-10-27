plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("app.cash.sqldelight")
}
kotlin {
    androidTarget()
    jvm()
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            implementation("io.ktor:ktor-client-logging:3.0.3")
            implementation("app.cash.sqldelight:runtime:2.0.2")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
            implementation("io.insert-koin:koin-core:4.0.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.ktor:ktor-client-mock:3.0.3")
        }
        androidMain.dependencies { implementation("app.cash.sqldelight:android-driver:2.0.2") }
        jvmTest.dependencies { implementation("app.cash.sqldelight:sqlite-driver:2.0.2") }
    }
}
android { namespace = "pe.upeu.agrosierra.shared"; compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
sqldelight { databases { create("AgroDb") { packageName.set("pe.upeu.agrosierra.db") } } }
