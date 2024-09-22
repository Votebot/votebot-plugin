package space.votebot.commands.vote

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.extensions.ephemeralMessageCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.edit
import dev.schlaubi.mikbot.plugin.api.util.discordError
import io.github.oshai.kotlinlogging.KotlinLogging
import space.votebot.command.poll
import space.votebot.commands.vote.create.guildOnlyCommand
import space.votebot.commands.vote.create.voteCommandContext
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.close
import space.votebot.core.findOneByMessage

private val LOG = KotlinLogging.logger { }

class CloseArguments : Arguments() {
    val poll by poll {
        name = "poll"
        description = "commands.close.arguments.poll.description"
    }
    val showChart by optionalBoolean {
        name = "show-chart"
        description = "commands.close.arguments.show_chart.description"
    }
}

suspend fun VoteBotModule.closeCommand() = ephemeralSlashCommand(::CloseArguments) {
    name = "close"
    description = "commands.close.description"
    guildOnlyCommand()

    action {
        if (arguments.poll.close(channel.kord, ::respond, ::translate, arguments.showChart, guild)) {
            respond {
                content = translate("commands.close.success")
            }
        }
    }
}

suspend fun VoteBotModule.closeMessageCommand() = ephemeralMessageCommand {
    name = "commands.context.close.name"
    voteCommandContext()

    action {
        val poll = VoteBotDatabase.polls.findOneByMessage(targetMessages.first())
            ?: discordError(translate("commands.generic.poll_not_found"))

        if (poll.authorId != user.id.value && getMember()?.asMember()?.hasPermission(Permission.ManageGuild) != true) {
            discordError(translate("commands.generic.no_permission"))
        }

        targetMessages.first().edit { content = "test2134" }

        poll.close(event.kord, ::respond, ::translate, showChart = null, guild = null)
    }
}
