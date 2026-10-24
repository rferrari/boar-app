package team.sopa.boar.ui.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.models.AnswerTier
import team.sopa.boar.engine.models.AssetKind
import team.sopa.boar.engine.models.SetupGuide.Page
import team.sopa.boar.setup.IndexPhase
import team.sopa.boar.setup.SetupState
import team.sopa.boar.setup.SetupViewModel
import team.sopa.boar.ui.settings.Choices
import team.sopa.boar.ui.settings.SwitchRow
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar

/**
 * Setup's step 4 (Oct 23): while the files download and the index builds, one page at a time to make BOAR the user's
 * own and learn to use it (SetupGuide). The download stays live in a strip on top; a tap on it shows its details.
 * Every choice is saved as it's made, with the same writes as Settings; every page can be skipped.
 */
@Composable
internal fun SetupGuidePage(state: SetupState, vm: SetupViewModel, themeId: String?, onTheme: (String) -> Unit, onFontScale: (String) -> Unit, onDone: () -> Unit) {
    val services = BoarServices.get(LocalContext.current)
    val page = state.page
    // The PIN page's "Set a PIN": the app's own PIN screens, in place of the page; done goes on to the next one.
    var pinEntry by rememberSaveable { mutableStateOf(false) }
    if (pinEntry) {
        BackHandler { pinEntry = false }
        team.sopa.boar.ui.security.PinSetup(services.pinLock, team.sopa.boar.ui.security.PinFlow.NEW,
            onDone = { pinEntry = false; vm.guideNext() }, onCancel = { pinEntry = false })
        return
    }
    var bannerHidden by rememberSaveable { mutableStateOf(false) }
    val skippable = (page == Page.FILES && state.files.isEmpty()) || (page == Page.PIN && !services.pinLock.isSet)
    StepScreen(footer = {
        if (page == Page.READY) {
            PrimaryButton(t("flows.onboarding.open"), enabled = state.finished, onClick = onDone)
            if (!state.finished) Meta(t("flows.onboarding.openWhenReady"))
        // A page with its own action (Add files, Set a PIN) keeps that one as the strong button; skipping is quieter.
        } else if (skippable) SecondaryButton(t("native.setupGuide.skip") + "  →") { vm.guideNext() }
        else PrimaryButton(t("native.setupGuide.next") + "  →") { vm.guideNext() }
        if (state.guidePage > 0) BackLink(t("flows.onboarding.back")) { vm.guideBack() }
    }) {
        ProgressStrip(state) { vm.showDetails(true) }
        // The download finished before the pages: BOAR can open now, or the user finishes them first.
        if (state.finished && page != Page.READY && !bannerHidden) ReadyBanner(onOpen = onDone) { bannerHidden = true }
        PageHeader(state.guidePage, state.pages.size, page)
        when (page) {
            Page.LOOK -> LookPage(services, themeId, onTheme, onFontScale)
            Page.ANSWERS -> AnswersPage(services, state)
            Page.FILES -> FilesPage(state, vm)
            Page.ASK -> AskPage(services, state)
            Page.PIN -> PinPage(services) { pinEntry = true }
            Page.PRIVACY -> PrivacyPage(services)
            Page.LABS -> {
                team.sopa.boar.labs.LabHooks.SetupList()
                Meta(t("native.setupGuide.labs.note"), Boar.colors.textSecondary)
            }
            Page.READY -> ReadyPage(services, state) { vm.showDetails(true) }
        }
    }
}

// --- Frame ---------------------------------------------------------------------------------------------------------

/** The download under the pages: bar, line and time left; amber when it needs the user, and a tap shows the details. */
@Composable
private fun ProgressStrip(state: SetupState, onDetails: () -> Unit) {
    val c = Boar.colors
    val p = tourProgress(state)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (p.problem) c.amberBgSubtle else c.bgCard)
        .border(1.dp, if (p.problem) c.amberBorder else c.borderSubtle, RoundedCornerShape(12.dp)).clickable(onClick = onDetails).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = c.emerald500, trackColor = c.bgSubtle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.line, style = Boar.type.subtext, color = if (p.problem) c.textAccentAmber else c.textSecondary,
                fontWeight = if (p.problem) FontWeight.Bold else null, modifier = Modifier.weight(1f))
            Text(t("native.setupGuide.details") + " ›", style = Boar.type.caption, color = c.textAccentEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReadyBanner(onOpen: () -> Unit, onKeep: () -> Unit) {
    val c = Boar.colors
    Card(background = c.emeraldBgSubtle, border = c.emerald500) {
        Text(t("native.tour.ready"), style = Boar.type.title, color = c.textHeading)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton(t("native.tour.openBoar"), onClick = onOpen) }
            Box(Modifier.weight(1f)) { SecondaryButton(t("native.setupGuide.keepGoing"), onKeep) }
        }
    }
}

@Composable
private fun PageHeader(index: Int, total: Int, page: Page) {
    val c = Boar.colors
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { n -> Box(Modifier.size(6.dp).background(if (n <= index) c.emerald500 else c.bgSubtle, CircleShape)) }
        Text(t("native.setupGuide.step", "n" to index + 1, "total" to total).uppercase(), style = Boar.type.micro, color = c.textAccentEmerald,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
    val key = page.name.lowercase()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t("native.setupGuide.$key.title"), style = Boar.type.headline, color = c.textHeading)
        Text(t("native.setupGuide.$key.sub"), style = Boar.type.body, color = c.textSecondary)
    }
}

// --- Pages ---------------------------------------------------------------------------------------------------------

@Composable
private fun LookPage(services: BoarServices, themeId: String?, onTheme: (String) -> Unit, onFontScale: (String) -> Unit) {
    var picked by remember { mutableStateOf(themeId) }
    team.sopa.boar.ui.appearance.ThemePicker(services.vault, picked) { picked = it; onTheme(it) }
    // Below the chips, so a tap repaints the example right under the finger (as the tour's theme card).
    team.sopa.boar.ui.tour.TourPreview("theme")
    var scale by remember { mutableStateOf(services.settings.fontScale) }
    Choices(t("native.settings.textSize"), listOf("compact", "standard", "large").map { it to t("native.settings.$it") }, scale) { scale = it; onFontScale(it) }
}

@Composable
private fun AnswersPage(services: BoarServices, state: SetupState) {
    val c = Boar.colors
    val s = services.settings
    var style by remember { mutableStateOf(s.personalityId ?: services.personalities.defaultId) }
    // Personal Files is offered with the files (the How to ask page says how to switch to it).
    for (id in listOf("succinct", "detailed")) {
        OptionCard("${services.personalities.get(id).icon}  ${t("personalities.$id.label")}", style == id, null, null, { style = id; s.personalityId = id }) {
            Meta(t("personalities.$id.description"), c.textSecondary)
        }
    }
    var deep by remember { mutableStateOf(s.deepResearchMode) }
    SwitchRow(t("personalitySettings.deepResearchTitle"), t("native.setupGuide.answers.deepNote"), deep) { deep = it; s.deepResearchMode = it }
    // Deep Research writes longer answers in several steps: on the light model that's minutes per answer.
    val light = state.models.firstOrNull { it.id == state.chosenId }?.answerTier == AnswerTier.COMPACT
    if (deep && light) Meta(t("native.setupGuide.answers.deepSlow"), c.textAccentAmber)
}

@Composable
private fun FilesPage(state: SetupState, vm: SetupViewModel) {
    val c = Boar.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addFiles(uris) }
    // The same types as Knowledge → Personal Files.
    val pick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/csv", "text/comma-separated-values", "application/json", "application/pdf", "text/*")) }
    if (state.files.isEmpty()) PrimaryButton("＋  " + t("native.setupGuide.files.add"), onClick = pick)
    else SecondaryButton("＋  " + t("native.setupGuide.files.addMore"), pick)
    Meta(t("native.setupGuide.files.types"))
    if (state.files.isNotEmpty()) Card {
        val searchReady = state.downloads.any { it.asset.kind == AssetKind.EMBEDDING && it.done }
        val waiting = t(when {
            state.importing -> "native.setupGuide.files.adding"
            !searchReady -> "native.setupGuide.files.waitSearch"
            state.indexPhase == IndexPhase.BUILDING -> "native.setupGuide.files.waitIndex"
            else -> "native.setupGuide.files.queued"
        })
        for (name in state.files) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val done = name in state.filesImported && !state.importing
            Text(if (done) "✓" else "◷", style = Boar.type.title, color = if (done) c.textAccentEmerald else c.textSecondary)
            Text(name, style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 2)
            Text(if (done) t("native.setupGuide.files.added") else waiting, style = Boar.type.caption, color = if (done) c.textAccentEmerald else c.textMuted)
        }
    }
    state.importErrors.forEach { Meta(it, c.crimson400) }
    Meta(t("native.setupGuide.files.where"), c.textAccentCyan)
}

@Composable
private fun AskPage(services: BoarServices, state: SetupState) {
    val c = Boar.colors
    Card {
        listOfNotNull("tip1", "tip2", "tip3", if (state.files.isNotEmpty()) "tipFiles" else null).forEach { k ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = Boar.type.body, color = c.textAccentEmerald)
                Text(t("native.setupGuide.ask.$k"), style = Boar.type.body, color = c.textPrimary)
            }
        }
    }
    team.sopa.boar.ui.tour.TourPreview("sources")
    Text(t("native.setupGuide.ask.pick").uppercase(), style = Boar.type.micro, color = c.textSecondary, fontWeight = FontWeight.Bold)
    val examples = listOfNotNull(
        t("chat.suggestions.q2"), t("chat.suggestions.q4"), t("chat.suggestions.q8"),
        if (state.files.isNotEmpty()) t("native.setupGuide.ask.filesExample") else null,
    )
    var question by remember { mutableStateOf(services.settings.pendingFirstQuestion.orEmpty()) }
    val pick: (String) -> Unit = { q -> question = q; services.settings.pendingFirstQuestion = q }
    for (q in examples) {
        val on = q == question
        Text(q, style = Boar.type.body, color = if (on) c.textAccentEmerald else c.textPrimary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) c.emeraldBgSubtle else c.bgCard)
                .border(if (on) 2.dp else 1.dp, if (on) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(12.dp))
                .clickable { pick(if (on) "" else q) }.padding(12.dp))
    }
    Box(Modifier.fillMaxWidth().background(c.bgInput, RoundedCornerShape(12.dp)).border(1.dp, c.borderDefault, RoundedCornerShape(12.dp)).padding(12.dp)) {
        val own = question.takeIf { it !in examples }.orEmpty()
        if (own.isEmpty()) Text(t("native.setupGuide.ask.own"), style = Boar.type.body, color = c.textDim)
        BasicTextField(own, pick, textStyle = Boar.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.emerald500), modifier = Modifier.fillMaxWidth())
    }
    Meta(t("native.setupGuide.ask.pickNote"))
}

@Composable
private fun PinPage(services: BoarServices, onSet: () -> Unit) {
    val c = Boar.colors
    if (services.pinLock.isSet) {
        Card(background = c.emeraldBgSubtle, border = c.emerald500) { Text("✓  " + t("native.setupGuide.pin.on"), style = Boar.type.title, color = c.textHeading) }
        return
    }
    Text(t("native.pin.offerBody"), style = Boar.type.body, color = c.textPrimary)
    PrimaryButton("🔑  " + t("native.pin.offerSet"), onClick = onSet)
    Meta(t("native.setupGuide.pin.note"))
}

@Composable
private fun PrivacyPage(services: BoarServices) {
    val s = services.settings
    var free by remember { mutableStateOf(s.freeMemoryInBackground) }
    SwitchRow(t("native.settings.freeMemory"), t("native.settings.freeMemoryNote"), free) { free = it; s.freeMemoryInBackground = it }
    var confirm by remember { mutableStateOf(s.confirmChatDelete) }
    SwitchRow(t("native.settings.confirmChatDelete"), t("native.settings.confirmChatDeleteNote"), confirm) { confirm = it; s.confirmChatDelete = it }
    var titles by remember { mutableStateOf(s.autoGenerateTitles) }
    SwitchRow(t("memorySettings.autoTitlesLabel"), t("memorySettings.autoTitlesValue"), titles) { titles = it; s.autoGenerateTitles = it }
    var voice by remember { mutableStateOf(s.voiceInputEnabled) }
    SwitchRow(t("voiceSettings.enabledLabel"), t("voiceSettings.enabledValue"), voice) { voice = it; s.voiceInputEnabled = it }
    Meta(t("native.setupGuide.privacy.where"), Boar.colors.textAccentCyan)
}

/** The last page: what was chosen, then the summary when BOAR is ready, else where the download is. */
@Composable
private fun ReadyPage(services: BoarServices, state: SetupState, onDetails: () -> Unit) {
    val c = Boar.colors
    val s = services.settings
    val on = t("native.setupGuide.ready.on")
    val off = t("native.setupGuide.ready.off")
    val style = s.personalityId ?: services.personalities.defaultId
    val rows = listOf(
        "style" to t("personalities.$style.label"),
        "deep" to if (s.deepResearchMode) on else off,
        "files" to if (state.files.isEmpty()) t("native.setupGuide.ready.none") else "${state.files.size}",
        "question" to (s.pendingFirstQuestion ?: t("native.setupGuide.ready.none")),
        "pin" to if (services.pinLock.isSet) on else off,
    )
    Card {
        Text(t("native.setupGuide.ready.choices").uppercase(), style = Boar.type.micro, color = c.textSecondary, fontWeight = FontWeight.Bold)
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("native.setupGuide.ready.$k"), style = Boar.type.body, color = c.textSecondary, modifier = Modifier.weight(0.45f))
                Text(v, style = Boar.type.body, color = c.textPrimary, modifier = Modifier.weight(0.55f), maxLines = 3)
            }
        }
        Meta(t("native.setupGuide.ready.change"))
    }
    if (state.finished) DoneSummary(state)
    else SecondaryButton(t("native.setupGuide.ready.seeDownload"), onDetails)
}
