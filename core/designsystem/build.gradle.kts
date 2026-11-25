plugins { id("rehabgrip.kmp.compose") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.rehabgrip.core.designsystem.resources"
}
