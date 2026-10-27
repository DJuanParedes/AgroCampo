plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "pe.upeu.agrosierra"
    compileSdk = 35
    defaultConfig { applicationId = "pe.upeu.agrosierra"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"") }
        release { buildConfigField("String", "API_BASE_URL", "\"https://api.agrosierra.example/api/v1/\"") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("app.cash.sqldelight:android-driver:2.0.2")
}
