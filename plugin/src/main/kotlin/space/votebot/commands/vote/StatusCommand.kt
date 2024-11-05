package space.votebot.commands.vote

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kord.common.entity.ApplicationIntegrationType
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.poll
import space.votebot.commands.vote.create.voteCommandContext
import space.votebot.core.VoteBotModule
import space.votebot.core.addMessage
import space.votebot.core.toEmbed
import space.votebot.core.voteParentChannel
import space.votebot.translations.VoteBotTranslations

class StatusArguments : Arguments() {
    val poll by poll {
        name = VoteBotTranslations.Commands.Status.Arguments.Poll.name
        description = VoteBotTranslations.Commands.Status.Arguments.Poll.description
    }

    val liveMessage by defaultingBoolean {
        name = VoteBotTranslations.Commands.Status.Arguments.Live.name
        description = VoteBotTranslations.Commands.Status.Arguments.Live.description
        defaultValue = false
    }
}

suspend fun VoteBotModule.statusCommand() = ephemeralSlashCommand(::StatusArguments) {
    name = VoteBotTranslations.Commands.Status.name
    description = VoteBotTranslations.Commands.Status.description
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
                content = translate(VoteBotTranslations.Commands.Status.success)
            }
        } else {
            discordError(VoteBotTranslations.Commands.Status.userInstall)
        }
    }
}
