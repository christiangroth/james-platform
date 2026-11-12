package de.chrgroth.james.platform.adapter.`in`.web

/**
 * Return URLs ("go back to where I came from") are only ever followed if they are a local path of the user UI, so the parameter cannot be abused
 * as an open redirect.
 */
object LocalReturnUrl {
  private const val UI_PREFIX = "/ui/"

  /** [candidate] if it is a safe local path below `/ui/` (no scheme, host, protocol-relative or backslash tricks, no control characters), otherwise null. */
  fun validated(candidate: String?): String? {
    val url = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val safe = url.startsWith(UI_PREFIX) &&
      url.none { it == '\\' || it.isISOControl() || it.isWhitespace() } &&
      !url.contains("://") &&
      !url.contains("/../") &&
      !url.endsWith("/..")
    return url.takeIf { safe }
  }
}
