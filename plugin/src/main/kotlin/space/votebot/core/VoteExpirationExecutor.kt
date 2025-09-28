package space.votebot.core

import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kordex.core.i18n.SupportedLocales
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.dm
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import org.litote.kmongo.and
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.not
import space.votebot.common.models.FinalPollSettings
import space.votebot.common.models.Poll
import java.util.*

internal val ExpirationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val expirationCache = mutableMapOf<String, Job>()

suspend fun rescheduleAllPollExpires(kord: Kord) = coroutineScope {
    VoteBotDatabase.polls.find(and(not(Poll::settings / FinalPollSettings::deleteAfter eq null), Poll::excludedFromScheduling eq false))
        .toFlow()
        .filter {
            if (it.guildId == null) kord.gateway.gateways.containsKey(0)
            else ((it.guildId!! shr 22) % kord.resources.shards.totalShards.toUInt()).toInt() in kord.gateway.gateways.keys
        }
        .onEach { poll ->
            poll.addExpirationListener(kord)
        }.launchIn(this)
}

@OptIn(KordUnsafe::class, KordExperimental::class)
fun Poll.addExpirationListener(kord: Kord) {
    val translatableContext = object : TranslatableContext {
        override var resolvedLocale: Locale? = SupportedLocales.ENGLISH
        override suspend fun getLocale(): Locale  = SupportedLocales.ENGLISH
    }
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
        }, translatableContext, guild = guildId?.let { kord.unsafe.guild(Snowflake(it)) })
    }
}
