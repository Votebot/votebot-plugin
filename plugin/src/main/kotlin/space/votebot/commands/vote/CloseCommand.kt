package space.votebot.commands.vote

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.edit
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.extensions.ephemeralMessageCommand
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.utils.hasPermission
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import io.github.oshai.kotlinlogging.KotlinLogging
import space.votebot.command.poll
import space.votebot.commands.vote.create.guildOnlyCommand
import space.votebot.commands.vote.create.voteCommandContext
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.close
import space.votebot.core.findOneByMessage
import space.votebot.translations.VoteBotTranslations

private val LOG = KotlinLogging.logger { }

class CloseArguments : Arguments() {
    val poll by poll {
        name = VoteBotTranslations.Commands.Close.Arguments.Poll.name
        description = VoteBotTranslations.Commands.Close.Arguments.Poll.description
    }
    val showChart by optionalBoolean {
        name = VoteBotTranslations.Commands.Close.Arguments.ShowChart.name
        description = VoteBotTranslations.Commands.Close.Arguments.ShowChart.description
    }
}

suspend fun VoteBotModule.closeCommand() = ephemeralSlashCommand(::CloseArguments) {
    name = VoteBotTranslations.Commands.Close.name
    description = VoteBotTranslations.Commands.Close.description
    guildOnlyCommand()

    action {
        if (arguments.poll.close(channel.kord, ::respond, this, arguments.showChart, guild)) {
            respond {
                content = translate(VoteBotTranslations.Commands.Close.success)
            }
        }
    }
}

suspend fun VoteBotModule.closeMessageCommand() = ephemeralMessageCommand {
    name = VoteBotTranslations.Commands.Context.Close.name
    voteCommandContext()

    action {
        val poll = VoteBotDatabase.polls.findOneByMessage(targetMessages.first())
            ?: discordError(VoteBotTranslations.Commands.Generic.pollNotFound)

        if (poll.authorId != user.id.value && getMember()?.asMember()?.hasPermission(Permission.ManageGuild) != true) {
            discordError(VoteBotTranslations.Commands.Generic.noPermission)
        }

        targetMessages.first().edit { content = "test2134" }

        poll.close(event.kord, ::respond, this, showChart = null, guild = null)
    }
}
