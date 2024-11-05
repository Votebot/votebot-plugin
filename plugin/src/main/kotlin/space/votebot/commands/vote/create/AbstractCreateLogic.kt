package space.votebot.commands.vote.create

import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.InteractionContextType
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.interaction.response.FollowupPermittingInteractionResponseBehavior
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.MessageBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.commands.application.slash.SlashCommandContext
import dev.kordex.core.i18n.types.Key
import dev.schlaubi.mikbot.plugin.api.util.Confirmation
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.translate
import kotlinx.datetime.Clock
import org.litote.kmongo.newId
import space.votebot.common.models.Poll
import space.votebot.common.models.merge
import space.votebot.core.*
import space.votebot.translations.VoteBotTranslations
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

        settings.channel != null -> discordError(VoteBotTranslations.Vote.Create.channelContextError)
        else -> response.toVoteParentChannel(event.interaction.channelId)
    }

    settings.settings.deleteAfter?.let { checkDuration(it) }

    if (settings.answers.size < 2) {
        discordError(VoteBotTranslations.Vote.Create.notEnoughOptions)
    }

    if (isGuildInstall) {
        if (settings.answers.size > 25) {
            discordError(VoteBotTranslations.Vote.Create.tooManyOptions)
        }
    } else if (settings.answers.size > 20) {
        discordError(VoteBotTranslations.Vote.Create.TooManyOptions.userMode)
    }

    val toLongOption = settings.answers.firstOrNull {
        it.length > 50
    }

    if (toLongOption != null) {
        discordError(VoteBotTranslations.Vote.Create.tooLongOption.withOrdinalPlaceholders(toLongOption))
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
        discordError(VoteBotTranslations.Vote.Create.MissingPermissions.bot.withOrdinalPlaceholders(channel.mention))
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
            yesWord = VoteBotTranslations.Common.retry,
            noWord = VoteBotTranslations.Common.cancel,
        ) {
            content = translate(VoteBotTranslations.Vote.Create.dmsDisabled)
        }
        if (agreed) {
            return attemptSendingDMs()
        }
        return false
    }

    return true
}

private fun SlashCommandContext<*, *, *>.confirmation(
    yesWord: Key,
    noWord: Key,
    messageBuilder: suspend MessageBuilder.() -> Unit
): Confirmation {
    return when (this) {
        is EphemeralSlashCommandContext<*, *> -> confirmation(yesWord, noWord, messageBuilder = messageBuilder)
        is PublicSlashCommandContext<*, *> -> confirmation(yesWord, noWord, messageBuilder = messageBuilder)
        else -> error("Unexpected command context: $this")
    }
}

fun SlashCommandContext<*, *, *>.checkDuration(duration: Duration) {
    if (!event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)
        && duration > 10.minutes
    ) {
        if (event.interaction.context == InteractionContextType.Guild) {
            discordError(VoteBotTranslations.Vote.Create.InvalidDuration.guild)
        } else {
            discordError(VoteBotTranslations.Vote.Create.InvalidDuration.dm)
        }
    }
}
