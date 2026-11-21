plugins {
    id("com.android.application")
}

android {
    namespace = "com.veles.caloriediary"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.veles.caloriediary"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
