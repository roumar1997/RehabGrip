plugins { id("rehabgrip.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
