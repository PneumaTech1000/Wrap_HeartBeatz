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

public class RenamePlaylistDialogFragment extends DialogFragment {

    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final String ARG_CURRENT_NAME = "current_name";

    public interface Callback {
        void onRenamePlaylist(long playlistId, @NonNull String newName);
    }

    private Callback callback;

    public static RenamePlaylistDialogFragment newInstance(long playlistId, String currentName) {
        RenamePlaylistDialogFragment fragment = new RenamePlaylistDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        args.putString(ARG_CURRENT_NAME, currentName);
        fragment.setArguments(args);
        return fragment;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        long playlistId = getArguments().getLong(ARG_PLAYLIST_ID);
        String currentName = getArguments().getString(ARG_CURRENT_NAME);

        Context context = requireContext();

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("New playlist name");

        TextInputEditText editText = new TextInputEditText(context);
        editText.setSingleLine(true);
        editText.setText(currentName);
        editText.setSelection(currentName.length());
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        inputLayout.addView(editText);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Rename playlist")
                        .setView(inputLayout)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Rename", null);

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

                if (name.equals(currentName)) {
                    dialog.dismiss();
                    return;
                }

                inputLayout.setError(null);

                if (callback != null) {
                    callback.onRenamePlaylist(playlistId, name);
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
