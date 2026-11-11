package com.todoroo.astrid.service

import android.content.Context
import androidx.annotation.ColorRes
import org.tasks.R
import org.tasks.preferences.Preferences
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import javax.inject.Inject

/**
 * Registra la versión instalada. DavTasks parte de Tasks.org 15.14, así que no hay
 * instalaciones anteriores que migrar; las constantes de versión solo sirven para
 * convertir copias de seguridad antiguas de Tasks.org al importarlas.
 */
class Upgrader @Inject constructor(
    private val preferences: Preferences,
) {
    fun upgrade(from: Int, to: Int) {
        if (from == 0) {
            preferences.installVersion = to
            preferences.installDate = currentTimeMillis()
            preferences.deviceInstallVersion = to
        } else if (preferences.deviceInstallVersion == 0) {
            preferences.deviceInstallVersion = from
        }
        preferences.setCurrentVersion(to)
    }

    companion object {
        const val V6_4 = 546
        const val V8_2 = 675
        const val V9_6 = 90600
        const val V12_4 = 120400
        const val V12_8 = 120800

        fun getAndroidColor(context: Context, index: Int): Int {
            val legacyColor = getLegacyColor(index)
            return if (legacyColor == 0) 0 else context.getColor(legacyColor)
        }

        @ColorRes
        private fun getLegacyColor(index: Int): Int = when (index) {
            0 -> org.tasks.kmp.R.color.blue_grey_500
            1 -> org.tasks.kmp.R.color.grey_900
            2 -> org.tasks.kmp.R.color.red_500
            3 -> org.tasks.kmp.R.color.pink_500
            4 -> org.tasks.kmp.R.color.purple_500
            5 -> org.tasks.kmp.R.color.deep_purple_500
            6 -> org.tasks.kmp.R.color.indigo_500
            7 -> org.tasks.kmp.R.color.blue_500
            8 -> org.tasks.kmp.R.color.light_blue_500
            9 -> org.tasks.kmp.R.color.cyan_500
            10 -> org.tasks.kmp.R.color.teal_500
            11 -> org.tasks.kmp.R.color.green_500
            12 -> org.tasks.kmp.R.color.light_green_500
            13 -> org.tasks.kmp.R.color.lime_500
            14 -> org.tasks.kmp.R.color.yellow_500
            15 -> org.tasks.kmp.R.color.amber_500
            16 -> org.tasks.kmp.R.color.orange_500
            17 -> org.tasks.kmp.R.color.deep_orange_500
            18 -> org.tasks.kmp.R.color.brown_500
            19 -> org.tasks.kmp.R.color.grey_500
            20 -> R.color.white_100
            else -> 0
        }
    }
}
