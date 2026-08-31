plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }

val nondaKeystorePath = System.getenv("NONDA_KEYSTORE_PATH")
val nondaKeystorePassword = System.getenv("NONDA_KEYSTORE_PASSWORD")
val nondaKeyAlias = System.getenv("NONDA_KEY_ALIAS")
val nondaKeyPassword = System.getenv("NONDA_KEY_PASSWORD")
val hasReleaseSigning = listOf(nondaKeystorePath, nondaKeystorePassword, nondaKeyAlias, nondaKeyPassword).all { !it.isNullOrBlank() }

android { namespace = "jp.okusuri.nonda"; compileSdk = 35
    defaultConfig { applicationId = "jp.okusuri.nonda"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "1.2.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(nondaKeystorePath!!)
                storePassword = nondaKeystorePassword
                keyAlias = nondaKeyAlias
                keyPassword = nondaKeyPassword
            }
        }
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.findByName("release") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1"); implementation("androidx.glance:glance-material3:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
