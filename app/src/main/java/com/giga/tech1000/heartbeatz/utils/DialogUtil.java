package com.giga.tech1000.heartbeatz.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.giga.tech1000.heartbeatz.R;
import com.google.android.material.checkbox.MaterialCheckBox;

public class DialogUtil {

    public interface DeleteCallback {
        void onDelete(boolean deleteFromDevice);
    }

    public static void showDeleteDialog(
            Context context,
            String songTitle,
            DeleteCallback callback
    ) {

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_delete_song, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        MaterialCheckBox cbDelete = view.findViewById(R.id.cbDeleteDevice);

        tvTitle.setText("Delete song");
        tvMessage.setText("Are you sure you want to delete " + songTitle + "?");

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setView(view)
                        .setCancelable(true)
                        .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                        .setPositiveButton("Delete", (d, w) -> {
                            boolean deleteFromDevice = cbDelete.isChecked();
                            callback.onDelete(deleteFromDevice);
                        });

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.show();

        // Optional: make buttons more Material 3 aligned
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(context.getColor(android.R.color.holo_red_light));
    }
}