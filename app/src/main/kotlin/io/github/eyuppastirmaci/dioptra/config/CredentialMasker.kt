package io.github.eyuppastirmaci.dioptra.config

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CredentialMasker {
    const val MASK: String = "******"

    private val redisUrlPattern = Regex("""\brediss?://[^\s,'"<>]+""")
    private val passwordAssignmentPattern = Regex(
        pattern = """(?i)\b(password|pass|pwd)\s*([=:])\s*([^\s,;]+)""",
    )

    fun maskSensitiveValues(value: String): String {
        val withoutRedisSecrets = redisUrlPattern.replace(value) { matchResult ->
            maskRedisUrl(matchResult.value)
        }

        return passwordAssignmentPattern.replace(withoutRedisSecrets) { matchResult ->
            "${matchResult.groupValues[1]}${matchResult.groupValues[2]}$MASK"
        }
    }

    fun maskRedisUrl(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        val scheme = uri.scheme ?: return value

        if (scheme != "redis" && scheme != "rediss") {
            return value
        }

        val userInfo = uri.rawUserInfo ?: return value
        val maskedUserInfo = maskUserInfo(userInfo)
        val rawAuthority = uri.rawAuthority ?: return value
        val maskedAuthority = rawAuthority.replaceFirst(userInfo, maskedUserInfo)

        return buildString {
            append(scheme)
            append("://")
            append(maskedAuthority)
            append(uri.rawPath.orEmpty())
            if (uri.rawQuery != null) {
                append('?')
                append(uri.rawQuery)
            }
            if (uri.rawFragment != null) {
                append('#')
                append(uri.rawFragment)
            }
        }
    }

    internal fun encodeUriPart(value: String): String {
        return URLEncoder
            .encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
    }

    internal fun decodeUriPart(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    private fun maskUserInfo(userInfo: String): String {
        val separatorIndex = userInfo.indexOf(':')

        return if (separatorIndex == -1) {
            userInfo
        } else {
            "${userInfo.substring(0, separatorIndex)}:$MASK"
        }
    }
}
