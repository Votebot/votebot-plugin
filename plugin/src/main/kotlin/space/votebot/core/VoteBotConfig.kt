package space.votebot.core

import dev.schlaubi.mikbot.plugin.api.EnvironmentConfig
import io.ktor.http.*

object VoteBotConfig : EnvironmentConfig("") {
    val PIE_CHART_SERVICE_URL by getEnv { Url(it) }
    val GITHUB_TOKEN by getEnv().optional()
    val GITHUB_USERNAME by getEnv().optional()
}
