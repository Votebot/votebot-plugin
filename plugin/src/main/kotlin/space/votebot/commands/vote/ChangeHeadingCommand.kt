package space.votebot.commands.vote

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.poll
import space.votebot.commands.vote.create.guildOnlyCommand
import space.votebot.core.VoteBotModule
import space.votebot.core.updateMessages
import space.votebot.translations.VoteBotTranslations

class ChangeHeadingArguments : Arguments() {
    val poll by poll {
        name = VoteBotTranslations.Commands.ChangeHeading.Arguments.Poll.name
        description = VoteBotTranslations.Commands.ChangeHeading.Arguments.Poll.description
    }

    val heading by string {
        name = VoteBotTranslations.Commands.ChangeHeading.Arguments.NewHeading.name
        description = VoteBotTranslations.Commands.ChangeHeading.Arguments.NewHeading.description
    }
}

suspend fun VoteBotModule.changeHeadingCommand() = ephemeralSlashCommand(::ChangeHeadingArguments) {
    name = VoteBotTranslations.Commands.ChangeHeading.name
    description = VoteBotTranslations.Commands.ChangeHeading.description
    guildOnlyCommand()

    action {
        val poll = arguments.poll
        val newPoll = poll.copy(title = arguments.heading)
        respond {
            content = translate(VoteBotTranslations.Commands.ChangeHeading.success, newPoll.title)
        }

        if (newPoll.title != poll.title) {
            newPoll.updateMessages(channel.kord, guild)
        }
    }
}
