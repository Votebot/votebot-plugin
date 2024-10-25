package space.votebot.commands.vote.create

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.InteractionContextType
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.interaction.response.FollowupPermittingInteractionResponseBehavior
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.MessageBuilder
import dev.schlaubi.mikbot.plugin.api.util.Confirmation
import dev.schlaubi.mikbot.plugin.api.util.confirmation
import dev.schlaubi.mikbot.plugin.api.util.discordError
import kotlinx.datetime.Clock
import org.litote.kmongo.newId
import space.votebot.common.models.Poll
import space.votebot.common.models.merge
import space.votebot.core.*
import space.votebot.util.checkPermissions
import space.votebot.util.voteSafeGuild
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

suspend fun <A> SlashCommandContext<*, A, *>.createVote(response: FollowupPermittingInteractionResponseBehavior)
    where A : Arguments, A : CreateSettings = createVote(response) { arguments }

suspend fun <A : Arguments> SlashCommandContext<*, A, *>.createVote(
    response: FollowupPermittingInteractionResponseBehavior,
    settings: CreateSettings
) = createVote(response) { settings }

suspend fun <A : Arguments> SlashCommandContext<*, A, *>.createVote(
    response: FollowupPermittingInteractionResponseBehavior,
    optionProvider: SlashCommandContext<*, A, *>.() -> CreateSettings
): VoteParentChannel.Message? {
    val kord = getKoin().get<Kord>()
    val settings = optionProvider()
    val guildVoteChannel = guild?.let {
        VoteBotDatabase.guildSettings.findOneByGuild(it.id)?.voteChannelId?.let {
            kord.getChannelOf<GuildMessageChannel>(it)
        }
    }
    val isGuildInstall =
        event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)
    val channel = when {
        isGuildInstall -> {
            val guildChannel = (guildVoteChannel ?: settings.channel ?: this.channel).asChannelOf<GuildMessageChannel>()
            if(!checkPermissions(guildChannel)) return null
            guildChannel.toVoteParentChannel()
        }

        settings.channel != null -> discordError(translate("vote.create.channel_context_error"))
        else -> response.toVoteParentChannel(event.interaction.channelId)
    }

    settings.settings.deleteAfter?.let { checkDuration(it) }

    if (settings.answers.size < 2) {
        discordError(translate("vote.create.not_enough_options"))
    }

    if (isGuildInstall) {
        if (settings.answers.size > 25) {
            discordError(translate("vote.create.too_many_options"))
        }
    } else if (settings.answers.size > 25) {
        discordError(translate("vote.create.too_many_options.user_mode"))
    }

    val toLongOption = settings.answers.firstOrNull {
        it.length > 50
    }

    if (toLongOption != null) {
        discordError(translate("vote.create.too_long_option", arrayOf(toLongOption)))
    }

    val finalSettings = if (settings.settings.complete) {
        settings.settings.merge(null)
    } else {
        val globalSettings = VoteBotDatabase.userSettings.findOneById(user.id)?.settings
        settings.settings.merge(globalSettings)
    }

    if (finalSettings.publicResults && !attemptSendingDMs()) {
        return null
    }

    val emojis = finalSettings.selectEmojis(
        voteSafeGuild,
        settings.answers.size
    )

    val poll = Poll(
        newId<Poll>().toString(),
        guild?.id?.value,
        user.id.value,
        settings.title,
        settings.answers.mapIndexed { index, it ->
            Poll.Option.ActualOption(null, it, emojis.getOrNull(index))
        },
        emptyMap(),
        emptyList(),
        emptyList(),
        Clock.System.now(),
        finalSettings
    )
    val message = try {
        poll.addMessage(channel, addButtons = true, addToDatabase = false, closeButton = !isGuildInstall, guild = guild)
    } catch (_: RequestException) {
        discordError(translate("vote.create.missing_permissions.bot", arrayOf(channel.mention)))
    }
    VoteBotDatabase.polls.save(poll.copy(messages = listOf(message.toPollMessage())))

    if (finalSettings.deleteAfter != null) {
        poll.addExpirationListener(channel.kord)
    }

    return message
}

private suspend fun <A : Arguments> SlashCommandContext<*, A, *>.attemptSendingDMs(): Boolean {
    if (user.getDmChannelOrNull() == null) {
        val (agreed) = confirmation(
            yesWord = translate("vote.create.retry"),
            noWord = translate("vote.create.cancel"),
        ) {
            content = translate("vote.create.dms_disabled")
        }
        if (agreed) {
            return attemptSendingDMs()
        }
        return false
    }

    return true
}

private suspend fun SlashCommandContext<*, *, *>.confirmation(
    yesWord: String,
    noWord: String,
    messageBuilder: suspend MessageBuilder.() -> Unit
): Confirmation {
    return when (this) {
        is EphemeralSlashCommandContext<*, *> -> confirmation(yesWord, noWord, messageBuilder = messageBuilder)
        is PublicSlashCommandContext<*, *> -> confirmation(yesWord, noWord, messageBuilder = messageBuilder)
        else -> error("Unexpected command context: $this")
    }
}

suspend fun SlashCommandContext<*, *, *>.checkDuration(duration: Duration) {
    if (!event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)
        && duration > 10.minutes
    ) {
        if (event.interaction.context == InteractionContextType.Guild) {
            discordError(translate("vote.create.invalid_duration.guild", "votebot"))
        } else {
            discordError(translate("vote.create.invalid_duration.dm", "votebot"))
        }
    }
}
