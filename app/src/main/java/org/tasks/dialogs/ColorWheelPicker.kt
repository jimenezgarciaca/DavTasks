package org.tasks.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import org.tasks.R
import org.tasks.resources.getString

class ColorWheelPicker : DialogFragment() {

    companion object {
        const val EXTRA_SELECTED = "extra_selected"
        private const val EXTRA_REQUEST_KEY = "extra_request_key"

        fun newColorWheel(requestKey: String, selected: Int): ColorWheelPicker {
            val args = Bundle()
            args.putInt(EXTRA_SELECTED, selected)
            args.putString(EXTRA_REQUEST_KEY, requestKey)
            val dialog = ColorWheelPicker()
            dialog.arguments = args
            return dialog
        }
    }

    interface ColorPickedCallback {
        fun onColorPicked(color: Int)
    }

    var dialog: AlertDialog? = null
    var selected = -1
    var callback: ColorPickedCallback? = null

    private val requestKey: String
        get() = requireArguments().getString(EXTRA_REQUEST_KEY)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selected = savedInstanceState?.getInt(EXTRA_SELECTED) ?: requireArguments().getInt(EXTRA_SELECTED, 0)

        val builder = ColorPickerDialogBuilder
                .with(activity)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(7)
                .setOnColorChangedListener { which ->
                    selected = which
                }
                .setOnColorSelectedListener { which ->
                    selected = which
                }
                .lightnessSliderOnly()
                .setPositiveButton(getString(R.string.ok)) { _, _, _ ->
                    deliverSelection()
                }
                .setNegativeButton(R.string.cancel, null)
        if (selected != 0) {
            builder.initialColor(selected)
        }
        dialog = builder.build()
        return dialog as Dialog
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        if (activity is ColorPickedCallback) {
            callback = activity
        }
    }

    private fun deliverSelection() {
        dialog?.dismiss()
        if (callback != null) {
            callback?.onColorPicked(selected)
        } else {
            parentFragmentManager.setFragmentResult(
                requestKey,
                bundleOf(EXTRA_SELECTED to selected)
            )
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        // no result on cancel
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(EXTRA_SELECTED, selected)
    }
}
