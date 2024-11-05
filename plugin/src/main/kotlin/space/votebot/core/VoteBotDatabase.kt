package space.votebot.core

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kordex.core.koin.KordExKoinComponent
import dev.schlaubi.mikbot.plugin.api.io.getCollection
import dev.schlaubi.mikbot.plugin.api.util.database
import org.litote.kmongo.and
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.div
import org.litote.kmongo.eq
import space.votebot.common.models.Poll
import space.votebot.models.GuildSettings
import space.votebot.models.UserSettings
import space.votebot.util.toPollMessage

object VoteBotDatabase : KordExKoinComponent {
    val polls = database.getCollection<Poll>("polls")
    val userSettings = database
        .getCollection<UserSettings>("user_settings")
    val guildSettings = database
        .getCollection<GuildSettings>("guild_settings")
}

suspend fun CoroutineCollection<Poll>.findOneByMessage(pollMessage: Poll.Message) =
    findOne(
        and(
            Poll::messages / Poll.Message::messageId eq pollMessage.messageId,
            Poll::messages / Poll.Message::channelId eq pollMessage.channelId,
        )
    )

suspend fun CoroutineCollection<Poll>.findOneByMessage(message: Message) = findOneByMessage(message.toPollMessage())

suspend fun CoroutineCollection<GuildSettings>.findOneByGuild(guildId: Snowflake) =
    findOne(GuildSettings::guildId eq guildId)
