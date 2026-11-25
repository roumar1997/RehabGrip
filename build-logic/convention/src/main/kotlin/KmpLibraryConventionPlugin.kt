import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Kotlin Multiplatform library targeting Android + iOS with shared defaults. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        extensions.configure<KotlinMultiplatformExtension> {
            androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
            iosX64()
            iosArm64()
            iosSimulatorArm64()
            applyDefaultHierarchyTemplate()
            // CoreBluetooth/UIKit (iOS) usan cinterop experimental: opt-in para todo el modulo.
            sourceSets.all { languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi") }
            sourceSets.getByName("commonTest").dependencies { implementation(kotlin("test")) }
        }
        extensions.configure<LibraryExtension> {
            namespace = "com.rehabgrip." + path.removePrefix(":").replace(':', '.')
            compileSdk = 35
            defaultConfig { minSdk = 26 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
