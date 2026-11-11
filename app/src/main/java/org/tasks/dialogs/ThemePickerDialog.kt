package org.tasks.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.todoroo.andlib.utility.AndroidUtilities.preS
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import javax.inject.Inject

@AndroidEntryPoint
class ThemePickerDialog : DialogFragment() {

    companion object {
        const val EXTRA_SELECTED = "extra_selected"
        const val EXTRA_WIDGET = "extra_widget"
        const val REQUEST_KEY = "theme_picker_result"

        fun newThemePickerDialog(
            selected: Int,
            widget: Boolean = false
        ): ThemePickerDialog {
            val args = Bundle()
            args.putInt(EXTRA_SELECTED, selected)
            args.putBoolean(EXTRA_WIDGET, widget)
            val dialog = ThemePickerDialog()
            dialog.arguments = args
            return dialog
        }
    }

    @Inject lateinit var dialogBuilder: DialogBuilder

    private var selected = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selected = savedInstanceState?.getInt(EXTRA_SELECTED) ?: requireArguments().getInt(EXTRA_SELECTED)
        val widget = arguments?.getBoolean(EXTRA_WIDGET) ?: false
        val themes = resources
            .getStringArray(if (widget) R.array.widget_themes else R.array.base_theme_names)
            .let { if (widget && preS()) it.dropLast(1).toTypedArray() else it }

        return dialogBuilder.newDialog()
            .setTitle(R.string.theme)
            .setSingleChoiceItems(themes, selected) { dialog, which ->
                selected = which
                deliverResult()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deliverResult() {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(EXTRA_SELECTED to selected)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_SELECTED, selected)
    }
}
