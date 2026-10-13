package team.sopa.boar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Native-only palettes the Truffle Vault unlocks, next to Campfire and Moonlight (generated from the Expo app's
 * tokens). Each one is Moonlight with its own accent and surface: every other token (text, warning, danger) stays
 * Moonlight's, so all 59 tokens are always defined and readable.
 */
object Themes {
    private fun derive(id: String, name: String, icon: String, accent: Color, surface: Color, base: BoarColors = ThemeTokens.moonlight): BoarColors {
        val white = Color.White
        val black = Color.Black
        fun up(f: Float) = lerp(surface, white, f)
        val border = lerp(surface, accent, 0.22f)
        return base.copy(
            id = id, name = name, icon = icon,
            bgBlack = lerp(surface, black, 0.4f), bgTerminal = surface, bgSurface = up(0.03f), bgCard = up(0.06f),
            bgCardElevated = up(0.10f), bgCardHover = up(0.14f), bgInput = up(0.02f),
            bgOverlay = surface.copy(alpha = 0.78f), bgModalOverlay = lerp(surface, black, 0.4f).copy(alpha = 0.85f),
            borderDefault = border, borderElevated = lerp(border, white, 0.10f), borderFocus = accent,
            borderEmerald = accent.copy(alpha = 0.35f),
            textInverse = surface, textAccentEmerald = accent,
            emerald50 = lerp(accent, white, 0.9f), emerald400 = lerp(accent, white, 0.2f), emerald500 = accent,
            emerald600 = lerp(accent, black, 0.12f), emerald900 = lerp(accent, black, 0.7f),
            emeraldBgSubtle = accent.copy(alpha = 0.12f), emeraldBorder = accent.copy(alpha = 0.28f),
            frontierGlow = accent, frontierGradientStart = up(0.06f), frontierGradientEnd = surface,
        )
    }

    /** Tactical Red is open to everyone; the rest are granted by plugins (TruffleVault.grantTheme). */
    val tacticalRed = derive("tactical_red", "Tactical Red", "🔴", Color(0xFFFF3B30), Color(0xFF000000))
    val extras: List<BoarColors> = listOf(
        tacticalRed,
        derive("hacker_green", "Hacker Green", "🟢", Color(0xFF00FF66), Color(0xFF0D1117)),
        derive("stealth_mono", "Stealth Mono", "🥷", Color(0xFFE5E5EA), Color(0xFF000000)),
        derive("nomad_moss", "Nomad Moss", "🏕️", Color(0xFFD4A373), Color(0xFF1C2826)),
        derive("quantum_cyan", "Quantum Cyan", "⚛️", Color(0xFF00E5FF), Color(0xFF0B0E14)),
        derive("solarpunk_gold", "Solarpunk Gold", "☀️", Color(0xFFFFB800), Color(0xFF121212)),
    )

    /** Themes open without a grant. */
    val open: Set<String> = ThemeTokens.all.map { it.id }.toSet() + tacticalRed.id

    val all: List<BoarColors> = ThemeTokens.all + extras

    fun byId(id: String?): BoarColors = extras.firstOrNull { it.id == id } ?: ThemeTokens.byId(id)
}
