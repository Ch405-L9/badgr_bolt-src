package com.badgr.orbreader

import android.app.Application
import com.badgr.orbreader.billing.InAppPurchaseManager
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.integrity.IntegrityChecker
import com.badgr.orbreader.data.preferences.UserPreferencesRepository
import com.badgr.orbreader.data.local.BookDatabase
import com.badgr.orbreader.data.local.BookEntity
import com.badgr.orbreader.data.model.FileType
import com.badgr.orbreader.util.WordTokenizer
import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application subclass – declared in AndroidManifest.xml via android:name=".OrbReaderApp".
 *
 * Responsibilities:
 *  - Initialise InAppPurchaseManager singleton
 *  - Restore persisted Pro entitlement to ProGate before billing reconnects (2.3.3)
 *  - Collect purchaseManager.isPro and keep ProGate + DataStore in sync (2.3.1 / 2.3.3)
 */
class OrbReaderApp : Application() {

    lateinit var purchaseManager: InAppPurchaseManager
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        userPreferencesRepository = UserPreferencesRepository(this)

        purchaseManager = InAppPurchaseManager(
            context          = this,
            onPurchaseSuccess = {
                // Entitlement state driven by isPro StateFlow collector below
            },
            onPurchaseError  = { error ->
                android.util.Log.e("InAppPurchase", "Purchase error: $error")
            }
        )

        // 2.3.3: Restore persisted entitlement immediately so ProGate is correct
        // before the billing client finishes its async reconnection.
        applicationScope.launch(Dispatchers.IO) {
            val persisted = userPreferencesRepository.preferences.first().isPro
            ProGate.setProEntitlement(persisted)
        }

        purchaseManager.connect()

        // 2.3.1 + 2.3.3: Observe live entitlement — update ProGate and persist on every change.
        applicationScope.launch {
            purchaseManager.isPro.collect { isPro ->
                ProGate.setProEntitlement(isPro)
                applicationScope.launch(Dispatchers.IO) {
                    userPreferencesRepository.setIsPro(isPro)
                }
            }
        }

        // Play Integrity report-only probe (fail-open — never blocks the user).
        applicationScope.launch(Dispatchers.IO) {
            IntegrityChecker.runReportOnlyCheck(this@OrbReaderApp)
        }

        // Pre-populate library with manual if empty
        applicationScope.launch(Dispatchers.IO) {
            val db = BookDatabase.getInstance(this@OrbReaderApp)
            if (db.bookDao().bookCount() == 0) {
                try {
                    val manualId = "welcome_guide"
                    val manualTitle = "Welcome to BADGR Bolt"
                    val manualText = assets.open("manual_text.txt").bufferedReader().use { it.readText() }
                    val words = WordTokenizer.tokenize(manualText)
                    
                    val book = BookEntity(
                        id = manualId,
                        title = manualTitle,
                        fileType = FileType.PDF.name,
                        wordCount = words.size,
                        createdAt = System.currentTimeMillis(),
                        coverPath = null
                    )
                    
                    // Save words atomically — same tmp→rename pattern as BookRepository.saveBook()
                    val wordFile = File(filesDir, "words_$manualId.json")
                    val tmp = File(filesDir, "words_$manualId.json.tmp")
                    try {
                        tmp.writeText(Gson().toJson(words))
                        if (!tmp.renameTo(wordFile)) wordFile.writeText(Gson().toJson(words))
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                    
                    db.bookDao().insertBook(book)
                } catch (e: Exception) {
                    android.util.Log.e("OrbReaderApp", "Failed to pre-populate manual: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        purchaseManager.disconnect()
        applicationScope.cancel()
    }
}
