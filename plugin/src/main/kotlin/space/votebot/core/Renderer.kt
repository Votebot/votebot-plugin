package space.votebot.core

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.interaction.response.EphemeralMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.x.emoji.Emojis
import dev.kordex.core.time.TimestampType
import dev.kordex.core.time.toDiscord
import dev.schlaubi.mikbot.plugin.api.util.effectiveAvatar
import dev.schlaubi.mikbot.plugin.api.util.embed
import dev.schlaubi.stdx.coroutines.forEachParallel
import dev.schlaubi.stdx.coroutines.localSuspendLazy
import io.ktor.client.request.forms.*
import kotlinx.datetime.Clock
import mu.KotlinLogging
import space.votebot.common.models.Poll
import space.votebot.common.models.VoteOption
import space.votebot.common.models.sumUp
import space.votebot.pie_char_service.client.PieChartCreateRequest
import space.votebot.pie_char_service.client.PieChartServiceClient
import space.votebot.pie_char_service.client.Vote
import space.votebot.transformer.TransformerContext
import space.votebot.transformer.transformMessageSafe
import java.text.DecimalFormat

private val percentage = DecimalFormat("#.##%")

const val block = "■"
const val blockBarLength = 30

val pieChartService = PieChartServiceClient(VoteBotConfig.PIE_CHART_SERVICE_URL)

private val LOG = KotlinLogging.logger { }

suspend fun Poll.addMessage(
    channel: VoteParentChannel,
    closeButton: Boolean,
    guild: GuildBehavior?,
    addButtons: Boolean,
    addToDatabase: Boolean,
): VoteParentChannel.Message {
    val message = channel.createMessage {
        embeds = mutableListOf(toEmbed(channel.kord, guild, false))
        if (addButtons) {
            components = makeButtons(channel.kord, closeButton, guild).toMutableList()
        }
    }

    if (addToDatabase) {
        VoteBotDatabase.polls.save(copy(messages = messages + message.toPollMessage()))
    }

    return message
}

/**
 * Returns whether all updates were successful.
 */
suspend fun Poll.updateMessages(
    kord: Kord,
    guild: GuildBehavior?,
    removeButtons: Boolean = false,
    highlightWinner: Boolean = false,
    showChart: Boolean? = null,
    deleteFailingMessages: Boolean = true,
    exceptFor: List<ULong> = emptyList(),
    response: EphemeralMessageInteractionResponseBehavior? = null,
): Boolean {
    val pieChart = if (highlightWinner && showChart ?: settings.showChartAfterClose && votes.isNotEmpty()) {
        runCatching {
            pieChartService
                .createPieChart(toPieChartCreateRequest(kord, guild))
        }.getOrNull()
    } else {
        null
    }

    val failedMessages = mutableListOf<Poll.Message>()

    require(response == null || messages.size == 1) { "Unsupported use case" }
    messages
        .filter { it.messageId !in exceptFor }
        .forEachParallel { message ->
            try {
                val messageBehavior = message.toBehavior(kord)
                val permissions = localSuspendLazy { messageBehavior.getEffectivePermissions(kord.selfId) }
                val messageEditor: suspend MessageModifyBuilder.() -> Unit = {
                    if (pieChart != null) {
                        if (Permission.AttachFiles in permissions()) {
                            addFile("chart.png", ChannelProvider { pieChart })
                        } else {
                            content = "Could not create pie chart due to missing permissions"
                        }
                    } else {
                        content = ""
                    }

                    embeds = mutableListOf(toEmbed(kord, guild, highlightWinner))
                    components = if (removeButtons) {
                        mutableListOf()
                    } else {
                        makeButtons(kord, message.interaction != null, guild).toMutableList()
                    }
                }
                if (response == null) {
                    message.toBehavior(kord).edit(messageEditor)
                } else {
                    response.edit { messageEditor() }
                }
                // yeah yeah I KNOW catching Exception is bad, but it isn't raly a huge problem here,
                // since handling for all exception will remain the same
            } catch (ignored: Exception) {
                LOG.debug(ignored) { "An error occurred whilst updating a poll message" }
                failedMessages += message
            }
        }

    if (failedMessages.isNotEmpty() && deleteFailingMessages) {
        VoteBotDatabase.polls.save(copy(messages = messages - failedMessages.toSet()))
    }

    return failedMessages.isEmpty()
}

private suspend fun Poll.makeButtons(
    kord: Kord,
    closeButton: Boolean,
    guild: GuildBehavior?
): List<MessageComponentBuilder> =
    sortedOptions
        .chunked(5)
        .map { options ->
            ActionRowBuilder().apply {
                options.forEach { (_, index, option, emoji) ->
                    interactionButton(ButtonStyle.Primary, "vote_$index") {
                        label = transformMessageSafe(option, TransformerContext(guild, kord, false))
                        this.emoji = emoji?.toDiscordPartialEmoji()
                    }
                }
            }
        }.run {
            if (closeButton) {
                this + ActionRowBuilder().apply {
                    interactionButton(ButtonStyle.Danger, "close") {
                        label = "Close"
                        emoji(ReactionEmoji.Unicode(Emojis.cross.toString()))
                    }
                }
            } else {
                this
            }
        }

suspend fun Poll.toEmbed(
    kord: Kord,
    guild: GuildBehavior?,
    highlightWinner: Boolean = false,
    overwriteHideResults: Boolean = false,
): EmbedBuilder = embed {
    title = transformMessageSafe(this@toEmbed.title, TransformerContext(guild, kord, false))

    author {
        val user = kord.getUser(Snowflake(authorId))
        name = user?.username
        icon = user?.effectiveAvatar
    }

    val names = sortedOptions
        .map { (index, _, value, emoji) ->
            val prefix = emoji?.toDiscordPartialEmoji()?.mention ?: "${index + 1}."
            "$prefix ${transformMessageSafe(value, TransformerContext(guild, kord, true))}"
        }.joinToString(separator = "\n")

    val totalVotes = votes.sumOf(Poll.Vote::amount)
    val results = if (options.isEmpty()) {
        ""
    } else if (!settings.hideResults || highlightWinner || overwriteHideResults) {
        val resultsText = sumUp()
            .joinToString(separator = "\n") { (option, _, votePercentage) ->
                val blocksForOption = (votePercentage * blockBarLength).toInt()

                " ${option.positionedIndex + 1} | ${
                    block.repeat(blocksForOption).padEnd(blockBarLength)
                } | (${percentage.format(votePercentage)})"
            }
        """```$resultsText```"""
    } else {
        "The results will be hidden until the Poll is over"
    }

    description = """
        $names

        $results
    """.trimIndent()

    if (settings.deleteAfter != null) {
        val deleteAt = createdAt + settings.deleteAfter!!
        if (deleteAt > Clock.System.now()) {
            field {
                name = "Will end in"
                value = deleteAt.toDiscord(TimestampType.RelativeTime)
            }
        }
    }

    if (highlightWinner) {
        val options = sumUp().groupBy(VoteOption::amount)
        val maxVotes = options.keys.maxOrNull()

        val winners = maxVotes?.let { options[it] } ?: emptyList()

        field {
            name = if (winners.size > 1) "Winners" else "Winner"
            value = if (winners.isEmpty()) "No one voted" else winners.map {
                transformMessageSafe(
                    it.option.option,
                    TransformerContext(guild, kord, false)
                )
            }
                .joinToString(", ")
        }
    }

    if (settings.publicResults) {
        field {
            name = "Privacy Notice"
            value = "The author of this Poll will be able to see, what you have voted for."
        }
    }

    field {
        name = "Total Votes"
        value = totalVotes.toString()
    }

    timestamp = createdAt
}

suspend fun Poll.toPieChartCreateRequest(kord: Kord, guild: GuildBehavior?): PieChartCreateRequest {
    val votes = sumUp()

    return PieChartCreateRequest(
        title,
        512, 512,
        votes.map { (option, count) ->
            Vote(
                count,
                transformMessageSafe(option.option, TransformerContext(guild, kord, false))
            )
        }
    )
}

private val DiscordPartialEmoji.mention: String
    get() =
        if (id == null) {
            name!! // unicode
        } else {
            "<:$name:$id>" // custom emote
        }
