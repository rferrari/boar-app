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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val ctx = LocalContext.current
    // The notifications page's answer, here so the footer turns to Next once they're allowed.
    var notifyGranted by remember { mutableStateOf(team.sopa.boar.app.NotificationAsk.granted(ctx)) }
    val skippable = (page == Page.FILES && state.files.isEmpty()) || (page == Page.PIN && !services.pinLock.isSet) ||
        (page == Page.NOTIFY && !notifyGranted)
    // A page of its own: Next opens it at the top, not where the last one was scrolled to.
    androidx.compose.runtime.key(page) { StepScreen(footer = {
        if (page == Page.READY) {
            // The page's subtitle already says when it opens: no note here, so large text keeps room for the page.
            PrimaryButton(t("flows.onboarding.open"), enabled = state.finished, onClick = onDone)
        // A page with its own action (Add files, Set a PIN, Allow notifications) keeps that one as the strong button;
        // skipping is quieter.
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
            Page.ANSWERS -> AnswersPage(services, state) { vm.guideTo(Page.FILES) }
            Page.FILES -> FilesPage(state, vm)
            Page.ASK -> AskPage(state)
            Page.FIRST -> FirstQuestionPage(services)
            Page.PIN -> PinPage(services) { pinEntry = true }
            Page.NOTIFY -> NotifyPage(notifyGranted) { notifyGranted = it }
            Page.PRIVACY -> PrivacyPage(services)
            Page.LABS -> {
                team.sopa.boar.labs.LabHooks.SetupList()
                Meta(t("native.setupGuide.labs.note"), Boar.colors.textSecondary)
            }
            Page.READY -> ReadyPage(services, state) { vm.showDetails(true) }
        }
    } }
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
    // Text size above the example: with large text the example fills the screen, and the size chips must stay in reach.
    var scale by remember { mutableStateOf(services.settings.fontScale) }
    Choices(t("native.settings.textSize"), listOf("compact", "standard", "large").map { it to t("native.settings.$it") }, scale) { scale = it; onFontScale(it) }
    // Below the chips, so a tap repaints the example right under the finger (as the tour's theme card).
    team.sopa.boar.ui.tour.TourPreview("theme")
}

/**
 * One way BOAR answers, its icon big and first (the same as on the chat's style button), so a quick reader matches the
 * button to the mode without reading the text. [onPick]: null for a mode explained but not picked here.
 */
@Composable
private fun ModeCard(icon: String, title: String, body: String, selected: Boolean, styleId: String? = null, onPick: (() -> Unit)?) {
    val c = Boar.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) c.emeraldBgSubtle else c.bgCard)
        .border(if (selected) 2.dp else 1.dp, if (selected) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(16.dp))
        .then(if (onPick != null) Modifier.clickable(onClick = onPick) else Modifier).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).background(if (selected) c.emerald500.copy(alpha = 0.25f) else c.bgSubtle, CircleShape), contentAlignment = Alignment.Center) {
            // An answer style, and Deep Research, show the icon pack's drawing (StyleIcon).
            if (styleId != null) team.sopa.boar.ui.StyleIcon(styleId, icon, 26.dp)
            else Text(icon, fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Boar.type.title, color = c.textHeading)
            Text(body, style = Boar.type.subtext, color = c.textSecondary)
        }
        if (selected) Text("✓", style = Boar.type.title, color = c.textAccentEmerald)
    }
}

@Composable
private fun AnswersPage(services: BoarServices, state: SetupState, onAddFiles: () -> Unit) {
    val c = Boar.colors
    val s = services.settings
    var style by remember { mutableStateOf(s.personalityId ?: services.personalities.defaultId) }
    // The styles BOAR can start with; Personal Files only once there are files to answer from.
    val ids = listOfNotNull("succinct", "detailed", if (state.files.isNotEmpty()) "personal" else null)
    for (id in ids) ModeCard(services.personalities.get(id).icon, t("personalities.$id.label"), t("personalities.$id.description"), style == id, id) { style = id; s.personalityId = id }
    // Without files, Personal Files is explained, not picked: there would be nothing to answer from. A tap offers the
    // files page, one page back, to add some.
    var askFiles by rememberSaveable { mutableStateOf(false) }
    if (state.files.isEmpty()) ModeCard(services.personalities.get("personal").icon, t("personalities.personal.label"), t("native.setupGuide.answers.personalLater"), false, "personal") { askFiles = true }
    if (askFiles) AlertDialog({ askFiles = false },
        confirmButton = { TextButton({ askFiles = false; onAddFiles() }) { Text(t("native.setupGuide.files.add")) } },
        dismissButton = { TextButton({ askFiles = false }) { Text(t("native.setupGuide.skip")) } },
        title = { Text(t("native.setupGuide.answers.addFilesTitle")) },
        text = { Text(t("native.setupGuide.answers.addFilesBody") + "\n\n" + t("native.setupGuide.files.types")) },
        containerColor = Boar.colors.bgCard, titleContentColor = Boar.colors.textHeading, textContentColor = Boar.colors.textSecondary)
    var deep by remember { mutableStateOf(s.deepResearchMode) }
    ModeCard("🔬", t("personalitySettings.deepResearchTitle"), t("native.setupGuide.answers.deepNote"), deep, "deep") { deep = !deep; s.deepResearchMode = deep }
    // Deep Research writes longer answers in several steps: on the light model that's minutes per answer.
    val light = state.models.firstOrNull { it.id == state.chosenId }?.answerTier == AnswerTier.COMPACT
    if (deep && light) Meta(t("native.setupGuide.answers.deepSlow"), c.textAccentAmber)
    Meta(t("native.setupGuide.answers.switch"), c.textAccentCyan)
}

@Composable
private fun FilesPage(state: SetupState, vm: SetupViewModel) {
    val c = Boar.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addFiles(uris) }
    // The same types as Knowledge → Personal Files.
    val pick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/csv", "text/comma-separated-values", "application/json", "application/pdf", "text/*")) }
    if (state.files.isEmpty()) PrimaryButton(t("native.setupGuide.files.add"), onClick = pick)
    else SecondaryButton(t("native.setupGuide.files.addMore"), pick)
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
            // A wrong pick goes again; an imported one comes out of Personal Files too.
            val removeLabel = t("native.setupGuide.files.remove", "name" to name)
            Text("✕", style = Boar.type.title, color = c.textMuted, modifier = Modifier.clip(CircleShape)
                .clickable(enabled = !state.importing) { vm.removeFile(name) }
                .semantics { contentDescription = removeLabel }.padding(8.dp))
        }
    }
    state.importNotes.forEach { Meta(it, c.textSecondary) }
    state.importErrors.forEach { Meta(it, c.crimson400) }
    // A question about these files needs the Personal Files style: the default one searches the library first.
    if (state.files.isNotEmpty()) Card(background = c.emeraldBgSubtle, border = c.emerald500) {
        Text(t("native.setupGuide.files.howToAsk"), style = Boar.type.body, color = c.textPrimary)
    }
    Meta(t("native.setupGuide.files.where"), c.textAccentCyan)
}

@Composable
private fun AskPage(state: SetupState) {
    val c = Boar.colors
    Card {
        // With files added, how to ask about them comes first.
        listOfNotNull(if (state.files.isNotEmpty()) "tipFiles" else null, "tip1", "tip2", "tip3").forEach { k ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = Boar.type.body, color = c.textAccentEmerald)
                Text(t("native.setupGuide.ask.$k"), style = Boar.type.body, color = c.textPrimary)
            }
        }
    }
    team.sopa.boar.ui.tour.TourPreview("sources")
}

/** The question waiting in the chat when BOAR opens: an example, or the user's own. */
@Composable
private fun FirstQuestionPage(services: BoarServices) {
    val c = Boar.colors
    val examples = listOf(t("chat.suggestions.q2"), t("chat.suggestions.q4"), t("chat.suggestions.q8"))
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
}

@Composable
private fun PinPage(services: BoarServices, onSet: () -> Unit) {
    val c = Boar.colors
    if (services.pinLock.isSet) {
        Card(background = c.emeraldBgSubtle, border = c.emerald500) { Text("✓  " + t("native.setupGuide.pin.on"), style = Boar.type.title, color = c.textHeading) }
        return
    }
    Text(t("native.pin.offerBody"), style = Boar.type.body, color = c.textPrimary)
    PrimaryButton(t("native.pin.offerSet"), onClick = onSet)
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

/**
 * Notifications, a page of their own (Oct 24; Android 13 and newer, SetupGuide.pages): what "BOAR is answering…" is,
 * how it works and why Android asks, with the permission asked here. Once this page has shown, the chat never asks at
 * the first question (NotificationAsk), whether the user allowed it or went on.
 */
@Composable
private fun NotifyPage(granted: Boolean, onGranted: (Boolean) -> Unit) {
    val c = Boar.colors
    val ctx = LocalContext.current
    // Refused before (Android won't show its dialog again): say where to turn it on. Read before the page marks it.
    var refused by remember { mutableStateOf(team.sopa.boar.app.NotificationAsk.asked(ctx) && !granted) }
    // Only Android's dialog counts as asked; the page showing only stops the chat from asking (CodeRabbit: a rotation or
    // Back hid the Allow button though nothing had been asked).
    androidx.compose.runtime.LaunchedEffect(Unit) { team.sopa.boar.app.NotificationAsk.markPageSeen(ctx) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onGranted(it); refused = !it }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(c.bgSubtle, CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(team.sopa.boar.R.drawable.ic_notifications), null, Modifier.size(22.dp), tint = c.textHeading)
            }
            Text(t("native.setupGuide.privacy.notifyTitle"), style = Boar.type.title, color = c.textHeading, modifier = Modifier.weight(1f))
        }
        Text(t("native.setupGuide.privacy.notifyBody"), style = Boar.type.body, color = c.textSecondary)
    }
    Text(t("native.setupGuide.notify.why"), style = Boar.type.body, color = c.textPrimary)
    when {
        granted -> Meta("✓  " + t("native.setupGuide.privacy.notifyOn"), c.textAccentEmerald)
        refused -> Meta(t("native.setupGuide.privacy.notifyOff"))
        else -> PrimaryButton(t("native.setupGuide.privacy.notifyAllow")) {
            team.sopa.boar.app.NotificationAsk.markAsked(ctx)
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Meta(t("native.setupGuide.notify.without"), c.textAccentCyan)
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
