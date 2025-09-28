import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
allprojects {
    version = "5.8.2v"
    group = "space.votebot"

    repositories {
        mavenCentral()
    }
}

subprojects {
    afterEvaluate {
        configure<KotlinBaseExtension> {
            jvmToolchain(24)
        }
    }
}
