package space.votebot.commands.vote

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import dev.schlaubi.mikbot.plugin.api.util.discordError
import space.votebot.command.poll
import space.votebot.commands.vote.create.guildOnlyCommand
import space.votebot.common.models.Poll
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.recalculateEmojis
import space.votebot.core.updateMessages
import space.votebot.transformer.TransformerContext
import space.votebot.transformer.transformMessageSafe

class RemoveOptionArguments : Arguments() {
    val poll by poll {
        name = "poll"
        description = "commands.remove_option.arguments.poll.description"
    }

    val position by int {
        name = "position"
        description = "commands.remove_option.arguments.position.description"
    }
}

suspend fun VoteBotModule.removeOptionCommand() = ephemeralSlashCommand(::RemoveOptionArguments) {
    name = "remove-option"
    description = "commands.remove_option.description"
    guildOnlyCommand()

    action {
        val poll = arguments.poll
        if (arguments.position > poll.options.count { it !is Poll.Option.Spacer }) {
            discordError(translate("commands.remove_option.out_of_bounds"))
        }
        val selectedOption = poll.sortedOptions[arguments.position - 1]
        val newOptions = poll.options.mapIndexed { index, option ->
            if (index == selectedOption.index) {
                Poll.Option.Spacer(null)
            } else {
                option
            }
        }
        val newVotes = poll.votes.filterNot { (forOption) ->
            forOption == selectedOption.index
        }
        val newPoll = poll.copy(options = newOptions, votes = newVotes).recalculateEmojis(guild)

        VoteBotDatabase.polls.save(newPoll)
        newPoll.updateMessages(channel.kord, guild!!)
        respond {
            content = translate(
                "commands.remove_option.success",
                arrayOf(
                    transformMessageSafe(
                        selectedOption.option,
                        TransformerContext(guild, this@removeOptionCommand.kord, true)
                    )
                )
            )
        }
    }
}
