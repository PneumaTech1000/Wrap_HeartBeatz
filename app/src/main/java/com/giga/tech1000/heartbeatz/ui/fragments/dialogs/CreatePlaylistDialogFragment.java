package com.giga.tech1000.heartbeatz.ui.fragments.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class CreatePlaylistDialogFragment extends DialogFragment {

    public interface Callback {
        void onCreatePlaylist(@NonNull String name);
    }

    private Callback callback;

    public static CreatePlaylistDialogFragment newInstance() {
        return new CreatePlaylistDialogFragment();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        Context context = requireContext();

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("Playlist name");

        TextInputEditText editText = new TextInputEditText(context);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        inputLayout.addView(editText);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Create playlist")
                        .setView(inputLayout)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Create", null); // we override later

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String name = editText.getText() != null
                        ? editText.getText().toString().trim()
                        : "";

                if (name.isEmpty()) {
                    inputLayout.setError("Playlist name required");
                    return;
                }

                inputLayout.setError(null);

                if (callback != null) {
                    callback.onCreatePlaylist(name);
                }

                dialog.dismiss();
            });
        });

        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        return dialog;
    }
}

