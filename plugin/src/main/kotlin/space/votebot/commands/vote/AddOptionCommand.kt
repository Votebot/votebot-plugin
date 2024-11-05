package space.votebot.commands.vote

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.poll
import space.votebot.commands.vote.create.voteCommandContext
import space.votebot.common.models.Poll
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.recalculateEmojis
import space.votebot.core.updateMessages
import space.votebot.translations.VoteBotTranslations

class AddOptionArguments : Arguments() {
    val poll by poll {
        name = VoteBotTranslations.Commands.AddOption.Arguments.Poll.name
        description = VoteBotTranslations.Commands.AddOption.Arguments.Poll.description
    }

    val option by string {
        name = VoteBotTranslations.Commands.AddOption.Arguments.Option.name
        description = VoteBotTranslations.Commands.AddOption.Arguments.Option.description
    }
    val position by optionalInt {
        name = VoteBotTranslations.Commands.AddOption.Arguments.Position.name
        description = VoteBotTranslations.Commands.AddOption.Arguments.Position.description
    }
}

suspend fun VoteBotModule.addOptionCommand() = ephemeralSlashCommand(::AddOptionArguments) {
    name = VoteBotTranslations.Commands.AddOption.name
    description = VoteBotTranslations.Commands.AddOption.description
    voteCommandContext()

    action {
        val poll = arguments.poll
        if (arguments.option.length > 50) {
            discordError(VoteBotTranslations.Vote.Create.tooLongOption.withOrdinalPlaceholders(arguments.option))
        }

        val option = Poll.Option.ActualOption(
            arguments.position?.minus(1),
            arguments.option,
            null
        )

        val newPoll = poll.copy(options = poll.options + option).recalculateEmojis(guild)

        VoteBotDatabase.polls.save(newPoll)
        newPoll.updateMessages(channel.kord, guild)
        respond {
            content = translate(VoteBotTranslations.Commands.AddOption.success, arguments.option)
        }
    }
}
