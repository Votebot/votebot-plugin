package space.votebot.util

import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommandContext
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.interaction.GuildInteraction

val SlashCommandContext<*, *, *>.voteSafeGuild: GuildBehavior?
    get() = (event.interaction as? GuildInteraction)?.guild
        ?.takeIf { event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall) }
