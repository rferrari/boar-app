package team.sopa.boar.labs.lunar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.sopa.boar.labs.LabPlugin
import team.sopa.boar.labs.LabStatus
import team.sopa.boar.ui.t
import team.sopa.boar.ui.theme.Boar
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Boar Lunar: the moon from the date alone, offline and without location (MoonPhase, Meeus ch. 49): today's phase and
 * lit share, the next full and new moon, and a month of phases. The hemisphere only mirrors the drawing; a waxing moon is
 * lit on the right in the north and on the left in the south. Moonrise and moonset would need the user's location, so
 * they're left out.
 */
object LunarLab : LabPlugin {
    override val id = "lunar"
    override val emoji = "🌕"
    override val art = team.sopa.boar.R.drawable.truffle_lunar
    override val status = LabStatus.BETA

    /**
     * Time zones south of the equator: the default hemisphere until the user picks one. The time zone, not the language:
     * a phone in Brazil set to English (US) said "north" (X6, Oct 16).
     */
    private val SOUTH_ZONES = listOf("America/Sao_Paulo", "America/Argentina", "America/Buenos_Aires", "America/Santiago", "America/Montevideo",
        "America/Asuncion", "America/La_Paz", "America/Lima", "America/Bahia", "America/Recife", "America/Fortaleza", "America/Belem",
        "America/Maceio", "America/Cuiaba", "America/Campo_Grande", "America/Porto_Velho", "America/Rio_Branco", "America/Noronha", "America/Araguaina",
        "Australia/", "Pacific/Auckland", "Pacific/Fiji", "Africa/Johannesburg", "Africa/Maputo", "Africa/Harare", "Africa/Lusaka",
        "Africa/Windhoek", "Africa/Gaborone", "Indian/Antananarivo", "Indian/Mauritius", "Antarctica/")

    private fun south(): Boolean = team.sopa.boar.labs.LabPrefs.string("lunar.south")?.let { it == "on" }
        ?: java.util.TimeZone.getDefault().id.let { id -> SOUTH_ZONES.any { id.startsWith(it) } }

    @Composable
    override fun Content() {
        val c = Boar.colors
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
        var isSouth by remember { mutableStateOf(south()) }
        var monthOffset by remember { mutableStateOf(0) }
        val m = MoonPhase.at(now)
        val dateTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(MoonPhase.emoji(m, isSouth), fontSize = 96.sp)
                Text(t("native.labs.lunar.phase.${MoonPhase.nameKey(m)}"), style = Boar.type.title, color = c.textHeading)
                Text(t("native.labs.lunar.lit", "pct" to Math.round(m.illumination * 100), "age" to String.format(Locale.US, "%.1f", m.ageDays)),
                    style = Boar.type.caption, color = c.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact(t("native.labs.lunar.nextFull"), "🌕 " + dateTime.format(Date(m.nextFullMs)), Modifier.weight(1f))
                Fact(t("native.labs.lunar.nextNew"), "🌑 " + dateTime.format(Date(m.nextNewMs)), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((south, key) in listOf(false to "north", true to "south")) {
                    Text(t("native.labs.lunar.$key"), style = Boar.type.caption, color = if (isSouth == south) c.textAccentEmerald else c.textMuted,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (isSouth == south) c.emeraldBorder else c.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable { isSouth = south; team.sopa.boar.labs.LabPrefs.putString("lunar.south", if (south) "on" else "off") }
                            .padding(vertical = 10.dp))
                }
            }
            Month(now, monthOffset, isSouth, onMove = { monthOffset += it })
            Text(t("native.labs.lunar.note"), style = Boar.type["mono.xs"], color = c.textDim)
        }
    }

    @Composable
    private fun Fact(label: String, value: String, modifier: Modifier) {
        val c = Boar.colors
        Column(modifier.background(c.bgCard, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Boar.type["mono.xs"], color = c.textDim, fontWeight = FontWeight.Bold)
            Text(value, style = Boar.type.caption, color = c.textPrimary)
        }
    }

    /** A month as a calendar: each day's phase at local noon, today outlined; ‹ › move by a month. */
    @Composable
    private fun Month(now: Long, offset: Int, south: Boolean, onMove: (Int) -> Unit) {
        val c = Boar.colors
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, offset); set(Calendar.DAY_OF_MONTH, 1) }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val title = String.format(Locale.getDefault(), "%1\$tB %1\$tY", cal)
        val firstDow = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        Column(Modifier.fillMaxWidth().background(c.bgCard, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = Boar.type.title, color = c.textAccentEmerald, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onMove(-1) }.padding(horizontal = 12.dp))
                Text(title.replaceFirstChar { it.uppercase() }, style = Boar.type.titleSm, color = c.textHeading, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("›", style = Boar.type.title, color = c.textAccentEmerald, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onMove(1) }.padding(horizontal = 12.dp))
            }
            val cells = List(firstDow) { 0 } + (1..days).toList()
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d ->
                        if (d == 0) Box(Modifier.weight(1f)) else {
                            val noon = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }
                            val isToday = noon.get(Calendar.YEAR) == today.get(Calendar.YEAR) && noon.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                            Column(Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(6.dp))
                                .then(if (isToday) Modifier.border(1.dp, c.emerald400, RoundedCornerShape(6.dp)) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(MoonPhase.emoji(MoonPhase.at(noon.timeInMillis), south), fontSize = 16.sp)
                                Text("$d", style = Boar.type["mono.xs"], color = if (isToday) c.textAccentEmerald else c.textMuted)
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
