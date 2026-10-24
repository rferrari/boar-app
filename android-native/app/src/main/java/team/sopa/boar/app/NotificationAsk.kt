package team.sopa.boar.app

import android.content.Context

/**
 * "BOAR is answering…" (AnswerService) needs the notification permission on Android 13+ (issue #91). BOAR asks once:
 * on setup's Privacy page, where it says what the notification is for (Oct 24), or else with the first question.
 * Either way the other never asks again, and a refusal stands.
 */
object NotificationAsk {
    private const val PREFS = "permissions"
    private const val ASKED = "notificationsAsked"

    /** Android asks for it at all (13 and newer). */
    fun applies(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    fun granted(ctx: Context): Boolean = !applies() ||
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun asked(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)

    fun markAsked(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()

    /** Asks with [ask] when it applies, isn't granted and was never asked; records that it was. */
    fun once(ctx: Context, ask: () -> Unit) {
        if (!applies() || granted(ctx) || asked(ctx)) return
        markAsked(ctx)
        ask()
    }
}
