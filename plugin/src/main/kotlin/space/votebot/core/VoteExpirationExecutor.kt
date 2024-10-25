package space.votebot.core

import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.schlaubi.mikbot.core.health.Config
import dev.schlaubi.mikbot.plugin.api.config.Environment
import dev.schlaubi.mikbot.plugin.api.pluginSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.litote.kmongo.and
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.not
import space.votebot.common.models.FinalPollSettings
import space.votebot.common.models.Poll
import dev.schlaubi.mikbot.plugin.api.config.Config as BotConfig

internal val ExpirationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val expirationCache = mutableMapOf<String, Job>()

suspend fun rescheduleAllPollExpires(kord: Kord) = coroutineScope {
    if (Config.POD_ID != 0 || !Config.ENABLE_SCALING || BotConfig.ENVIRONMENT != Environment.PRODUCTION)
    VoteBotDatabase.polls.find(and(not(Poll::settings / FinalPollSettings::deleteAfter eq null), Poll::excludedFromScheduling eq false))
        .toFlow()
        .onEach { poll ->
            poll.addExpirationListener(kord)
        }.launchIn(this)
}

@OptIn(KordUnsafe::class, KordExperimental::class)
fun Poll.addExpirationListener(kord: Kord) {
    val duration = settings.deleteAfter ?: error("This vote does not have an expiration Date")
    val expireAt = createdAt + duration

    expirationCache[id]?.cancel()
    expirationCache[id] = ExpirationScope.launch {
        val timeUntilExpiry = expireAt - Clock.System.now()
        if (!timeUntilExpiry.isNegative()) {
            delay(timeUntilExpiry)
        }

        close(kord, {
            kord.getUser(Snowflake(authorId))!!.dm {
                it()
            }!!
        }, pluginSystem::translate, guild = guildId?.let { kord.unsafe.guild(Snowflake(it)) })
    }
}
