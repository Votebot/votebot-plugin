package space.votebot.util

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.followup.FollowupMessage
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import kotlin.time.Duration.Companion.minutes

private val requiredPermissions =
    Permissions(Permission.SendMessages, Permission.EmbedLinks, Permission.AttachFiles, Permission.ViewChannel)

suspend fun <A : Arguments> SlashCommandContext<*, A, *>.checkPermissions(channel: GuildMessageChannel): Boolean {
    val selfPermissions = channel.getEffectivePermissions(channel.kord.selfId)
    if (requiredPermissions !in selfPermissions) {
        sendMissingPermissions("vote.create.missing_permissions.bot", channel, channel.kord.selfId, selfPermissions)
        return false
    }

    val userPermissions = channel.getEffectivePermissions(user.id)
    if ((requiredPermissions - Permission.ViewChannel) !in userPermissions) {
        sendMissingPermissions("vote.create.missing_permissions.user", channel, user.id, userPermissions)
        return false
    }

    return true
}

private suspend fun SlashCommandContext<*, *, *>.sendMissingPermissions(
    translation: String,
    channel: GuildMessageChannel,
    user: Snowflake,
    permissions: Permissions
) {
    suspend fun Boolean.translate() = translate(if (this) "common.yes" else "common.no")

    respond {
        content = translate(translation, arrayOf(channel.mention))

        components(5.minutes) {
            ephemeralButton {
                bundle = command.resolvedBundle
                style = ButtonStyle.Secondary
                label = translate("vote.create.missing_permissions.explainer.label")

                action {
                    val serverPermissions = channel.guild.getMember(user).getPermissions()
                    val isAdministrator =
                        if (Permission.Administrator in serverPermissions) "common.yes" else "common.no"

                    val missingPermissions = (requiredPermissions - permissions).values.map {
                        translate(
                            "vote.create.missing_permissions.explainer.permission",
                            arrayOf(
                                it.translate(this@sendMissingPermissions),
                                (it in serverPermissions).translate(),
                                (it in permissions).translate(),
                            )
                        )
                    }.joinToString("\n")


                    respond {
                        content = translate(
                            "vote.create.missing_permissions.explainer",
                            arrayOf(translate(isAdministrator), missingPermissions)
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
