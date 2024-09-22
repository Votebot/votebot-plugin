package space.votebot.core

import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.ALL
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.KordObject
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.followup.FollowupMessageBehavior
import dev.kord.core.behavior.interaction.followup.PublicFollowupMessageBehavior
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.behavior.interaction.response.FollowupPermittingInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import space.votebot.common.models.Poll

@OptIn(KordUnsafe::class, KordExperimental::class)
fun Poll.Message.toBehavior(kord: Kord): VoteParentChannel.Message {
    val interaction = interaction
    return if (interaction != null) {
        val followUpMessage = PublicFollowupMessageBehavior(
            Snowflake(interaction.id), kord.selfId, interaction.token, Snowflake(channelId), kord, kord.defaultSupplier
        )

        InteractionVoteChannel.Message(followUpMessage)
    } else {
        val guild = guildId ?: error("Missing guildId")
        val behavior = kord.unsafe.message(Snowflake(channelId), Snowflake(messageId))

        GuildVoteParentChannel.Message(behavior, Snowflake(guild))
    }
}

fun GuildMessageChannelBehavior.toVoteParentChannel(): VoteParentChannel =
    GuildVoteParentChannel(this)
fun FollowupPermittingInteractionResponseBehavior.toVoteParentChannel(parentChannelId: Snowflake): VoteParentChannel =
    InteractionVoteChannel(this, parentChannelId)

val EphemeralSlashCommandContext<*, *>.voteParentChannel: VoteParentChannel
    get() {
        return if(event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)) {
            (channel as GuildMessageChannelBehavior).toVoteParentChannel()
        } else {
            interactionResponse.toVoteParentChannel(channel.id)
        }
    }

interface VoteParentChannel : KordObject {
    val id: Snowflake
    val mention: String
        get() = "<#$id>"

    suspend fun createMessage(builder: suspend MessageCreateBuilder.() -> Unit): Message

    interface Message : KordObject {
        val id: Snowflake
        val channelId: Snowflake
        val jumpUrl: String

        suspend fun getEffectivePermissions(userId: Snowflake): Permissions
        suspend fun delete()
        suspend fun edit(builder: suspend MessageModifyBuilder.() -> Unit)
        fun toPollMessage(): Poll.Message
    }
}

interface HasGuild {
    val guildId: Snowflake
}

private data class GuildVoteParentChannel(private val delegate: GuildMessageChannelBehavior) : VoteParentChannel,
    HasGuild,
    KordObject by delegate {
    override val id: Snowflake
        get() = delegate.id
    override val guildId: Snowflake
        get() = delegate.guildId

    override suspend fun createMessage(builder: suspend MessageCreateBuilder.() -> Unit): VoteParentChannel.Message =
        Message(delegate.createMessage { builder() }, delegate.guildId)

    private class Message(private val delegate: MessageBehavior, private val guildId: Snowflake) :
        VoteParentChannel.Message, KordObject by delegate {
        override val id: Snowflake
            get() = delegate.id
        override val channelId: Snowflake
            get() = delegate.channelId
        override val jumpUrl: String
            get() = "https://discord.com/channels/$guildId/${delegate.channelId}/${delegate.id}"

        override fun toPollMessage(): Poll.Message =
            Poll.Message(delegate.id.value, delegate.channelId.value, guildId = guildId.value)

        override suspend fun delete() = delegate.delete()

        override suspend fun getEffectivePermissions(userId: Snowflake): Permissions {
            val channel = delegate.getChannel()
            return when (channel) {
                is ThreadChannel -> channel.getParent().getEffectivePermissions(userId)
                is TopGuildMessageChannel -> channel.getEffectivePermissions(userId)
                else -> error("Unexpected channel type: $channel")
            }
        }

        override suspend fun edit(builder: suspend MessageModifyBuilder.() -> Unit) {
            delegate.edit { builder() }
        }
    }

    companion object {
        fun Message(delegate: MessageBehavior, guildId: Snowflake): VoteParentChannel.Message =
            GuildVoteParentChannel.Message(delegate, guildId)
    }
}

private data class InteractionVoteChannel(
    private val delegate: FollowupPermittingInteractionResponseBehavior,
    private val channelId: Snowflake
) : VoteParentChannel, KordObject by delegate {
    override val id: Snowflake
        get() = channelId

    override suspend fun createMessage(builder: suspend MessageCreateBuilder.() -> Unit): VoteParentChannel.Message =
        Message(delegate.createPublicFollowup { builder() })

    private class Message(private val delegate: FollowupMessageBehavior) : VoteParentChannel.Message,
        KordObject by delegate {
        override val id: Snowflake
            get() = delegate.id
        override val channelId: Snowflake
            get() = delegate.channelId
        override val jumpUrl: String
            get() = "https://discordapp.com/channels/@me/$channelId/$id"

        override fun toPollMessage(): Poll.Message =
            Poll.Message(
                delegate.id.value,
                delegate.channelId.value,
                interaction = Poll.Message.Interaction(delegate.id.value, delegate.token)
            )

        override suspend fun getEffectivePermissions(userId: Snowflake) = Permissions.ALL

        override suspend fun delete() = delegate.delete()

        override suspend fun edit(builder: suspend MessageModifyBuilder.() -> Unit) {
            delegate.edit { builder() }
        }
    }

    companion object {
        fun Message(delegate: FollowupMessageBehavior): VoteParentChannel.Message =
            InteractionVoteChannel.Message(delegate)
    }
}
