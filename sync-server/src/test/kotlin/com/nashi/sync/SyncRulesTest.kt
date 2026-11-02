package com.nashi.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    private fun item(id: String, at: Long, name: String = id, deleted: Boolean = false) =
        ItemDto(id = id, name = name, categoryId = "c1", quantity = 1.0, updatedAt = at, isDeleted = deleted)

    private fun tx(id: String, at: Long) =
        TxDto(id = id, itemId = "i1", delta = 1.0, kind = "in", createdAt = at)

    private fun field(id: String, label: String) =
        FieldDefDto(id = id, categoryId = "c1", key = id, label = label, type = "text")

    @Test
    fun pushOldIsRejectedWithConflict() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(items = listOf(item("i1", 100, "新"))))

        val conflicts = applyPush(store, ChangeSet(items = listOf(item("i1", 50, "旧"))))

        assertEquals("新", store.items.getValue("i1").name)
        assertEquals(1, conflicts.size)
        assertEquals("item", conflicts[0].kind)
        assertEquals(100L, conflicts[0].serverUpdatedAt)
    }

    @Test
    fun pushWithSameTimestampWins() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(items = listOf(item("i1", 100, "甲"))))
        val conflicts = applyPush(store, ChangeSet(items = listOf(item("i1", 100, "乙"))))

        assertEquals("乙", store.items.getValue("i1").name)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun transactionsAreDedupedById() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(transactions = listOf(tx("t1", 100))))
        applyPush(store, ChangeSet(transactions = listOf(tx("t1", 100), tx("t2", 200))))

        assertEquals(2, store.txs.size)
        assertEquals(100L, store.txs.getValue("t1").createdAt)
    }

    @Test
    fun fieldDefsAreReplacedWholesale() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(categoryFields = listOf(field("f1", "容量"), field("f2", "颜色"))))
        applyPush(store, ChangeSet(categoryFields = listOf(field("f2", "颜色2"))))

        assertEquals(setOf("f2"), store.fieldDefs.keys)
        assertEquals("颜色2", store.fieldDefs.getValue("f2").label)
    }

    @Test
    fun nextSinceOnlyFollowsPageRows() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(items = listOf(item("i1", 1000))))

        val page = buildPullPage(store, since = 0, limit = 500)

        assertEquals(1000L, page.nextSince)
        assertEquals(1, page.changes.items.size)
    }

    @Test
    fun emptyPageKeepsSince() {
        val page = buildPullPage(FamilyStore(), since = 777, limit = 500)
        assertEquals(777L, page.nextSince)
        assertTrue(page.changes.items.isEmpty())
    }

    @Test
    fun pullIsPagedByLimitAndResumable() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(items = listOf(item("i1", 100), item("i2", 200), item("i3", 300))))

        val first = buildPullPage(store, since = 0, limit = 2)
        assertEquals(listOf("i1", "i2"), first.changes.items.map { it.id })
        assertEquals(200L, first.nextSince)

        val second = buildPullPage(store, since = first.nextSince, limit = 2)
        assertEquals(listOf("i3"), second.changes.items.map { it.id })
        assertEquals(300L, second.nextSince)

        val third = buildPullPage(store, since = second.nextSince, limit = 2)
        assertTrue(third.changes.items.isEmpty())
        assertEquals(300L, third.nextSince)
    }

    @Test
    fun tombstonesAreStillPulled() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(items = listOf(item("i1", 500, "已删", deleted = true))))

        val page = buildPullPage(store, since = 0, limit = 500)

        assertEquals(1, page.changes.items.size)
        assertTrue(page.changes.items[0].isDeleted)
    }

    @Test
    fun fieldDefsAreAlwaysIncluded() {
        val store = FamilyStore()
        applyPush(store, ChangeSet(categoryFields = listOf(field("f1", "容量"))))
        store.items["i1"] = item("i1", 100)

        val page = buildPullPage(store, since = 100, limit = 1)

        assertEquals(1, page.changes.categoryFields.size)
    }

    @Test
    fun inviteIsUsableOnlyWithinTtl() {
        val invite = Invite("INV-A", "f1", expiresAt = 1_000L)

        assertTrue(inviteUsable(invite, 999L))
        assertTrue("到期当刻即失效", !inviteUsable(invite, 1_000L))
    }

    @Test
    fun usedOrRevokedInviteIsRejected() {
        val base = Invite("INV-A", "f1", expiresAt = 10_000L)

        assertTrue("用过的码不能换第二个 token", !inviteUsable(base.copy(used = true), 1L))
        assertTrue("撤销后的码立即失效", !inviteUsable(base.copy(revoked = true), 1L))
        assertTrue(!inviteUsable(null, 1L))
    }

    @Test
    fun hashTokenIsStableAndNotTheToken() {
        val hash = hashToken("dev_abc")

        assertEquals(hash, hashToken("dev_abc"))
        assertEquals(64, hash.length)
        assertTrue(hash != "dev_abc")
        assertTrue(hashToken("dev_abd") != hash)
    }

    @Test
    fun legacyPlaintextTokensAreUpgradedOnLoad() {
        val (admin, devices) = upgradeLegacyTokens(
            legacyAdminToken = "adm_secret",
            adminTokenHash = "",
            devices = mapOf("dev_secret" to "phone", hashToken("dev_old") to "tablet"),
        )

        assertEquals(hashToken("adm_secret"), admin)
        assertEquals("phone", devices[hashToken("dev_secret")])
        assertEquals("tablet", devices[hashToken("dev_old")])
        assertEquals(2, devices.size)
    }

    @Test
    fun alreadyHashedSnapshotIsNotHashedTwice() {
        val stored = hashToken("dev_secret")
        val (admin, devices) = upgradeLegacyTokens("", hashToken("adm_secret"), mapOf(stored to "phone"))

        assertEquals(hashToken("adm_secret"), admin)
        assertEquals(mapOf(stored to "phone"), devices)
    }

    @Test
    fun memberDevicesAreDedupedAndSorted() {
        val listed = memberDevices(
            mapOf(
                "hash1" to "tablet",
                "hash2" to "phone",
                "hash3" to "phone",
                "hash4" to "",
            )
        )

        assertEquals(listOf("phone", "tablet"), listed)
    }

    @Test
    fun visibleInvitesOnlyListsLiveCodesOfThisFamilyNewestFirst() {
        val now = 1_000L
        val invites = listOf(
            Invite("INV-OLD", "f1", expiresAt = 1_100L),
            Invite("INV-NEW", "f1", expiresAt = 9_000L),
            Invite("INV-EXPIRED", "f1", expiresAt = 999L),
            Invite("INV-OTHER", "f2", expiresAt = 9_000L),
            Invite("INV-REVOKED", "f1", expiresAt = 8_000L, revoked = true),
        )

        val listed = visibleInvites(invites, familyId = "f1", now = now)

        assertEquals(listOf("INV-NEW", "INV-REVOKED", "INV-OLD"), listed.map { it.code })
    }
}
