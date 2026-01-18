package com.eqraa.reader.reader.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.eqraa.reader.ui.theme.MonochromeTheme
import androidx.compose.ui.graphics.Color

val LocalReaderTheme = staticCompositionLocalOf<MonochromeTheme> { 
    error("No ReaderTheme provided") 
}

@Composable
fun ReaderTheme(
    themeValues: MonochromeTheme = MonochromeTheme.PureWhite,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalReaderTheme provides themeValues
    ) {
        MaterialTheme(
            colors = lightColors(
                primary = themeValues.accent,
                background = themeValues.background,
                surface = themeValues.surface,
                onPrimary = themeValues.text,
                onBackground = themeValues.text,
                onSurface = themeValues.text
            ),
            content = content
        )
    }
}
