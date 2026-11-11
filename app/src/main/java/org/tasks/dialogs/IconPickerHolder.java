package org.tasks.dialogs;

import android.content.Context;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import org.tasks.Callback;
import org.tasks.databinding.DialogIconPickerCellBinding;
import org.tasks.themes.DrawableUtil;

public class IconPickerHolder extends RecyclerView.ViewHolder {

  private final Context context;
  private final Callback<Integer> onClick;
  private final AppCompatImageView imageView;

  private int index;

  IconPickerHolder(
      Context context,
      DialogIconPickerCellBinding binding,
      Callback<Integer> onClick) {
    super(binding.getRoot());

    imageView = binding.icon;
    imageView.setOnClickListener(v -> onClick.call(index));

    this.context = context;
    this.onClick = onClick;
  }

  public void bind(int index, int icon, int tint) {
    this.index = index;
    imageView.setImageDrawable(DrawableUtil.getWrapped(context, icon));
    DrawableUtil.setTint(imageView.getDrawable(), tint);
  }
}
