import dev.schlaubi.mikbot.gradle.mikbot

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikbot)
    alias(libs.plugins.buildconfig)
}

dependencies {
    api(projects.common)
    implementation(projects.chartServiceClient)
    implementation(libs.java.string.similarity)
    optionalPlugin(mikbot(libs.mikbot.gdpr))
    optionalPlugin(mikbot(libs.mikbot.kubernetes))
    optionalPlugin(mikbot(libs.mikbot.ktor))
}

mikbotPlugin {
    description = "Plugin adding VoteBot functionality"
    bundle = "votebot"
    pluginId = "votebot"
    provider = "votebot.space"
    license = "MIT"
    enableKordexProcessor = true

    i18n {
        classPackage = "space.votebot.translations"
        translationBundle = "votebot"
        className = "VoteBotTranslations"
    }
}

buildConfig  {
    packageName = "space.votebot"
    className = "VoteBotInfo"
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "BRANCH", "\"${project.getGitBranch()}\"")
    buildConfigField("String", "COMMIT", "\"${project.getGitCommit()}\"")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin/"))
        }
    }
}

tasks {
    afterEvaluate {
        named("kspKotlin") {
            dependsOn(generateBuildConfig)
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

fun Project.getGitCommit(): String {
    return execCommand(arrayOf("git", "rev-parse", "--short", "HEAD"))
        ?: System.getenv("GITHUB_SHA") ?: "<unknown>"
}

fun Project.getGitBranch(): String {
    return execCommand(arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD")) ?: "unknown"
}

fun Project.execCommand(command: Array<String>): String? {
    return try {
        providers.exec {
            commandLine(command.asIterable())
        }.standardOutput.asText.get().trim()
    } catch (e: Throwable) {
        logger.warn("An error occurred whilst executing a command", e)
        null
    }
}
