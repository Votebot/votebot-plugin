package space.votebot.commands.guild

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.channel
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.schlaubi.mikbot.plugin.api.settings.SettingsModule
import dev.schlaubi.mikbot.plugin.api.settings.guildAdminOnly
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import org.litote.kmongo.newId
import space.votebot.core.VoteBotDatabase
import space.votebot.core.findOneByGuild
import space.votebot.models.GuildSettings
import space.votebot.translations.VoteBotTranslations
import space.votebot.util.checkPermissions

class SetVoteChannelArguments : Arguments() {
    val channel by channel {
        name = VoteBotTranslations.Commands.Settings.SetVoteChannel.Arguments.Channel.name
        description = VoteBotTranslations.Commands.Settings.SetVoteChannel.Arguments.Channel.description
    }
}

suspend fun SettingsModule.addGuildSettingsCommand() = ephemeralSlashCommand {
    name = VoteBotTranslations.Commands.Settings.SetVoteChannel.name
    description = VoteBotTranslations.Commands.Settings.SetVoteChannel.description
    guildAdminOnly()
    allowedInstallTypes.add(ApplicationIntegrationType.GuildInstall)

    ephemeralSubCommand(::SetVoteChannelArguments) {
        name = VoteBotTranslations.Commands.Settings.SetVoteChannel.Arguments.Channel.name
        description = VoteBotTranslations.Commands.Settings.SetVoteChannel.Arguments.Channel.description

        action {
            val channel = arguments.channel.asChannelOfOrNull<TopGuildMessageChannel>()
                ?: discordError(VoteBotTranslations.Commands.Create.invalidChannel)
            if (!checkPermissions(channel)) return@action

            val guildSettings =
                VoteBotDatabase.guildSettings.findOneByGuild(guild!!.id) ?: GuildSettings(newId(), guild!!.id, null)
            VoteBotDatabase.guildSettings.save(guildSettings.copy(voteChannelId = channel.id))
            respond {
                content = translate(VoteBotTranslations.Vote.Settings.voteChannelUpdated)
            }
        }
    }

    ephemeralSubCommand {
        name = VoteBotTranslations.Commands.Settings.RemoveVoteChannel.name
        description = VoteBotTranslations.Commands.Settings.RemoveVoteChannel.description

        action {
            val guildSettings =
                VoteBotDatabase.guildSettings.findOneByGuild(guild!!.id) ?: GuildSettings(newId(), guild!!.id, null)
            if (guildSettings.voteChannelId == null) {
                respond {
                    content = translate(VoteBotTranslations.Vote.Settings.noChannelDefined)
                }
                return@action
            }

            VoteBotDatabase.guildSettings.save(guildSettings.copy(voteChannelId = null))
            respond {
                content = translate(VoteBotTranslations.Vote.Settings.voteChannelRemoved)
            }
        }
    }
}
