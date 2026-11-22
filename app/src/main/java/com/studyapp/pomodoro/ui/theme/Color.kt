package com.studyapp.pomodoro.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.studyapp.pomodoro.AppThemeMode

data class PomodoroPalette(
    val ivory: Color,
    val paper: Color,
    val parchment: Color,
    val forest: Color,
    val forestDeep: Color,
    val mint: Color,
    val mintLight: Color,
    val stone: Color,
    val stoneLight: Color,
    val ink: Color,
    val muted: Color,
    val mutedLight: Color,
    val border: Color,
    val shadowWarm: Color,
    val highlightBorder: Color,
)

/**
 * Default Sanctuary Mode: Warm Botanical Ivory & Forest Green.
 */
val SanctuaryPalette = PomodoroPalette(
    ivory = Color(0xFFFAF9F4),
    paper = Color(0xFFFDFBF6),
    parchment = Color(0xFFF2EEE4),
    forest = Color(0xFF0F5132),
    forestDeep = Color(0xFF0A3D26),
    mint = Color(0xFFB7D9C1),
    mintLight = Color(0xFFE4EFE6),
    stone = Color(0xFFD6D3C8),
    stoneLight = Color(0xFFE9E5DA),
    ink = Color(0xFF1F2B23),
    // WCAG AA (>= 4.6:1 on Ivory/Paper/Parchment). #7E8378 measured only 3.35-3.76:1.
    muted = Color(0xFF5F6459),
    mutedLight = Color(0xFFA4A79D),
    border = Color(0xFFEDE8DC),
    shadowWarm = Color(0x33706B5D),
    highlightBorder = Color(0xB8FFFFFF),
)

/**
 * Focus Mode: Stays within the crisp Green & White sanctuary aesthetic —
 * porcelain-alabaster white surfaces, soft dew-sage wells, and lush botanical green accents.
 */
val FocusModePalette = PomodoroPalette(
    ivory = Color(0xFFF5F9F5),
    paper = Color(0xFFFCFDFB),
    parchment = Color(0xFFEBF3EC),
    forest = Color(0xFF115E3A),
    forestDeep = Color(0xFF0B4429),
    mint = Color(0xFFA7D7B8),
    mintLight = Color(0xFFDDF0E3),
    stone = Color(0xFFCBD9CE),
    stoneLight = Color(0xFFE0ECE2),
    ink = Color(0xFF17281F),
    muted = Color(0xFF5E7266),
    mutedLight = Color(0xFF8CA094),
    border = Color(0xFFDCE9DF),
    shadowWarm = Color(0x2E355E47),
    highlightBorder = Color(0xCCE2F2E6),
)

/**
 * Advanced Focus Mode: Stays within the Green & White sanctuary aesthetic —
 * luminous white cards framed by crisp botanical sage borders and deep evergreen accents.
 */
val AdvancedFocusPalette = PomodoroPalette(
    ivory = Color(0xFFF1F7F3),
    paper = Color(0xFFFAFDFA),
    parchment = Color(0xFFE4F0E7),
    forest = Color(0xFF0A472B),
    forestDeep = Color(0xFF06301C),
    mint = Color(0xFF93CBA8),
    mintLight = Color(0xFFD3EBD9),
    stone = Color(0xFFC0D4C5),
    stoneLight = Color(0xFFD6E6DA),
    ink = Color(0xFF112319),
    muted = Color(0xFF4E6758),
    mutedLight = Color(0xFF7E9688),
    border = Color(0xFFCCE0D2),
    shadowWarm = Color(0x361F4D35),
    highlightBorder = Color(0xD9C5E3CE),
)

fun paletteForThemeMode(mode: AppThemeMode): PomodoroPalette = when (mode) {
    AppThemeMode.SANCTUARY -> SanctuaryPalette
    AppThemeMode.FOCUS -> FocusModePalette
    AppThemeMode.ADVANCED_FOCUS -> AdvancedFocusPalette
}

var Ivory by mutableStateOf(SanctuaryPalette.ivory)
    internal set
var Paper by mutableStateOf(SanctuaryPalette.paper)
    internal set
var Parchment by mutableStateOf(SanctuaryPalette.parchment)
    internal set
var Forest by mutableStateOf(SanctuaryPalette.forest)
    internal set
var ForestDeep by mutableStateOf(SanctuaryPalette.forestDeep)
    internal set
var Mint by mutableStateOf(SanctuaryPalette.mint)
    internal set
var MintLight by mutableStateOf(SanctuaryPalette.mintLight)
    internal set
var Stone by mutableStateOf(SanctuaryPalette.stone)
    internal set
var StoneLight by mutableStateOf(SanctuaryPalette.stoneLight)
    internal set
var Ink by mutableStateOf(SanctuaryPalette.ink)
    internal set
var Muted by mutableStateOf(SanctuaryPalette.muted)
    internal set
var MutedLight by mutableStateOf(SanctuaryPalette.mutedLight)
    internal set
var Border by mutableStateOf(SanctuaryPalette.border)
    internal set
val White = Color(0xFFFFFFFF)
val Error = Color(0xFFA63A3A)
var ShadowWarm by mutableStateOf(SanctuaryPalette.shadowWarm)
    internal set
var HighlightBorder by mutableStateOf(SanctuaryPalette.highlightBorder)
    internal set

@Composable
fun SyncPomodoroPalette(themeMode: AppThemeMode) {
    val target = paletteForThemeMode(themeMode)
    val spec = tween<Color>(durationMillis = 750, easing = FastOutSlowInEasing)

    val animIvory by animateColorAsState(target.ivory, spec, label = "themeIvory")
    val animPaper by animateColorAsState(target.paper, spec, label = "themePaper")
    val animParchment by animateColorAsState(target.parchment, spec, label = "themeParchment")
    val animForest by animateColorAsState(target.forest, spec, label = "themeForest")
    val animForestDeep by animateColorAsState(target.forestDeep, spec, label = "themeForestDeep")
    val animMint by animateColorAsState(target.mint, spec, label = "themeMint")
    val animMintLight by animateColorAsState(target.mintLight, spec, label = "themeMintLight")
    val animStone by animateColorAsState(target.stone, spec, label = "themeStone")
    val animStoneLight by animateColorAsState(target.stoneLight, spec, label = "themeStoneLight")
    val animInk by animateColorAsState(target.ink, spec, label = "themeInk")
    val animMuted by animateColorAsState(target.muted, spec, label = "themeMuted")
    val animMutedLight by animateColorAsState(target.mutedLight, spec, label = "themeMutedLight")
    val animBorder by animateColorAsState(target.border, spec, label = "themeBorder")
    val animShadow by animateColorAsState(target.shadowWarm, spec, label = "themeShadowWarm")
    val animHighlight by animateColorAsState(target.highlightBorder, spec, label = "themeHighlightBorder")

    Ivory = animIvory
    Paper = animPaper
    Parchment = animParchment
    Forest = animForest
    ForestDeep = animForestDeep
    Mint = animMint
    MintLight = animMintLight
    Stone = animStone
    StoneLight = animStoneLight
    Ink = animInk
    Muted = animMuted
    MutedLight = animMutedLight
    Border = animBorder
    ShadowWarm = animShadow
    HighlightBorder = animHighlight
}
