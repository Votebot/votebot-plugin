package space.votebot.util

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.commands.application.slash.SlashCommandContext
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.utils.translate
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.followup.FollowupMessage
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kordex.core.i18n.types.Key
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.translations.VoteBotTranslations
import kotlin.time.Duration.Companion.minutes

private val requiredPermissions =
    Permissions(Permission.SendMessages, Permission.EmbedLinks, Permission.AttachFiles, Permission.ViewChannel)

suspend fun <A : Arguments> SlashCommandContext<*, A, *>.checkPermissions(channel: GuildMessageChannel): Boolean {
    val selfPermissions = channel.getEffectivePermissions(channel.kord.selfId)
    if (requiredPermissions !in selfPermissions) {
        sendMissingPermissions(VoteBotTranslations.Vote.Create.MissingPermissions.bot, channel, channel.kord.selfId, selfPermissions)
        return false
    }

    val userPermissions = channel.getEffectivePermissions(user.id)
    if ((requiredPermissions - Permission.ViewChannel) !in userPermissions) {
        sendMissingPermissions(VoteBotTranslations.Vote.Create.MissingPermissions.user, channel, user.id, userPermissions)
        return false
    }

    return true
}

private suspend fun SlashCommandContext<*, *, *>.sendMissingPermissions(
    translation: Key,
    channel: GuildMessageChannel,
    user: Snowflake,
    permissions: Permissions
) {
    suspend fun Boolean.translate() =
        translate(if (this) VoteBotTranslations.Common.yes else VoteBotTranslations.Common.no)

    respond {
        content = translate(translation, channel.mention)

        components(5.minutes) {
            ephemeralButton {
                style = ButtonStyle.Secondary
                label = VoteBotTranslations.Vote.Create.MissingPermissions.Explainer.label

                action {
                    val serverPermissions = channel.guild.getMember(user).getPermissions()
                    val isAdministrator = (Permission.Administrator in serverPermissions).translate()

                    val missingPermissions = (requiredPermissions - permissions).values.map {
                        translate(
                            VoteBotTranslations.Vote.Create.MissingPermissions.Explainer.permission,
                            arrayOf(
                                it.translate(this@sendMissingPermissions),
                                (it in serverPermissions).translate(),
                                (it in permissions).translate(),
                            )
                        )
                    }.joinToString("\n")


                    respond {
                        content = translate(
                            VoteBotTranslations.Vote.Create.MissingPermissions.explainer,
                            isAdministrator, missingPermissions
                        )
                    }
                }
            }
        }
    }
}


private suspend fun GuildMessageChannel.getEffectivePermissions(user: Snowflake) = when (this) {
    is TopGuildMessageChannel -> getEffectivePermissions(user)
    is ThreadChannel -> parent.asChannel().getEffectivePermissions(user)
    else -> error("Could not determine permissions for channel type ${this::class.simpleName}")
}

suspend fun SlashCommandContext<*, *, *>.respond(build: suspend MessageCreateBuilder.() -> Unit): FollowupMessage {
    return when (this) {
        is EphemeralSlashCommandContext<*, *> -> respond(build)
        is PublicSlashCommandContext<*, *> -> respond(build)
        else -> error("Unsupported slash command context: $this")
    }
}
