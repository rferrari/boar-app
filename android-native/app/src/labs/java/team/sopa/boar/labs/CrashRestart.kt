package team.sopa.boar.labs

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * The error handler's second layer (Labs only; the first, Safe, is in every build): when BOAR crashes in its own code,
 * it opens again by itself, through [RestartActivity] in a process of its own (the crashing one can't start anything
 * once it's gone). At most one restart a minute, so a crash on start can't loop. A crash inside native code (llama.cpp,
 * an engine) can't be caught: the next start says BOAR closed after an error instead.
 */
object CrashRestart {
    private const val PREFS = "crash"
    private const val WINDOW_MS = 60_000L
    @Volatile private var installed = false
    @Volatile private var noteChecked = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val restart = LabPrefs.labsOn && LabPrefs.autoRestart && now - p.getLong("restartAt", 0) > WINDOW_MS
            // The stack only: no question or answer text goes to the log.
            Log.e("BOAR", "crash on ${t.name}: ${e.javaClass.simpleName}${if (restart) ", restarting" else ""}", e)
            runCatching { p.edit().putLong("crashAt", now).apply { if (restart) putLong("restartAt", now).putBoolean("noteDue", true) }.commit() }
            val started = restart && runCatching {
                app.startActivity(Intent(app, RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RestartActivity.PID, Process.myPid()))
            }.isSuccess
            if (started) { Process.killProcess(Process.myPid()); exitProcess(10) }
            prior?.uncaughtException(t, e)
        }
    }

    /**
     * Once per start: the note about the last time BOAR closed, when Labs is on. After a restart: it restarted. After a
     * crash it couldn't catch (native code) or one with restarts off: it closed after an error (Android 11+ reports it).
     */
    fun noteKey(context: Context): String? {
        if (noteChecked) return null
        noteChecked = true
        if (!LabPrefs.labsOn) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean("noteDue", false)) {
            p.edit().putBoolean("noteDue", false).putLong("exitSeen", System.currentTimeMillis()).apply()
            return "native.labs.crash.restarted"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val last = runCatching {
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 1).firstOrNull()
        }.getOrNull() ?: return null
        val seen = p.getLong("exitSeen", 0)
        p.edit().putLong("exitSeen", maxOf(seen, last.timestamp)).apply()
        val crashed = last.reason == ApplicationExitInfo.REASON_CRASH || last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
        return if (crashed && last.timestamp > seen && seen > 0) "native.labs.crash.closed" else null
    }
}

/** Runs in its own process (":restart"): waits for the crashed one to be gone, opens BOAR again, then leaves. */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getIntExtra(PID, -1).takeIf { it > 0 }?.let { Process.killProcess(it) }
        startActivity(Intent(this, team.sopa.boar.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Process.killProcess(Process.myPid())
    }

    companion object { const val PID = "crashedPid" }
}
