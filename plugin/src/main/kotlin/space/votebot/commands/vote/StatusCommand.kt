package space.votebot.commands.vote

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import dev.kord.common.entity.ApplicationIntegrationType
import dev.schlaubi.mikbot.plugin.api.util.discordError
import space.votebot.command.poll
import space.votebot.commands.vote.create.voteCommandContext
import space.votebot.core.VoteBotModule
import space.votebot.core.addMessage
import space.votebot.core.toEmbed
import space.votebot.core.voteParentChannel

class StatusArguments : Arguments() {
    val poll by poll {
        name = "poll"
        description = "commands.status.arguments.poll.description"
    }

    val liveMessage by defaultingBoolean {
        name = "live"
        description = "commands.status.arguments.live.description"
        defaultValue = false
    }
}

suspend fun VoteBotModule.statusCommand() = ephemeralSlashCommand(::StatusArguments) {
    name = "status"
    description = "commands.status.description"
    voteCommandContext()

    action {
        val poll = arguments.poll
        if (!arguments.liveMessage) {
            respond {
                embeds = mutableListOf(poll.toEmbed(channel.kord, guild))
            }
        } else if (event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)) {
            poll.addMessage(voteParentChannel, addButtons = true, addToDatabase = true, closeButton = false, guild = guild)

            respond {
                content = translate("commands.status.success")
            }
        } else {
            discordError(translate("commands.status.user_install"))
        }
    }
}
