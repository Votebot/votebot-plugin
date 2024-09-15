package space.votebot.core

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import space.votebot.common.models.Poll
import java.io.ByteArrayInputStream
import java.io.InputStream

private val header = listOf("user_id", "vote_option", "amount").joinToString(separator = ",")

suspend fun Poll.generateCSV(kord: Kord): String = buildString {
    append(header)
    appendLine()
    votes.forEach {
        appendSafe(kord.getUser(Snowflake(it.userId))?.username ?: "<unknown user>").append(it.userId)
        append(',')
        append("${(options[it.forOption] as Poll.Option.ActualOption).option} (${it.forOption + 1})")
        append(',')
        append(it.amount)
        appendLine()
    }
}

private fun StringBuilder.appendSafe(string: String) = if (string.contains("\\s+".toRegex())) {
    append('"').append(string).append('"')
} else {
    append(string)
}

suspend fun Poll.generateCSVFile(kord: Kord): InputStream = ByteArrayInputStream(generateCSV(kord).toByteArray())
