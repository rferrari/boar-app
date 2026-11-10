package team.sopa.boar.engine.models

/** When the chat's "couldn't load" note still applies (ChatViewModel.LoadNote). */
object LoadNotes {
    /**
     * A note about [noteModelId] is still about the user's pick ([pickedId], the Model Catalog's choice): picking another
     * model there closes it (adam, Nov 10: the banner stayed after loading a new model). No pick at all keeps it.
     */
    fun stillAbout(noteModelId: String, pickedId: String?): Boolean = pickedId == null || pickedId == noteModelId
}
