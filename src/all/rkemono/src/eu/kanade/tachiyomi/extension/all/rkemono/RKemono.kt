package eu.kanade.tachiyomi.extension.all.rkemono

import eu.kanade.tachiyomi.multisrc.kemono.Kemono

class RKemono : Kemono("Kemono", "https://kemono.cr", "all") {
    override val getTypes = listOf(
        "Patreon",
        "Pixiv Fanbox",
        "Discord",
        "Fantia",
        "Afdian",
        "Boosty",
        "Gumroad",
        "SubscribeStar",
    )
}
