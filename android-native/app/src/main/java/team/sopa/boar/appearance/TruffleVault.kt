package team.sopa.boar.appearance

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import team.sopa.boar.engine.settings.AppSettings
import team.sopa.boar.engine.vault.Vault
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Truffle Vault on this phone: counts what the vault's rules need (engine/vault/Vault) as the app is used, stores it in
 * settings.json, and announces each truffle the moment it's unearthed. Everything is counted here and never leaves
 * the phone; nothing runs in the background, only when the app opens and when something happens in it.
 */
class TruffleVault(private val context: Context, private val settings: AppSettings) {
    private val _state = MutableStateFlow(Vault.fromJson(settings.truffleVault))
    val state: StateFlow<Vault.State> = _state

    private val _fresh = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Truffles unearthed since the app opened, for the "Truffle unearthed" message. */
    val fresh: SharedFlow<String> = _fresh

    private val _icon = MutableStateFlow(settings.iconId?.takeIf { it in TruffleArt.byId })
    /** The equipped boar, or null for BOAR's own. */
    val icon: StateFlow<String?> = _icon

    @Synchronized
    fun record(change: (Vault.Facts) -> Vault.Facts) {
        val before = _state.value
        val (after, fresh) = Vault.apply(before, change(before.facts), LocalDate.now(), System.currentTimeMillis())
        if (after == before) return
        _state.value = after
        settings.truffleVault = Vault.toJson(after)
        // The default truffle isn't news.
        fresh.filter { Vault.RULES[it] != Vault.Rule.ALWAYS }.forEach { _fresh.tryEmit(it) }
    }

    /** On app start: the day, airplane mode, and what can be read from the phone and the app's own data. */
    fun onOpen(pinOn: Boolean, packs: Int, hfModels: Int) {
        val today = LocalDate.now()
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val source = sourceBuild()
        val degoogled = degoogled()
        record { f ->
            f.copy(
                usedDays = Vault.Days.add(f.usedDays, today),
                airplaneDays = if (airplane) Vault.Days.add(f.airplaneDays, today) else f.airplaneDays,
                pinOn = f.pinOn || pinOn, packs = maxOf(f.packs, packs), hfModels = maxOf(f.hfModels, hfModels),
                sourceBuild = f.sourceBuild || source, degoogled = f.degoogled || degoogled,
            )
        }
    }

    /** After an answer that wrote something. */
    fun answered(sourced: Boolean, deep: Boolean, portuguese: Boolean, tokPerSec: Double?, newChat: Boolean) {
        val offline = (context.getSystemService(ConnectivityManager::class.java))?.activeNetwork == null
        val night = Vault.isNight(LocalTime.now().hour)
        record { f ->
            f.copy(
                answers = f.answers + 1,
                sourcedAnswers = f.sourcedAnswers + if (sourced) 1 else 0,
                offlineAnswers = f.offlineAnswers + if (offline) 1 else 0,
                nightAnswers = f.nightAnswers + if (night) 1 else 0,
                deepResearch = f.deepResearch + if (deep) 1 else 0,
                chats = f.chats + if (newChat) 1 else 0,
                portuguese = f.portuguese || portuguese,
                bestTokPerSec = maxOf(f.bestTokPerSec, tokPerSec ?: 0.0),
            )
        }
    }

    /** A plugin (a Lab) grants a theme. */
    fun grantTheme(id: String) {
        if (id in _state.value.themes) return
        synchronized(this) {
            val s = _state.value.copy(themes = _state.value.themes + (id to System.currentTimeMillis()))
            _state.value = s
            settings.truffleVault = Vault.toJson(s)
        }
    }

    fun unlocked(id: String) = id in _state.value.unlocked

    /**
     * Equips a boar (null = BOAR's own). The in-app avatar changes now; the launcher icon when the app leaves the screen
     * ([applyIcon] from MainActivity.onStop): switching off the launcher entry the app was opened from closes it.
     */
    fun equip(id: String?) {
        val target = id?.takeIf { it in TruffleArt.byId && unlocked(it) }
        settings.iconId = target
        _icon.value = target
    }

    /** Puts the equipped boar on the launcher if it isn't there yet. Called when the app is no longer visible. */
    fun applyIcon() {
        // A launcher that refuses the switch mustn't take the app down: the avatar has already changed.
        runCatching { if (IconSwitcher.drifted(context, _icon.value)) IconSwitcher.apply(context, _icon.value) }
            .onFailure { android.util.Log.w("TruffleVault", "icon switch failed", it) }
    }


    /** Signed with anything but BOAR's release key: someone built this APK themselves. Debug builds count. */
    private fun sourceBuild(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val certs = info.signingInfo?.apkContentsSigners.orEmpty()
        certs.none { sha256(it.toByteArray()) == RELEASE_CERT_SHA256 }
    }.getOrDefault(false)

    /** GrapheneOS, or a phone without Google Play services (both need a <queries> entry in the manifest). */
    private fun degoogled(): Boolean {
        fun has(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        return has("app.grapheneos.apps") || !has("com.google.android.gms")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        /** The equipped boar's in-app picture, or [original] (what that spot showed before the vault) for BOAR's own. */
        fun avatar(id: String?, original: Int): Int = id?.let { TruffleArt.byId[it]?.avatar } ?: original

        /** apksigner verify --print-certs on the v1.1.1 / native release: the public certificate, not the key. */
        const val RELEASE_CERT_SHA256 = "7cf514f9ab8253cbd78eb7d112d66fd05c2ad27e6fc987bf8f3276a7eedf1f01"
    }
}

/** The equipped boar for the composables under it (null = BOAR's own); MainActivity provides it. */
val LocalTruffle = androidx.compose.runtime.compositionLocalOf<String?> { null }
