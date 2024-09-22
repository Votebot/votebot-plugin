package space.votebot.util

import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildChannel
import space.votebot.common.models.Poll

fun Message.toPollMessage() = Poll.Message(
    id.value, channelId.value, (channel as? GuildMessageChannelBehavior)?.guildId?.value
)

val Poll.Message.jumpUrl: String
    get() = "https://discord.com/channels/${guildId ?: "@me"}/$channelId/$messageId"
