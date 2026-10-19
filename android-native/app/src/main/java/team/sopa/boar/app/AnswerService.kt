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
        val n = notification(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val ID = 4042
        private const val CHANNEL = "answering"

        /** With each answer. A start Android refuses (BOAR already in the background) is skipped, never thrown. */
        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, AnswerService::class.java)) }
        }

        fun stop(ctx: Context) {
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
