pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RehabGrip"

include(":core:domain", ":core:data", ":core:ble", ":core:designsystem")
include(":feature:onboarding", ":feature:exercises", ":feature:measure")
include(":feature:history", ":feature:progress", ":feature:profile")
include(":feature:reminders", ":feature:workouts")
include(":shared", ":androidApp")
