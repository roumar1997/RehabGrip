plugins { id("rehabgrip.kmp.compose") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:domain"))
            implementation(project(":core:ble"))
        }
    }
}
