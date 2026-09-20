package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Song;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.atomic.AtomicBoolean;

@OptIn(markerClass = UnstableApi.class)
public class EditSongInfoPanel {
    private final View root;
    private final FragmentHome fragmentHome;
    private final Context context;
    private Song song;
    private final LibraryRepository repository;

    private ShapeableImageView ivAlbumEdit;
    private TextInputEditText etTitle, etArtist, etAlbum, etGenre;

    private final AtomicBoolean isVisible = new AtomicBoolean(false);

    private Uri selectedArtUri;

    public EditSongInfoPanel(@NonNull FragmentHome fragment, @NonNull ViewGroup parent) {
        this.fragmentHome = fragment;
        this.context = fragment.requireContext();
        this.repository = HeartBeatzApp.container(getContext()).requireUiThread().getLibrarySetViewModel().getRepo();

        root = LayoutInflater.from(context).inflate(R.layout.media_edit_song_info, parent, false);


        initViews(root);
    }

    public void setCurrentSong(Song s) {
        this.song = s;

        setIsVisible(true);
        loadSongData();
    }

    private void initViews(View root) {
        ivAlbumEdit = root.findViewById(R.id.ivAlbum);
        etTitle = root.findViewById(R.id.etTitle);
        etArtist = root.findViewById(R.id.etArtist);
        etAlbum = root.findViewById(R.id.etAlbum);
        etGenre = root.findViewById(R.id.etGenre);

        MaterialButton btnChangeArt = root.findViewById(R.id.btnChangeImage);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancel);
        MaterialButton btnSave = root.findViewById(R.id.btnSave);

        btnChangeArt.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launchImagePicker();
            } else {
                launchLegacyPicker();
            }
        });

        btnCancel.setOnClickListener(v -> fragmentHome.hideMediaDetailsPanel());

        btnSave.setOnClickListener(v -> saveChanges());
    }

    private void loadSongData() {
        ImageLoader.load(ivAlbumEdit, song.getAlbumArt());
        etTitle.setText(song.getTitle());
        etArtist.setText(song.getArtist());
        etAlbum.setText(song.getAlbum());
        etGenre.setText(song.getGenreName());
        
        selectedArtUri = song.getAlbumArt();
    }

    private void saveChanges() {
        if (etTitle.getText() == null || etArtist.getText() == null || etAlbum.getText() == null || etGenre.getText() == null) {
            return;
        }

        String newTitle = etTitle.getText().toString().trim();
        String newArtist = etArtist.getText().toString().trim();
        String newAlbum = etAlbum.getText().toString().trim();
        String newGenre = etGenre.getText().toString().trim();

        if (newTitle.isEmpty()) {
            etTitle.setError(context.getString(R.string.error_title_empty));
            return;
        }

        // Show progress or disable buttons
        MaterialButton btnSave = root.findViewById(R.id.btnSave);
        btnSave.setEnabled(false);

        // Update local object (UI purposes)
        // We MUST create a copy of the song object if we want the DiffUtil to detect a change
        // because the original object is the one already in the list.
        Song updatedSong = Song.copy(song);
        updatedSong.setTitle(newTitle);
        updatedSong.setArtist(newArtist);
        updatedSong.setAlbum(newAlbum);
        updatedSong.setGenreName(newGenre);
        updatedSong.setAlbumArt(selectedArtUri);

        // Persist changes to Database and MediaStore
        repository.updateSongMetadata(updatedSong, new LibraryRepository.OnMetadataUpdateListener() {
            @Override
            public void onUpdateSuccess() {
                btnSave.setEnabled(true);
                Toast.makeText(context, context.getString(R.string.success_changes_saved), Toast.LENGTH_SHORT).show();
                fragmentHome.hideMediaDetailsPanel();
                
                // Trigger a light refresh of genres and albums to reflect name changes
                HeartBeatzApp.container(getContext()).requireUiThread().getScannerManager().runIncrementalMediaRefresh();
            }

            @Override
            public void onPermissionRequired(PendingIntent pendingIntent) {
                btnSave.setEnabled(true);
                // The permission denial usually happens here on Android 10+
                // We need to launch the IntentSender provided by RecoverableSecurityException
                fragmentHome.requestUpdate(pendingIntent);
            }

            @Override
            public void onUpdateError(Exception e) {
                btnSave.setEnabled(true);
                if (e instanceof SecurityException && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Toast.makeText(context, "Permission denied. Please grant storage permissions.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, context.getString(R.string.error_save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void launchImagePicker() {
        fragmentHome.imagePickerLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
    }

    public void launchLegacyPicker() {
        fragmentHome.legacyPicker.launch("image/*");
    }

    public void handleImagePickerResult(Uri uri) {
        if (uri != null) {
            onImagePicked(uri);
        } else {
            Toast.makeText(context, context.getString(R.string.error_no_image_selected), Toast.LENGTH_SHORT).show();
        }
    }
    
    public void onImagePicked(Uri uri) {
        this.selectedArtUri = uri;
        ImageLoader.load(ivAlbumEdit, uri);
    }

    public AtomicBoolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(boolean isVisible) {
        this.isVisible.set(isVisible);
    }

    public View getView() {
        return root;
    }

    public void setBottomPadding(int dimensionPixelSize) {
        root.setPadding(0, 0, 0, dimensionPixelSize);
    }
}
