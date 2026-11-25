plugins {
    id("rehabgrip.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android) }
        iosMain.dependencies { implementation(libs.sqldelight.native) }
    }
}

sqldelight {
    databases {
        create("RehabGripDatabase") {
            packageName.set("com.rehabgrip.core.data.db")
        }
    }
}
