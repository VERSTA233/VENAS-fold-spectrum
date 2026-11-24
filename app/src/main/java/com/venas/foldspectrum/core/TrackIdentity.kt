package com.venas.foldspectrum.core

/** Display titles can contain live lyrics. A media ID identifies the actual item. */
internal fun trackIdentity(player: String, mediaId: String?, title: String, artist: String): String =
    if (!mediaId.isNullOrBlank()) "$player|id:$mediaId"
    else "$player|text:$title|$artist"
