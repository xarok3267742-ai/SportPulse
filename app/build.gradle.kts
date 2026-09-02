import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file(".local/release-signing.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun configuredValue(name: String): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name).orEmpty()
}

fun quotedBuildConfig(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

fun signingValue(propertyName: String, environmentName: String): String {
    return providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName).orEmpty()
}

val releaseStoreFile = signingValue(
    "storeFile",
    "SPORT_PULSE_RELEASE_STORE_FILE"
).trim()
val releaseStorePassword = signingValue(
    "storePassword",
    "SPORT_PULSE_RELEASE_STORE_PASSWORD"
)
val releaseKeyAlias = signingValue(
    "keyAlias",
    "SPORT_PULSE_RELEASE_KEY_ALIAS"
).trim()
val releaseKeyPassword = signingValue(
    "keyPassword",
    "SPORT_PULSE_RELEASE_KEY_PASSWORD"
)
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all(String::isNotBlank)

val sportsScheduleProxyUrl = configuredValue(
    "SPORTS_SCHEDULE_PROXY_URL"
).trim().also { value ->
    if (value.isNotBlank()) {
        val uri = runCatching { URI(value) }
            .getOrElse { error("SPORTS_SCHEDULE_PROXY_URL is not a valid URL") }
        val host = uri.host?.lowercase().orEmpty()
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "SPORTS_SCHEDULE_PROXY_URL must use HTTPS"
        }
        require(host.isNotBlank() && uri.userInfo == null && uri.fragment == null) {
            "SPORTS_SCHEDULE_PROXY_URL must be a public HTTPS endpoint without credentials or fragments"
        }
        require(
            !host.endsWith(".rapidapi.com") &&
                host != "api-sports.io" &&
                !host.endsWith(".api-sports.io")
        ) {
            "SPORTS_SCHEDULE_PROXY_URL must point to a server-side proxy, not the data provider"
        }
    }
}

android {
    namespace = "ru.sportpulse.info"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.sportpulse.info"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 79
        versionName = "3.7.0"
        buildConfigField(
            "String",
            "SPORTS_SCHEDULE_PROXY_URL",
            quotedBuildConfig(sportsScheduleProxyUrl)
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Upgrade the wrapper together with AGP; 9.3.1 is its documented pairing.
        disable += "AndroidGradlePluginVersion"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
