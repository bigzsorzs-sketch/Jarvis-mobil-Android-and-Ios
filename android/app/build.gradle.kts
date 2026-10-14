plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Release credentials are supplied only by protected CI environment variables.
// Without them developer builds remain unsigned; never generate a new release key.
val releaseStore = System.getenv("ANDROID_SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
val jarvisGatewayOrigin = (System.getenv("JARVIS_GATEWAY_ORIGIN") ?: "").trim().trimEnd('/')
// A public origin is configuration, NEVER an embedded shared API credential.
require(jarvisGatewayOrigin.isEmpty() ||
    jarvisGatewayOrigin.matches(Regex("""https://[A-Za-z0-9.-]+(?::[0-9]{2,5})?"""))) {
    "JARVIS_GATEWAY_ORIGIN must be a public HTTPS origin without paths or credentials"
}

android {
    namespace = "ai.jarvis.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.jarvis.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "0.2.30"
        buildConfigField("String", "JARVIS_GATEWAY_ORIGIN", "\"$jarvisGatewayOrigin\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseStore != null) {
            create("upload") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // Google Identity AuthorizationClient: no account passwords or client secrets in APK.
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

