package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class TrackIdentityTest {
    @Test fun liveLyricsDoNotChangeSongIdentity() {
        assertEquals(
            trackIdentity("netease", "26254689", "Across the river and hide from all the suffering", "Animal Collective"),
            trackIdentity("netease", "26254689", "Like the shades of trees", "Animal Collective"))
    }

    @Test fun realSongAndPlayerChangesStillChangeIdentity() {
        val first = trackIdentity("netease", "1", "Same title", "Same artist")
        assertNotEquals(first, trackIdentity("netease", "2", "Same title", "Same artist"))
        assertNotEquals(first, trackIdentity("poweramp", "1", "Same title", "Same artist"))
    }

    @Test fun absentIdsKeepMetadataFallback() {
        val first = trackIdentity("player", null, "First", "Artist")
        assertEquals(first, trackIdentity("player", "  ", "First", "Artist"))
        assertNotEquals(first, trackIdentity("player", "", "Second", "Artist"))
    }
}
