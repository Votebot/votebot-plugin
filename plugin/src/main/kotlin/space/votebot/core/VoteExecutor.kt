package space.votebot.core

import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.interaction.response.createEphemeralFollowup
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.votebot.common.models.Poll
import space.votebot.util.reFetch

private val VoteExecutor = CoroutineScope(Dispatchers.IO + SupervisorJob())

suspend fun Poll.close(kord: Kord, showChart: Boolean? = null, guild: GuildBehavior) {
    with(reFetch()) {
        updateMessages(kord, removeButtons = true, highlightWinner = true, guild = guild, showChart = showChart)

        VoteBotDatabase.polls.deleteOneById(id)

        if (settings.publicResults) {
            kord.getUser(Snowflake(authorId))?.dm {
                content = "This message contains the requested vote statistic for your poll: $title ($id)"

                addFile("votes.csv", ChannelProvider(block = generateCSVFile(kord)::toByteReadChannel))
            }
        }
    }
}

@OptIn(KordUnsafe::class, KordExperimental::class)
suspend fun VoteBotModule.voteExecutor() = event<GuildButtonInteractionCreateEvent> {
    action {
        VoteExecutor.launch {
            onVote(kord.unsafe.guild(event.interaction.guildId))
        }
    }
}

private suspend fun EventContext<GuildButtonInteractionCreateEvent>.onVote(guild: GuildBehavior) {
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
    newPoll.updateMessages(interaction.kord, guild)
// TODO: discuss this
//    if (poll.settings.hideResults) {
//        ack.followUpEphemeral {
//            embeds.add(newPoll.toEmbed(ack.kord, highlightWinner = false, overwriteHideResults = true, guild = guild))
//        }
//    }
}
