package team.sopa.boar.engine.answer

/**
 * Staged answers stopped being a plugin (Nov 12): the old plugin switch becomes the setting once. Off stays off; on or
 * never touched becomes on (the new default). [pluginSwitch]: the stored "enabled.staged", null when never set.
 */
object StagedMigration {
    fun settingFrom(pluginSwitch: Boolean?): Boolean? = if (pluginSwitch == false) false else null
}
