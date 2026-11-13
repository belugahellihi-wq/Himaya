package app.himaya

/**
 * Свой список поверх Cloudflare Family DNS (1.1.1.3), который сам режет миллионы взрослых доменов.
 * Здесь — ключевые слова и доменные зоны, чтобы ловить то, что в базах ещё не появилось.
 */
object Blocklist {
    private val words = listOf(
        "porn", "xxx", "hentai", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "brazzers",
        "onlyfans", "rule34", "nsfw", "camgirl", "livejasmin", "chaturbate", "stripchat",
        "bongacams", "spankbang", "eporner", "fansly", "tnaflix", "nhentai", "hqporner", "motherless"
    )
    private val labels = Regex("(^|[.-])(sex|sexy|nude|nudes|milf|bdsm|erotic)([.-]|$)")

    /** Тихо отдаём NXDOMAIN (без экрана): отключает «секретный» DoH у Firefox, через который можно обойти фильтр. */
    val silent = setOf("use-application-dns.net")

    fun isBlocked(host: String): Boolean =
        host.endsWith(".adult") || words.any { host.contains(it) } || labels.containsMatchIn(host)
}
