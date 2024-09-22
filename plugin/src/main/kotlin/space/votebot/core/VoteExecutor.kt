package space.votebot.core

import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.interaction.response.EphemeralMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.createEphemeralFollowup
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.x.emoji.Emojis
import dev.schlaubi.mikbot.plugin.api.util.MessageSender
import dev.schlaubi.mikbot.plugin.api.util.Translator
import dev.schlaubi.mikbot.plugin.api.util.discordError
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.votebot.common.models.Poll
import space.votebot.util.reFetch

private val VoteExecutor = CoroutineScope(Dispatchers.IO + SupervisorJob())

suspend fun Poll.close(
    kord: Kord,
    sendMessage: MessageSender,
    translate: Translator,
    showChart: Boolean? = null,
    guild: GuildBehavior?,
    isRetry: Boolean = false,
    response: EphemeralMessageInteractionResponseBehavior? = null,
): Boolean {
    with(reFetch()) {
        val messageUpdateFailed = !updateMessages(
            kord,
            removeButtons = true,
            highlightWinner = true,
            guild = guild,
            showChart = showChart,
            deleteFailingMessages = false,
            response = response
        )

        if (messageUpdateFailed) {
            if (isRetry) {
                sendMessage {
                    content = translate("vote.close.error.again", "votebot")
                }
            } else {
                sendMessage {
                    content = translate("vote.close.error.generic", "votebot")
                    embeds = mutableListOf(toEmbed(kord, guild, highlightWinner = true, overwriteHideResults = true))

                    components {
                        ephemeralButton {
                            emoji(Emojis.repeat.toString())
                            label = translate("common.retry", "votebot")

                            action {
                                removeAll()
                                edit { components = mutableListOf() }
                                close(kord, ::respond, translate, showChart, guild, isRetry = true)
                            }
                        }
                    }

                    if (settings.publicResults) {
                        addFile("votes.csv", ChannelProvider(block = generateCSVFile(kord)::toByteReadChannel))
                    }
                }
            }
        }

        if (settings.publicResults && !messageUpdateFailed && !isRetry) {
            kord.getUser(Snowflake(authorId))?.dm {
                content = "This message contains the requested vote statistic for your poll: $title ($id)"

                addFile("votes.csv", ChannelProvider(block = generateCSVFile(kord)::toByteReadChannel))
            }
        }
        if (!messageUpdateFailed || isRetry) {
            VoteBotDatabase.polls.deleteOneById(id)
        }

        return !messageUpdateFailed
    }
}

@OptIn(KordUnsafe::class, KordExperimental::class)
suspend fun VoteBotModule.voteExecutor() = event<ButtonInteractionCreateEvent> {
    action {
        VoteExecutor.launch {
            val guild = (event as? GuildButtonInteractionCreateEvent)?.interaction?.guild
            onClose(guild)
            onVote(guild)
        }
    }
}

private suspend fun EventContext<ButtonInteractionCreateEvent>.onClose(guild: GuildBehavior?) {
    val interaction = event.interaction
    if (interaction.componentId != "close") return
    val ack = interaction.deferEphemeralMessageUpdate()

    val message = interaction.message
    val poll = VoteBotDatabase.polls.findOneByMessage(message) ?: run {
        ack.createEphemeralFollowup { content = "This message is invalid" }
        return
    }

    if (poll.authorId != interaction.user.id.value) {
        if (guild != null) {
            val channel = interaction.channel.asChannel()
            val permissionChannel = if (channel is TopGuildMessageChannel) {
                channel
            } else {
                (channel as ThreadChannel).parent
            }
            val permissions = permissionChannel.asChannel().getEffectivePermissions(event.kord.selfId)

            if (Permission.ManageMessages !in permissions) {
                discordError(translate("commands.generic.no_permission"))
            }
        } else {
            discordError(translate("commands.generic.no_permission"))
        }
    }

    poll.close(event.kord, { ack.createEphemeralFollowup { it() } }, ::translate, guild = guild, response = ack)
}

private suspend fun EventContext<ButtonInteractionCreateEvent>.onVote(guild: GuildBehavior?) {
    val interaction = event.interaction
    if (!interaction.componentId.startsWith("vote_")) return
    val ack = interaction.deferEphemeralMessageUpdate()

    val message = interaction.message
    val poll = VoteBotDatabase.polls.findOneByMessage(message) ?: run {
        ack.createEphemeralFollowup { content = "This message is invalid" }
        return
    }

    val option = interaction.componentId.substringAfter("vote_").toIntOrNull() ?: run {
        ack.createEphemeralFollowup { content = "Unexpected component id: ${interaction.componentId}" }
        return
    }

    val userId = interaction.user.id.value
    val userVotes = poll.votes.asSequence()
        .filter { it.userId == userId }
        .sumOf(Poll.Vote::amount)

    val newPoll = if (userVotes > 0) {
        val settings = poll.settings
        if (settings.maxVotes == 1 && settings.maxChanges == 0) {
            ack.createEphemeralFollowup {
                content = translate("vote.voted_already")
            }
            return
        } else if (settings.maxChanges == 0) { // maxVotes > 1
            if (settings.maxVotes > userVotes) {
                val existingVote =
                    poll.votes.firstOrNull { it.userId == userId && it.forOption == option }
                        ?: Poll.Vote(
                            option,
                            userId,
                            0
                        )

                val newVote = existingVote.copy(amount = existingVote.amount + 1)

                poll.copy(votes = poll.votes - existingVote + newVote)
            } else {
                ack.createEphemeralFollowup {
                    content = translate("vote.too_many_votes", arrayOf(settings.maxVotes))
                }
                return
            }
        } else { // maxChanges >= 1
            val changes = poll.changes[userId] ?: 0
            if (changes >= settings.maxChanges) {
                ack.createEphemeralFollowup {
                    content = translate("vote.too_many_changes", arrayOf(settings.maxChanges))
                }
                return
            }
            val oldVote = poll.votes.first { it.userId == userId }
            val newVote = Poll.Vote(option, userId)

            poll.copy(
                votes = poll.votes - oldVote + newVote,
                changes = poll.changes + (userId to changes + 1)
            )
        }
    } else {
        poll.copy(votes = poll.votes + Poll.Vote(option, userId))
    }

    VoteBotDatabase.polls.save(newPoll)
    if (poll.settings.hideResults) {
        ack.createEphemeralFollowup {
            content = translate("vote.voted", arrayOf(newPoll.options[option]))
        }
    }
    // Update the message the suer clicked on using the interaction API
    // This way we can use an up-to-date interaction token
    ack.edit {
        // Only set the embeds field here
        // since we do not need to update buttons
        // as the options have not changed
        embeds = mutableListOf(newPoll.toEmbed(interaction.kord, guild))
    }
    newPoll.updateMessages(interaction.kord, guild, exceptFor = listOf(interaction.message.id.value))
}
