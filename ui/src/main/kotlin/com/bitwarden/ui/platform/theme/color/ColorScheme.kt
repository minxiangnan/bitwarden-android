package com.bitwarden.ui.platform.theme.color

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The default [BitwardenColorScheme] for dark mode.
 */
val darkBitwardenColorScheme: BitwardenColorScheme = BitwardenColorScheme(
    isDarkTheme = true,
    isDynamicTheme = false,
    text = BitwardenColorScheme.TextColors(
        primary = ZhColor.darkOnSurface,
        secondary = ZhColor.darkMuted,
        interaction = ZhColor.darkPrimary,
        reversed = ZhColor.darkBackground,
        codePink = PrimitiveColors.pink200,
        codeBlue = ZhColor.darkPrimary,
    ),
    background = BitwardenColorScheme.BackgroundColors(
        primary = ZhColor.darkBackground,
        secondary = ZhColor.darkSurface,
        tertiary = ZhColor.darkSurfaceHigh,
        alert = ZhColor.darkSurfaceHigh,
        scrim = ZhColor.darkBackground.copy(alpha = 0.55f),
        pressed = ZhColor.darkDivider,
    ),
    stroke = BitwardenColorScheme.StrokeColors(
        divider = ZhColor.darkDivider,
        border = ZhColor.darkPrimary,
        segmentedNav = ZhColor.darkDivider,
    ),
    icon = BitwardenColorScheme.IconColors(
        primary = ZhColor.darkMuted,
        secondary = ZhColor.darkPrimary,
        reversed = ZhColor.darkBackground,
        badgeBackground = PrimitiveColors.pink200,
        badgeForeground = PrimitiveColors.gray1100,
    ),
    filledButton = BitwardenColorScheme.FilledButtonColors(
        background = ZhColor.darkPrimary,
        backgroundDisabled = ZhColor.darkSurfaceHigh,
        backgroundReversed = ZhColor.darkSurface,
        foreground = ZhColor.darkBackground,
        foregroundDisabled = ZhColor.darkMuted,
        foregroundReversed = ZhColor.darkPrimary,
    ),
    outlineButton = BitwardenColorScheme.OutlineButtonColors(
        border = ZhColor.darkDivider,
        borderDisabled = ZhColor.darkDivider,
        borderReversed = ZhColor.darkSurface,
        foreground = ZhColor.darkPrimary,
        foregroundDisabled = ZhColor.darkMuted,
        foregroundReversed = ZhColor.darkOnSurface,
    ),
    toggleButton = BitwardenColorScheme.ToggleButtonColors(
        backgroundOn = ZhColor.darkPrimary,
        backgroundOff = ZhColor.darkDivider,
        switch = ZhColor.darkOnSurface,
    ),
    sliderButton = BitwardenColorScheme.SliderButtonColors(
        knobBackground = ZhColor.darkPrimary,
        knobLabel = ZhColor.darkBackground,
        filled = ZhColor.darkPrimary,
        unfilled = ZhColor.darkDivider,
    ),
    status = BitwardenColorScheme.StatusColors(
        strong = PrimitiveColors.green200,
        good = ZhColor.darkPrimary,
        weak1 = ZhColor.darkError,
        weak2 = PrimitiveColors.yellow200,
        error = ZhColor.darkError,
    ),
    statusBadge = BitwardenColorScheme.StatusBadgeColors(
        success = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.green800,
            background = PrimitiveColors.green950,
            text = PrimitiveColors.green150,
        ),
        error = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.red800,
            background = PrimitiveColors.red950,
            text = PrimitiveColors.red150,
        ),
        warning = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.orange800,
            background = PrimitiveColors.orange950,
            text = PrimitiveColors.orange200,
        ),
        primary = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.blue600,
            background = PrimitiveColors.blue700,
            text = PrimitiveColors.blue150,
        ),
    ),
    illustration = BitwardenColorScheme.IllustrationColors(
        stroke = PrimitiveColors.gray1100,
        fillPrimary = PrimitiveColors.blue200,
        fillSecondary = PrimitiveColors.blue300,
        fillTertiary = PrimitiveColors.blue100,
        fillAccent = PrimitiveColors.teal400,
        logo = PrimitiveColors.gray100,
    ),
)

/**
 * The default [BitwardenColorScheme] for light mode.
 */
val lightBitwardenColorScheme: BitwardenColorScheme = BitwardenColorScheme(
    isDarkTheme = false,
    isDynamicTheme = false,
    text = BitwardenColorScheme.TextColors(
        primary = ZhColor.onSurface,
        secondary = ZhColor.muted,
        interaction = ZhColor.primaryText,
        reversed = ZhColor.surface,
        codePink = PrimitiveColors.pink100,
        codeBlue = ZhColor.primaryText,
    ),
    background = BitwardenColorScheme.BackgroundColors(
        primary = ZhColor.background,
        secondary = ZhColor.surface,
        tertiary = ZhColor.track,
        alert = ZhColor.track,
        scrim = ZhColor.onSurface.copy(alpha = 0.4f),
        pressed = ZhColor.divider,
    ),
    stroke = BitwardenColorScheme.StrokeColors(
        divider = ZhColor.divider,
        border = ZhColor.outline,
        segmentedNav = ZhColor.track,
    ),
    icon = BitwardenColorScheme.IconColors(
        primary = ZhColor.muted,
        secondary = ZhColor.primaryText,
        reversed = ZhColor.surface,
        badgeBackground = PrimitiveColors.pink100,
        badgeForeground = PrimitiveColors.gray100,
    ),
    filledButton = BitwardenColorScheme.FilledButtonColors(
        background = ZhColor.primary,
        backgroundDisabled = ZhColor.divider,
        backgroundReversed = ZhColor.surface,
        foreground = ZhColor.onPrimary,
        foregroundDisabled = ZhColor.muted,
        foregroundReversed = ZhColor.primaryText,
    ),
    outlineButton = BitwardenColorScheme.OutlineButtonColors(
        border = ZhColor.divider,
        borderDisabled = ZhColor.divider,
        borderReversed = ZhColor.surface,
        foreground = ZhColor.primaryText,
        foregroundDisabled = ZhColor.muted,
        foregroundReversed = ZhColor.onSurface,
    ),
    toggleButton = BitwardenColorScheme.ToggleButtonColors(
        backgroundOn = ZhColor.primary,
        backgroundOff = ZhColor.divider,
        switch = ZhColor.surface,
    ),
    sliderButton = BitwardenColorScheme.SliderButtonColors(
        knobBackground = ZhColor.primary,
        knobLabel = ZhColor.onPrimary,
        filled = ZhColor.primary,
        unfilled = ZhColor.divider,
    ),
    status = BitwardenColorScheme.StatusColors(
        strong = PrimitiveColors.green300,
        good = ZhColor.primaryText,
        weak1 = ZhColor.error,
        weak2 = PrimitiveColors.yellow300,
        error = ZhColor.error,
    ),
    statusBadge = BitwardenColorScheme.StatusBadgeColors(
        success = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.green150,
            background = PrimitiveColors.green050,
            text = PrimitiveColors.green400,
        ),
        error = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.red150,
            background = PrimitiveColors.red050,
            text = PrimitiveColors.red400,
        ),
        warning = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.orange200,
            background = PrimitiveColors.orange050,
            text = PrimitiveColors.orange700,
        ),

        primary = BitwardenColorScheme.StatusBadgeVariantColors(
            border = PrimitiveColors.blue150,
            background = PrimitiveColors.blue050,
            text = PrimitiveColors.blue600,
        ),
    ),
    illustration = BitwardenColorScheme.IllustrationColors(
        stroke = PrimitiveColors.blue600,
        fillPrimary = PrimitiveColors.blue050,
        fillSecondary = PrimitiveColors.blue200,
        fillTertiary = PrimitiveColors.gray100,
        fillAccent = PrimitiveColors.teal400,
        logo = PrimitiveColors.blue500,
    ),
)

/**
 * Creates a [BitwardenColorScheme] based on dynamic Material You colors.
 */
@Suppress("LongMethod")
internal fun dynamicBitwardenColorScheme(
    materialColorScheme: ColorScheme,
    isDarkTheme: Boolean,
): BitwardenColorScheme {
    val defaultTheme = if (isDarkTheme) darkBitwardenColorScheme else lightBitwardenColorScheme
    return BitwardenColorScheme(
        isDarkTheme = isDarkTheme,
        isDynamicTheme = true,
        text = BitwardenColorScheme.TextColors(
            primary = materialColorScheme.onBackground,
            secondary = materialColorScheme.onSurface,
            interaction = materialColorScheme.primary,
            reversed = materialColorScheme.onTertiary,
            codePink = defaultTheme.text.codePink,
            codeBlue = defaultTheme.text.codeBlue,
        ),
        background = BitwardenColorScheme.BackgroundColors(
            primary = materialColorScheme.background,
            secondary = materialColorScheme.surfaceContainer,
            tertiary = materialColorScheme.surfaceContainerHighest,
            alert = materialColorScheme.inverseSurface,
            scrim = materialColorScheme.scrim.copy(alpha = 0.4f),
            pressed = materialColorScheme.onSurfaceVariant,
        ),
        stroke = BitwardenColorScheme.StrokeColors(
            divider = materialColorScheme.outlineVariant,
            border = materialColorScheme.primary,
            segmentedNav = materialColorScheme.outline,
        ),
        icon = BitwardenColorScheme.IconColors(
            primary = materialColorScheme.onBackground,
            secondary = materialColorScheme.primary,
            reversed = materialColorScheme.inversePrimary,
            badgeBackground = materialColorScheme.error,
            badgeForeground = materialColorScheme.onError,
        ),
        filledButton = BitwardenColorScheme.FilledButtonColors(
            background = materialColorScheme.primary,
            backgroundDisabled = materialColorScheme.onSurface.copy(alpha = 0.12f),
            backgroundReversed = materialColorScheme.surfaceContainerHighest,
            foreground = materialColorScheme.onPrimary,
            foregroundDisabled = materialColorScheme.onSurface.copy(alpha = 0.38f),
            foregroundReversed = materialColorScheme.onSurface,
        ),
        outlineButton = BitwardenColorScheme.OutlineButtonColors(
            border = materialColorScheme.outlineVariant,
            borderDisabled = materialColorScheme.outlineVariant,
            borderReversed = materialColorScheme.outlineVariant,
            foreground = materialColorScheme.primary,
            foregroundDisabled = materialColorScheme.onSurface,
            foregroundReversed = materialColorScheme.inversePrimary,
        ),
        toggleButton = BitwardenColorScheme.ToggleButtonColors(
            backgroundOn = materialColorScheme.primary,
            backgroundOff = materialColorScheme.onSurfaceVariant,
            switch = materialColorScheme.onPrimary,
        ),
        sliderButton = BitwardenColorScheme.SliderButtonColors(
            knobBackground = materialColorScheme.primary,
            knobLabel = materialColorScheme.onPrimary,
            filled = materialColorScheme.primary,
            unfilled = materialColorScheme.secondaryContainer,
        ),
        status = BitwardenColorScheme.StatusColors(
            strong = defaultTheme.status.strong,
            good = defaultTheme.status.good,
            weak1 = defaultTheme.status.weak1,
            weak2 = defaultTheme.status.weak2,
            error = defaultTheme.status.error,
        ),
        statusBadge = BitwardenColorScheme.StatusBadgeColors(
            success = defaultTheme.statusBadge.success,
            error = defaultTheme.statusBadge.error,
            warning = defaultTheme.statusBadge.warning,
            primary = defaultTheme.statusBadge.primary,
        ),
        illustration = BitwardenColorScheme.IllustrationColors(
            stroke = materialColorScheme.onSurface,
            fillPrimary = if (isDarkTheme) {
                materialColorScheme.outlineVariant
            } else {
                materialColorScheme.surfaceContainerHighest
            },
            fillSecondary = if (isDarkTheme) {
                materialColorScheme.surfaceContainerHighest
            } else {
                materialColorScheme.outlineVariant
            },
            fillTertiary = materialColorScheme.surface,
            fillAccent = materialColorScheme.tertiaryContainer,
            logo = materialColorScheme.primary,
        ),
    )
}

/**
 * Derives a Material [ColorScheme] from the [BitwardenColorScheme] using the [defaultColorScheme]
 * as a baseline.
 */
internal fun BitwardenColorScheme.toMaterialColorScheme(
    defaultColorScheme: ColorScheme,
): ColorScheme = defaultColorScheme.copy(
    primary = this.filledButton.background,
    onPrimary = this.filledButton.foreground,
    primaryContainer = this.background.tertiary,
    onPrimaryContainer = this.text.primary,
    inversePrimary = this.icon.secondary,
    secondary = this.icon.secondary,
    onSecondary = this.filledButton.foreground,
    secondaryContainer = this.background.tertiary,
    onSecondaryContainer = this.text.primary,
    tertiary = this.status.weak2,
    onTertiary = this.text.primary,
    tertiaryContainer = this.background.tertiary,
    onTertiaryContainer = this.text.primary,
    background = this.background.primary,
    onBackground = this.text.primary,
    surface = this.background.secondary,
    onSurface = this.text.primary,
    surfaceVariant = this.background.tertiary,
    onSurfaceVariant = this.text.secondary,
    surfaceTint = this.filledButton.background,
    inverseSurface = this.text.primary,
    inverseOnSurface = this.background.primary,
    error = this.status.error,
    onError = this.background.secondary,
    errorContainer = this.statusBadge.error.background,
    onErrorContainer = this.statusBadge.error.text,
    outline = this.stroke.border,
    outlineVariant = this.stroke.divider,
    scrim = this.background.scrim,
    surfaceBright = this.background.secondary,
    surfaceDim = this.background.primary,
    surfaceContainerLowest = this.background.secondary,
    surfaceContainerLow = this.background.primary,
    surfaceContainer = this.background.secondary,
    surfaceContainerHigh = this.background.tertiary,
    surfaceContainerHighest = this.background.tertiary,
)

/**
 * The raw colors that support the [BitwardenColorScheme].
 */
/**
 * Tokens from docs/zh-design-system.md. Primary text is one step darker than the brand fill
 * so small terracotta text clears WCAG AA on cream.
 */
private data object ZhColor {
    val background: Color = Color(color = 0xFFFBF6EF)
    val surface: Color = Color(color = 0xFFFFFDF9)
    val primary: Color = Color(color = 0xFFC2552D)
    val primaryText: Color = Color(color = 0xFFBA4E29)
    val onPrimary: Color = Color(color = 0xFFFFFFFF)
    val onSurface: Color = Color(color = 0xFF3B2A22)
    val muted: Color = Color(color = 0xFF7A675B)
    val divider: Color = Color(color = 0xFFEADFD3)
    val outline: Color = Color(color = 0xFF9E8676)
    val track: Color = Color(color = 0xFFF3E6DC)
    val error: Color = Color(color = 0xFFC4473A)

    val darkBackground: Color = Color(color = 0xFF241812)
    val darkSurface: Color = Color(color = 0xFF32241C)
    val darkSurfaceHigh: Color = Color(color = 0xFF3D2C24)
    val darkOnSurface: Color = Color(color = 0xFFF6EDE4)
    val darkMuted: Color = Color(color = 0xFFC4A894)
    val darkPrimary: Color = Color(color = 0xFFE08A6C)
    val darkDivider: Color = Color(color = 0xFF4E3B30)
    val darkError: Color = Color(color = 0xFFF0A8A0)
}

private data object PrimitiveColors {
    val gray100: Color = Color(color = 0xFFFFFBF6)
    val gray200: Color = Color(color = 0xFFFBF6F0)
    val gray300: Color = Color(color = 0xFFF3E6D8)
    val gray400: Color = Color(color = 0xFFE7D5C4)
    val gray500: Color = Color(color = 0xFFC4A48E)
    val gray600: Color = Color(color = 0xFFA7846A)
    val gray700: Color = Color(color = 0xFF8A6A52)
    val gray800: Color = Color(color = 0xFF6E5340)
    val gray900: Color = Color(color = 0xFF5C4636)
    val gray1000: Color = Color(color = 0xFF4A3728)
    val gray1100: Color = Color(color = 0xFF3A2A22)
    val gray1200: Color = Color(color = 0xFF2C211C)
    val gray1300: Color = Color(color = 0xFF4A3728)
    val gray1400: Color = Color(color = 0xFF2C211C)

    val blue050: Color = Color(color = 0xFFFBF1EC)
    val blue100: Color = Color(color = 0xFFF6E4DB)
    val blue150: Color = Color(color = 0xFFF0D0C2)
    val blue200: Color = Color(color = 0xFFE8B9A4)
    val blue300: Color = Color(color = 0xFFE09A7E)
    val blue400: Color = Color(color = 0xFFE08A6C)
    val blue500: Color = Color(color = 0xFFD97757)
    val blue600: Color = Color(color = 0xFFC46548)
    val blue700: Color = Color(color = 0xFF8C4A34)

    val teal400: Color = Color(color = 0xFF2CDDE9)

    val green050: Color = Color(color = 0xFFF0FDF4)
    val green100: Color = Color(color = 0xFFBFECC3)
    val green150: Color = Color(color = 0xFFB9F8CF)
    val green200: Color = Color(color = 0xFF6BF178)
    val green300: Color = Color(color = 0xFF0C8018)
    val green400: Color = Color(color = 0xFF08540F)
    val green800: Color = Color(color = 0xFF016630)
    val green950: Color = Color(color = 0xFF032E15)

    val red050: Color = Color(color = 0xFFFEF2F2)
    val red100: Color = Color(color = 0xFFFFECEF)
    val red150: Color = Color(color = 0xFFFFC9C9)
    val red200: Color = Color(color = 0xFFFF4E63)
    val red300: Color = Color(color = 0xFFCB263A)
    val red400: Color = Color(color = 0xFF951B2A)
    val red800: Color = Color(color = 0xFF9F0712)
    val red950: Color = Color(color = 0xFF460809)

    val yellow100: Color = Color(color = 0xFFFFF8E4)
    val yellow200: Color = Color(color = 0xFFFFBF00)
    val yellow300: Color = Color(color = 0xFFAC5800)

    val orange050: Color = Color(color = 0xFFFFF8F1)
    val orange200: Color = Color(color = 0xFFFCD9BD)
    val orange700: Color = Color(color = 0xFFB23300)
    val orange800: Color = Color(color = 0xFF8A2203)
    val orange950: Color = Color(color = 0xFF441600)

    val pink100: Color = Color(color = 0xFFC01176)
    val pink200: Color = Color(color = 0xFFFF8FD0)
}
