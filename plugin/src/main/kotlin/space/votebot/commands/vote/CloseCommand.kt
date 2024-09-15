package space.votebot.commands.vote

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import space.votebot.command.poll
import space.votebot.core.VoteBotModule
import space.votebot.core.close

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

    action {
        if (arguments.poll.close(channel.kord, ::respond, ::translate, arguments.showChart, guild!!)) {
            respond {
                content = translate("commands.close.success")
            }
        }
    }
}


