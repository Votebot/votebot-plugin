package space.votebot.commands.vote

import dev.kordex.core.extensions.publicSlashCommand
import dev.kord.rest.builder.message.embed
import dev.schlaubi.mikbot.plugin.api.MikBotInfo
import dev.schlaubi.mikbot.plugin.api.util.executableEverywhere
import dev.schlaubi.mikbot.plugin.api.util.translate
import dev.schlaubi.stdx.coroutines.parallelMapNotNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import space.votebot.VoteBotInfo
import space.votebot.core.VoteBotConfig
import space.votebot.core.VoteBotModule
import space.votebot.translations.VoteBotTranslations
import kotlin.collections.set

private val LOG = KotlinLogging.logger { }
private val repositories = listOf("DRSchlaubi/mikbot", "Votebot/piechart-service")
private val gitHubUserCache = mutableMapOf<Long, GitHubUser>()

private val json = Json {
    ignoreUnknownKeys = true
}

private val client = HttpClient {
//    install(ContentNegotiation) {
//        json(json)
//    }
}

@Serializable
private data class GitHubContributor(val id: Long, val url: String)

@Serializable
private data class GitHubUser(
    @SerialName("html_url") val htmlUrl: String,
    val name: String?,
    val id: Long,
    val login: String,
)

private suspend fun findContributors() = repositories.parallelMapNotNull { repository ->
    client.get("https://api.github.com/repos") {
        url {
            appendPathSegments(repository, "contributors")
        }

        if (VoteBotConfig.GITHUB_TOKEN != null) {
            val token = "${VoteBotConfig.GITHUB_USERNAME}:${VoteBotConfig.GITHUB_TOKEN}"
                .encodeBase64()
            header(HttpHeaders.Authorization, "Basic $token")
        }
    }.body<List<GitHubContributor>>()
}
    .flatten()
    .parallelMapNotNull { (id, url) ->
        gitHubUserCache[id] ?: client.get(url).body<GitHubUser>().also {
            gitHubUserCache[id] = it
        }
    }
    .distinctBy(GitHubUser::id)

suspend fun VoteBotModule.infoCommand() = publicSlashCommand {
    name = VoteBotTranslations.Commands.Info.name
    description = VoteBotTranslations.Commands.Info.description
    executableEverywhere()

    action {
        respond {
            embed {
                title = "VoteBot"

                description = translate(VoteBotTranslations.Commands.Info.mikbot)

                field {
                    name = translate(VoteBotTranslations.Commands.Info.contributors)
                    val contributors = runCatching { findContributors() }
                    contributors.exceptionOrNull()?.let {
                        LOG.warn(it) { "An error occurred while fetching Contributors" }
                    }
                    value = contributors.getOrNull()?.joinToString { (url, name, _, login) ->
                        "[${name ?: login}]($url)"
                    } ?: translate(VoteBotTranslations.Commands.Info.Contributors.failed)
                    inline = true
                }

                field {
                    name = translate(VoteBotTranslations.Commands.Info.graphics)
                    value = "[Oskar Lang](https://rxs.to)"
                    inline = false
                }

                field {
                    name = translate(VoteBotTranslations.Commands.Info.Version.mikbot)
                    value = MikBotInfo.VERSION
                    inline = true
                }

                field {
                    name = translate(VoteBotTranslations.Commands.Info.Version.votebot)
                    value = VoteBotInfo.VERSION
                    inline = true
                }

                field {
                    name = translate(VoteBotTranslations.Commands.Info.sourceCode)
                    value = "https://github.com/votebot/votebot-plugin/tree/main"
                    inline = false
                }
            }
        }
    }
}
