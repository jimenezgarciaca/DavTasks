package org.tasks.dialogs

import android.app.Activity
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.tasks.R
import org.tasks.databinding.DialogIconPickerCellBinding
import org.tasks.dialogs.ColorPalettePicker.Pickable

class ColorPickerAdapter(
    private val activity: Activity,
    private val onSelected: (Int) -> Unit
) : ListAdapter<Pickable, IconPickerHolder>(DiffCallback()) {

    enum class Palette {
        COLORS,
        LAUNCHERS,
        WIDGET
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        IconPickerHolder(
            activity,
            DialogIconPickerCellBinding.inflate(activity.layoutInflater, parent, false),
            onSelected
        )

    override fun onBindViewHolder(holder: IconPickerHolder, position: Int) {
        holder.bind(position, R.drawable.color_picker, getItem(position).pickerColor)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Pickable>() {
        override fun areItemsTheSame(oldItem: Pickable, newItem: Pickable): Boolean =
                oldItem == newItem

        override fun areContentsTheSame(oldItem: Pickable, newItem: Pickable): Boolean = true
    }
}