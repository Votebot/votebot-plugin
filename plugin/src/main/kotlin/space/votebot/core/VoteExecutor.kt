package space.votebot.core

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
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.components.types.emoji
import dev.kordex.core.events.EventContext
import dev.kordex.core.extensions.event
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.dm
import dev.schlaubi.mikbot.plugin.api.util.MessageSender
import dev.schlaubi.mikbot.plugin.api.util.translate
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.votebot.common.models.Poll
import space.votebot.translations.VoteBotTranslations
import space.votebot.util.reFetch

private val VoteExecutor = CoroutineScope(Dispatchers.IO + SupervisorJob())

suspend fun Poll.close(
    kord: Kord,
    sendMessage: MessageSender,
    translator: TranslatableContext,
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
            VoteBotDatabase.polls.save(copy(excludedFromScheduling = true))
            if (isRetry) {
                sendMessage {
                    content = translator.translate(VoteBotTranslations.Vote.Close.Error.again)
                }
            } else {
                sendMessage {
                    content = translator.translate(VoteBotTranslations.Vote.Close.Error.generic)
                    embeds = mutableListOf(toEmbed(kord, guild, highlightWinner = true, overwriteHideResults = true))

                    components {
                        ephemeralButton {
                            emoji(Emojis.repeat.toString())
                            label = VoteBotTranslations.Common.retry

                            action {
                                removeAll()
                                edit { components = mutableListOf() }
                                close(kord, ::respond, translator, showChart, guild, isRetry = true)
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
                ack.createEphemeralFollowup {
                    content = translate(VoteBotTranslations.Vote.Close.Failed.missingPermission)
                }
                return
            }
        } else {
            ack.createEphemeralFollowup {
                content = translate(VoteBotTranslations.Vote.Close.Failed.missingPermission)
            }
            return
        }
    }

    poll.close(event.kord, { ack.createEphemeralFollowup { it() } }, this, guild = guild, response = ack)
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
                content = translate(VoteBotTranslations.Vote.votedAlready)
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
                    content = translate(VoteBotTranslations.Vote.tooManyVotes, settings.maxVotes)
                }
                return
            }
        } else { // maxChanges >= 1
            val changes = poll.changes[userId] ?: 0
            if (changes >= settings.maxChanges) {
                ack.createEphemeralFollowup {
                    content = translate(VoteBotTranslations.Vote.tooManyChanges, settings.maxChanges)
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
            content = translate(VoteBotTranslations.Vote.voted, newPoll.options[option])
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
