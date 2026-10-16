package team.sopa.boar.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Settings → Haptic feedback: a short tick when a question is sent, a firmer one when its answer finishes. The switch
 * was saved but nothing read it, so nothing ever vibrated (X6, Oct 16). Uses the vibrator directly, so it follows the
 * app's switch rather than the phone's touch-feedback setting.
 */
object Haptics {
    fun sent(ctx: Context) = play(ctx, VibrationEffect.EFFECT_TICK, 15)

    fun finished(ctx: Context) = play(ctx, VibrationEffect.EFFECT_CLICK, 30)

    private fun play(ctx: Context, effect: Int, fallbackMs: Long) {
        if (!BoarServices.get(ctx).settings.hapticsEnabled) return
        val v = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        if (v == null || !v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(effect))
            else v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
