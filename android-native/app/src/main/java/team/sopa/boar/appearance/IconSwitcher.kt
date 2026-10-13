package team.sopa.boar.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the launcher icon by enabling one <activity-alias> and disabling the others (the manifest's .Launcher and one
 * .Launcher_<id> per truffle). The target is enabled first, so there's never a moment with no launcher entry. Some
 * launchers take a few seconds to show the change.
 */
object IconSwitcher {
    private const val DEFAULT = ".Launcher"
    /**
     * The aliases' class names are relative to the code's namespace (team.sopa.boar), not the package name: the
     * release package is team.sopa.aoair, so packageName + alias names a class that doesn't exist.
     */
    private val NAMESPACE = team.sopa.boar.MainActivity::class.java.`package`!!.name

    private fun aliasOf(id: String?) = id?.let { TruffleArt.byId[it]?.alias } ?: DEFAULT

    fun apply(context: Context, id: String?) {
        val pm = context.packageManager
        val target = aliasOf(id)
        fun component(alias: String) = ComponentName(context.packageName, NAMESPACE + alias)
        pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (alias in listOf(DEFAULT) + TruffleArt.byId.values.map { it.alias }) {
            if (alias == target) continue
            val c = component(alias)
            if (pm.getComponentEnabledSetting(c) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                pm.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** True when the enabled launcher entry isn't the one [id] wants (a backup restore, a reinstall). */
    fun drifted(context: Context, id: String?): Boolean {
        val pm = context.packageManager
        val c = ComponentName(context.packageName, NAMESPACE + aliasOf(id))
        val s = pm.getComponentEnabledSetting(c)
        // The default alias is enabled in the manifest; the truffle aliases aren't.
        return if (id == null) s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED else s != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}
