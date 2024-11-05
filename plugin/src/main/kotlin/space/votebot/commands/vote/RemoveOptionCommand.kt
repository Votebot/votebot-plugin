package space.votebot.commands.vote

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.poll
import space.votebot.commands.vote.create.guildOnlyCommand
import space.votebot.common.models.Poll
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.recalculateEmojis
import space.votebot.core.updateMessages
import space.votebot.transformer.TransformerContext
import space.votebot.transformer.transformMessageSafe
import space.votebot.translations.VoteBotTranslations

class RemoveOptionArguments : Arguments() {
    val poll by poll {
        name = VoteBotTranslations.Commands.RemoveOption.Arguments.Poll.name
        description = VoteBotTranslations.Commands.RemoveOption.Arguments.Poll.description
    }

    val position by int {
        name = VoteBotTranslations.Commands.RemoveOption.Arguments.Position.name
        description = VoteBotTranslations.Commands.RemoveOption.Arguments.Position.description
    }
}

suspend fun VoteBotModule.removeOptionCommand() = ephemeralSlashCommand(::RemoveOptionArguments) {
    name = VoteBotTranslations.Commands.RemoveOption.name
    description = VoteBotTranslations.Commands.RemoveOption.description
    guildOnlyCommand()

    action {
        val poll = arguments.poll
        if (arguments.position > poll.options.count { it !is Poll.Option.Spacer }) {
            discordError(VoteBotTranslations.Commands.RemoveOption.outOfBounds)
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
                VoteBotTranslations.Commands.RemoveOption.success,
                transformMessageSafe(
                    selectedOption.option,
                    TransformerContext(guild, this@removeOptionCommand.kord, true)
                )
            )
        }
    }
}
