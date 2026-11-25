plugins { id("rehabgrip.kmp.compose") }

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "RehabGripShared"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:data"))
            api(project(":core:ble"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:exercises"))
            implementation(project(":feature:measure"))
            implementation(project(":feature:history"))
            implementation(project(":feature:progress"))
            implementation(project(":feature:profile"))
            implementation(project(":feature:reminders"))
            implementation(project(":feature:workouts"))
            implementation(project(":feature:grips"))
        }
    }
}
