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

    /**
     * Paper (Oct 24): white, near-black text, light grey cards, black as the accent; the one light theme. Written out
     * whole: [derive] lightens a dark surface, which on white would leave white text on white. Text on white reads at
     * AA or better (dim #6B6B6B 5.3:1, muted #525252 7.8:1); white on the black accent 18:1.
     */
    val paper = ThemeTokens.moonlight.copy(
        id = "paper", name = "Paper", icon = "📄", light = true,
        bgBlack = Color(0xFFF0F0F0), bgTerminal = Color(0xFFFFFFFF), bgSurface = Color(0xFFFAFAFA), bgCard = Color(0xFFF4F4F4),
        bgCardElevated = Color(0xFFECECEC), bgCardHover = Color(0xFFE4E4E4), bgInput = Color(0xFFF7F7F7), bgSubtle = Color(0x0A000000),
        bgOverlay = Color(0xD9FFFFFF), bgModalOverlay = Color(0x80000000),
        borderSubtle = Color(0x14000000), borderDefault = Color(0xFFD6D6D6), borderElevated = Color(0xFFBDBDBD), borderFocus = Color(0xFF111111),
        borderEmerald = Color(0x59111111), borderCyan = Color(0x59404040), borderAmber = Color(0x669A6B00), borderDanger = Color(0x66B8321F),
        borderFrontier = Color(0x73404040),
        textPrimary = Color(0xFF111111), textHeading = Color(0xFF000000), textSecondary = Color(0xFF3B3B3B), textMuted = Color(0xFF525252),
        textDim = Color(0xFF6B6B6B), textInverse = Color(0xFFFFFFFF), textAccentEmerald = Color(0xFF000000), textAccentCyan = Color(0xFF333333),
        textAccentAmber = Color(0xFF8A6200), textAccentViolet = Color(0xFF333333),
        emerald50 = Color(0xFFF5F5F5), emerald400 = Color(0xFF333333), emerald500 = Color(0xFF111111), emerald600 = Color(0xFF000000),
        emerald900 = Color(0xFFE8E8E8), emeraldBgSubtle = Color(0x14111111), emeraldBorder = Color(0x47111111),
        cyan400 = Color(0xFF525252), cyan500 = Color(0xFF404040), cyan600 = Color(0xFF262626), cyanBgSubtle = Color(0x12404040), cyanBorder = Color(0x4D404040),
        frontierGlow = Color(0xFF111111), frontierGlowCyan = Color(0xFF404040), frontierBadgeBg = Color(0x12111111), frontierBadgeBorder = Color(0x47111111),
        frontierText = Color(0xFF262626), frontierGradientStart = Color(0xFFF4F4F4), frontierGradientEnd = Color(0xFFFFFFFF),
        amber400 = Color(0xFF8A6200), amber500 = Color(0xFF9A6B00), amber600 = Color(0xFF7A5300), amberBgSubtle = Color(0x1A9A6B00), amberBorder = Color(0x599A6B00),
        crimson400 = Color(0xFFB8321F), crimson500 = Color(0xFFB91C1C), crimson600 = Color(0xFF991B1B), crimson900 = Color(0xFFFDECEA),
        crimsonBgSubtle = Color(0x14B8321F), crimsonBorder = Color(0x59B8321F),
    )

    /** Tactical Red comes with Boar Avatars (experimental); the rest are granted by plugins (TruffleVault.grantTheme). */
    val tacticalRed = derive("tactical_red", "Tactical Red", "🔴", Color(0xFFFF3B30), Color(0xFF000000))
    val extras: List<BoarColors> = listOf(
        tacticalRed,
        derive("hacker_green", "Hacker Green", "🟢", Color(0xFF00FF66), Color(0xFF0D1117)),
        derive("stealth_mono", "Stealth Mono", "🥷", Color(0xFFE5E5EA), Color(0xFF000000)),
        derive("nomad_moss", "Nomad Moss", "🏕️", Color(0xFFD4A373), Color(0xFF1C2826)),
        derive("quantum_cyan", "Quantum Cyan", "⚛️", Color(0xFF00E5FF), Color(0xFF0B0E14)),
        derive("solarpunk_gold", "Solarpunk Gold", "☀️", Color(0xFFFFB800), Color(0xFF121212)),
        paper,
    )

    /** Themes open to everyone. */
    val open: Set<String> = ThemeTokens.all.map { it.id }.toSet()

    /** Themes an avatar brings: open once that avatar is unlocked (theme id to avatar id). */
    val byAvatar: Map<String, String> = mapOf(paper.id to "snow")

    /** Open without a grant while Boar Avatars is on. */
    val starter: Set<String> = setOf(tacticalRed.id)

    val all: List<BoarColors> = ThemeTokens.all + extras

    fun byId(id: String?): BoarColors = extras.firstOrNull { it.id == id } ?: ThemeTokens.byId(id)
}
