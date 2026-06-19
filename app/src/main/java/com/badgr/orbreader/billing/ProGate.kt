package com.badgr.orbreader.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for feature entitlement.
 *
 * PRIVATE_ROLLOUT_ALL_OPEN = true   → all Pro features unlocked (testing / private rollout)
 * PRIVATE_ROLLOUT_ALL_OPEN = false  → entitlement enforced via [isPro]
 *
 * OrbReaderApp collects InAppPurchaseManager.isPro StateFlow and calls
 * setProEntitlement() to keep this gate in sync — closes TD-003.
 */
object ProGate {

    // ── Toggle this to false when billing goes live ───────────────────────────
    private const val PRIVATE_ROLLOUT_ALL_OPEN = false

    // ── Free tier limits ──────────────────────────────────────────────────────
    const val FREE_BOOK_LIMIT = 5

    // ── Reactive entitlement state ────────────────────────────────────────────
    private val _isPro = MutableStateFlow(PRIVATE_ROLLOUT_ALL_OPEN)

    /** Observable stream — collect in UI / ViewModel layers. */
    val isProFlow: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Synchronous accessor — use in non-Compose, non-suspend contexts. */
    val isPro: Boolean get() = _isPro.value

    // ── Feature gates ─────────────────────────────────────────────────────────
    val statsScreen:     Boolean get() = isPro
    val cloudSync:       Boolean get() = isPro
    val unlimitedLib:    Boolean get() = isPro
    val customThemes:    Boolean get() = isPro
    val tts:             Boolean get() = isPro
    val largeFileImport: Boolean get() = isPro  // 100 MB cap vs 20 MB free

    // ── Called by OrbReaderApp collector once billing is wired ────────────────
    fun setProEntitlement(unlocked: Boolean) {
        _isPro.value = PRIVATE_ROLLOUT_ALL_OPEN || unlocked
    }

    // ── Called on sign-out or purchase revocation ─────────────────────────────
    fun revokeEntitlement() {
        _isPro.value = PRIVATE_ROLLOUT_ALL_OPEN  // respects rollout flag
    }
}
