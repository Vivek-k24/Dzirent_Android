import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
val rootRepoLocalPropertiesFile = rootProject.file("../local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
} else if (rootRepoLocalPropertiesFile.exists()) {
    rootRepoLocalPropertiesFile.inputStream().use { localProperties.load(it) }
}

val supabaseUrlProperty: String? = localProperties.getProperty("SUPABASE_URL")?.takeIf { it.isNotBlank() }
val supabaseAnonKeyProperty: String? = localProperties.getProperty("SUPABASE_ANON_KEY")?.takeIf { it.isNotBlank() }

// Placeholders keep debug builds compiling; release builds refuse them (see below).
val supabaseUrl: String = supabaseUrlProperty ?: "https://your-project-id.supabase.co"
val supabaseAnonKey: String = supabaseAnonKeyProperty ?: "your-anon-key-here"

val missingSupabaseProperties: List<String> = listOfNotNull(
    "SUPABASE_URL".takeIf { supabaseUrlProperty == null },
    "SUPABASE_ANON_KEY".takeIf { supabaseAnonKeyProperty == null },
)

val requireReleaseSupabaseConfig by tasks.registering {
    description = "Fails release builds that would embed placeholder Supabase configuration."
    val missing = missingSupabaseProperties
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release build requires ${missing.joinToString(" and ")} in local.properties " +
                    "(android/local.properties or the repository root local.properties). " +
                    "Refusing to build a release APK with placeholder Supabase values."
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(requireReleaseSupabaseConfig)
}

android {
    namespace = "com.dzirent.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dzirent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Supabase-kt BOM & official modules (Auth, Postgrest, Storage, Realtime)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)

    // Ktor Client engine
    implementation(libs.ktor.client.android)

    // Coil Compose for image loading from Supabase Storage
    implementation(libs.coil.compose)

    // Fused Location Provider
    implementation(libs.play.services.location)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
