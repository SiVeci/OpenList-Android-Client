package io.openlist.client.feature.share

import io.openlist.client.core.model.Share
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareListFilterTest {

    @Test
    fun `status filter keeps all shares by default`() {
        val shares = listOf(
            share(id = "a", enabled = true),
            share(id = "b", enabled = false),
        )

        val result = shares.filterForShareList("", ShareStatusFilter.ALL)

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `enabled filter uses enabled flag even when share is expired`() {
        val shares = listOf(
            share(id = "enabled-expired", enabled = true, expiresAt = 1L),
            share(id = "disabled", enabled = false),
        )

        val result = shares.filterForShareList("", ShareStatusFilter.ENABLED)

        assertEquals(listOf("enabled-expired"), result.map { it.id })
    }

    @Test
    fun `disabled filter excludes enabled shares`() {
        val shares = listOf(
            share(id = "enabled", enabled = true),
            share(id = "disabled", enabled = false),
        )

        val result = shares.filterForShareList("", ShareStatusFilter.DISABLED)

        assertEquals(listOf("disabled"), result.map { it.id })
    }

    @Test
    fun `query matches name path or id ignoring case`() {
        val shares = listOf(
            share(id = "alpha-id", name = "Release Notes", paths = listOf("/docs/release.md")),
            share(id = "beta-id", name = "Photos", paths = listOf("/albums/summer")),
            share(id = "Gamma-Link", name = null, paths = listOf("/misc")),
        )

        assertEquals(
            listOf("alpha-id"),
            shares.filterForShareList("release", ShareStatusFilter.ALL).map { it.id },
        )
        assertEquals(
            listOf("beta-id"),
            shares.filterForShareList("SUMMER", ShareStatusFilter.ALL).map { it.id },
        )
        assertEquals(
            listOf("Gamma-Link"),
            shares.filterForShareList("gamma", ShareStatusFilter.ALL).map { it.id },
        )
    }

    @Test
    fun `query and status filter are combined`() {
        val shares = listOf(
            share(id = "enabled-doc", enabled = true, paths = listOf("/docs/a.txt")),
            share(id = "disabled-doc", enabled = false, paths = listOf("/docs/b.txt")),
            share(id = "disabled-photo", enabled = false, paths = listOf("/photos/c.jpg")),
        )

        val result = shares.filterForShareList("docs", ShareStatusFilter.DISABLED)

        assertEquals(listOf("disabled-doc"), result.map { it.id })
    }

    private fun share(
        id: String,
        enabled: Boolean = true,
        name: String? = id,
        paths: List<String> = listOf("/$id"),
        expiresAt: Long? = null,
    ) = Share(
        id = id,
        instanceId = "instance",
        paths = paths,
        name = name,
        shareUrl = null,
        password = null,
        enabled = enabled,
        expiresAt = expiresAt,
        accessed = 0,
        maxAccessed = 0,
        creator = null,
    )
}
