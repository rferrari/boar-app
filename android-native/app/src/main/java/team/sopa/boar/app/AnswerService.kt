package team.sopa.boar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import team.sopa.boar.MainActivity
import team.sopa.boar.R
import team.sopa.boar.ui.I18n

/**
 * Keeps BOAR alive while it writes an answer, with a quiet "BOAR is answering…" notification (Android's foreground
 * service). Xiaomi's memory manager force-stopped BOAR 10-35 s after it left the screen mid-answer (F3 and X6, Oct 19
 * chaos runs); a foreground service is the standard way to say "busy, keep me". It starts with each answer, while BOAR
 * is still on screen (Android doesn't let a background app start one), and stops when the answer ends.
 */
class AnswerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always foreground first: Android closes an app whose startForegroundService() isn't followed by startForeground().
        // An answer that ended in the meantime (a plugin's instant one) left a stop request; it's honored right after.
        runCatching {
            val n = notification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
        }
        synchronized(lock) {
            started = true
            if (stopPending) { stopPending = false; stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) { started = false; stopPending = false }
        super.onDestroy()
    }

    companion object {
        private const val ID = 4042
        private const val CHANNEL = "answering"

        /** With each answer. A start Android refuses (BOAR already in the background) is skipped, never thrown. */
        private val lock = Any()
        /** The service has called startForeground (stopping it is safe now). */
        private var started = false
        /** A stop that came before that; onStartCommand honors it. */
        private var stopPending = false
        /** A start was requested and not yet stopped. */
        private var requested = false

        fun start(ctx: Context) {
            synchronized(lock) { stopPending = false }
            runCatching { ctx.startForegroundService(Intent(ctx, AnswerService::class.java)); synchronized(lock) { requested = true } }
        }

        fun stop(ctx: Context) {
            synchronized(lock) {
                if (!requested) return
                requested = false
                // Not foreground yet: stopping now would make Android close BOAR. onStartCommand stops it instead.
                if (!started) { stopPending = true; return }
            }
            runCatching { ctx.stopService(Intent(ctx, AnswerService::class.java)) }
        }

        private fun notification(ctx: Context): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            // Low importance: no sound, no pop-up; it only sits in the shade while the answer is written.
            nm.createNotificationChannel(NotificationChannel(CHANNEL, I18n.t(ctx, "native.answering.channel"), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_answering)
                .setContentTitle(I18n.t(ctx, "native.answering.title"))
                .setContentText(I18n.t(ctx, "native.answering.text"))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }
}
