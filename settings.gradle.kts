rootProject.name = "votebot"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":chart-service-client",
    ":common",
    ":plugin"
)

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
