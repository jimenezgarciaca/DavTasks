package org.tasks.ui

import android.app.Activity
import org.tasks.R
import org.tasks.compose.chips.ChipDataProvider
import org.tasks.themes.ColorProvider
import org.tasks.themes.chipColors
import javax.inject.Inject

@Deprecated("remove me")
class ChipProvider @Inject constructor(
    private val activity: Activity,
    val lists: ChipDataProvider,
    private val colorProvider: ColorProvider,
) {
    private val isDark = activity.resources.getBoolean(R.bool.is_dark)

    fun getColor(theme: Int): Int {
        if (theme != 0) {
            return colorProvider.getChipColor(theme).primaryColor
        }
        return chipColors(activity.getColor(org.tasks.kmp.R.color.grey_300), isDark).backgroundColor
    }
}
