package com.giga.tech1000.heartbeatz.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.utils.audio.ParametricEQBand;
import com.giga.tech1000.heartbeatz.view_models.extended_models.EqualizerViewModel;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager for handling equalizer presets.
 * Provides functionality to save, load, import, export, and organize presets.
 */
public class PresetManager {

    // ======================
    // Constants
    // ======================

    private static final String PREFS_NAME = "heartbeatz_presets";
    private static final String KEY_PRESET_LIST = "preset_list";
    private static final String KEY_LAST_USED_PRESET = "last_used_preset";
    private static final String PRESET_FILE_EXTENSION = ".eqpreset";
    private static final String PRESET_DIRECTORY = "HeartBeatz/Presets";

    // ======================
    // Fields
    // ======================

    private final Context context;
    private final SharedPreferences sharedPreferences;

    // ======================
    // Constructor
    // ======================

    public PresetManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ======================
    // Public Methods - Save/Load
    // ======================

    /**
     * Save a preset to persistent storage
     */
    public void savePreset(@NonNull Preset preset) {
        List<Preset> presets = getAllPresets();

        // Check if preset with same ID already exists (update)
        boolean updated = false;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(preset.getId())) {
                presets.set(i, preset);
                updated = true;
                break;
            }
        }

        // If not found, add as new preset
        if (!updated) {
            presets.add(preset);
        }

        // Sort presets by name for consistent display
        Collections.sort(presets, Comparator.comparing(Preset::getName));

        // Save to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        JSONArray jsonArray = new JSONArray();
        for (Preset p : presets) {
            try {
                jsonArray.put(p.toJson());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        editor.putString(KEY_PRESET_LIST, jsonArray.toString());
        editor.apply();
    }

    /**
     * Load a preset by ID
     */
    public Preset loadPreset(String presetId) {
        List<Preset> presets = getAllPresets();
        for (Preset preset : presets) {
            if (preset.getId().equals(presetId)) {
                return preset;
            }
        }
        return null; // Not found
    }

    /**
     * Get all presets
     */
    @NonNull
    public List<Preset> getAllPresets() {
        List<Preset> presets = new ArrayList<>();
        String jsonString = sharedPreferences.getString(KEY_PRESET_LIST, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    Preset preset = Preset.fromJson(jsonArray.getJSONObject(i));
                    presets.add(preset);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Skip invalid preset entries
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Return empty list if JSON is malformed
        }

        return presets;
    }

    /**
     * Get all presets sorted by usage count (most used first)
     */
    @NonNull
    public List<Preset> getPresetsByUsage() {
        List<Preset> presets = getAllPresets();
        Collections.sort(presets, Comparator.comparingInt(Preset::getUsageCount).reversed());
        return presets;
    }

    /**
     * Get all presets sorted by modification time (most recent first)
     */
    @NonNull
    public List<Preset> getPresetsByRecent() {
        List<Preset> presets = getAllPresets();
        Collections.sort(presets, Comparator.comparingLong(Preset::getModifiedTimestamp).reversed());
        return presets;
    }

    /**
     * Delete a preset by ID
     */
    public boolean deletePreset(String presetId) {
        List<Preset> presets = getAllPresets();
        boolean removed = false;

        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(presetId)) {
                presets.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            // Save updated list
            SharedPreferences.Editor editor = sharedPreferences.edit();
            JSONArray jsonArray = new JSONArray();
            for (Preset p : presets) {
                try {
                    jsonArray.put(p.toJson());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            editor.putString(KEY_PRESET_LIST, jsonArray.toString());
            editor.apply();
        }

        return removed;
    }

    /**
     * Get the last used preset
     */
    public Preset getLastUsedPreset() {
        String lastUsedId = sharedPreferences.getString(KEY_LAST_USED_PRESET, null);
        if (lastUsedId != null) {
            return loadPreset(lastUsedId);
        }
        return null;
    }

    /**
     * Set the last used preset
     */
    public void setLastUsedPreset(@NonNull Preset preset) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LAST_USED_PRESET, preset.getId());
        editor.apply();
    }

    // ======================
    // Public Methods - Import/Export
    // ======================

    /**
     * Export a preset to a file
     */
    public boolean exportPreset(@NonNull Preset preset, @NonNull String fileName) {
        try {
            File dir = new File(context.getExternalFilesDir(null), PRESET_DIRECTORY);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, fileName + PRESET_FILE_EXTENSION);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(preset.toJsonString());
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Import a preset from a file
     */
    public Preset importPreset(@NonNull String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            Preset preset = Preset.fromJsonString(jsonContent.toString());
            // Generate a new ID to avoid conflicts
            preset.setId(java.util.UUID.randomUUID().toString());
            preset.touch(); // Update timestamps
            return preset;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Export all presets to a single file
     */
    public boolean exportAllPresets(@NonNull String fileName) {
        try {
            File dir = new File(context.getExternalFilesDir(null), PRESET_DIRECTORY);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, fileName + PRESET_FILE_EXTENSION);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            JSONArray jsonArray = new JSONArray();
            for (Preset preset : getAllPresets()) {
                try {
                    jsonArray.put(preset.toJson());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            writer.write(jsonArray.toString());
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Import presets from a file (replaces current presets)
     */
    public boolean importAllPresets(@NonNull String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            List<Preset> importedPresets = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    Preset preset = Preset.fromJson(jsonArray.getJSONObject(i));
                    // Generate new IDs to avoid conflicts
                    preset.setId(java.util.UUID.randomUUID().toString());
                    preset.touch();
                    importedPresets.add(preset);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Skip invalid presets
                }
            }

            // Save imported presets
            SharedPreferences.Editor editor = sharedPreferences.edit();
            JSONArray newJsonArray = new JSONArray();
            for (Preset preset : importedPresets) {
                try {
                    newJsonArray.put(preset.toJson());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            editor.putString(KEY_PRESET_LIST, newJsonArray.toString());
            editor.apply();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ======================
    // Public Methods - Organization
    // ======================

    /**
     * Get all unique categories
     */
    @NonNull
    public List<String> getAllCategories() {
        List<Preset> presets = getAllPresets();
        Set<String> categories = new HashSet<>();
        for (Preset preset : presets) {
            String category = preset.getCategory();
            if (category != null && !category.isEmpty()) {
                categories.add(category);
            }
        }
        List<String> sortedCategories = new ArrayList<>(categories);
        Collections.sort(sortedCategories);
        return sortedCategories;
    }

    /**
     * Get all presets in a specific category
     */
    @NonNull
    public List<Preset> getPresetsByCategory(@NonNull String category) {
        List<Preset> allPresets = getAllPresets();
        List<Preset> filteredPresets = new ArrayList<>();
        for (Preset preset : allPresets) {
            if (preset.getCategory().equalsIgnoreCase(category)) {
                filteredPresets.add(preset);
            }
        }
        return filteredPresets;
    }

    /**
     * Search presets by name, tags, or description
     */
    @NonNull
    public List<Preset> searchPresets(@NonNull String query) {
        if (query == null || query.isEmpty()) {
            return getAllPresets();
        }

        String lowerCaseQuery = query.toLowerCase();
        List<Preset> allPresets = getAllPresets();
        List<Preset> matchingPresets = new ArrayList<>();

        for (Preset preset : allPresets) {
            if (preset.getName().toLowerCase().contains(lowerCaseQuery) ||
                preset.getTags().toLowerCase().contains(lowerCaseQuery) ||
                preset.getDescription().toLowerCase().contains(lowerCaseQuery)) {
                matchingPresets.add(preset);
            }
        }

        return matchingPresets;
    }

    // ======================
    // Public Methods - Smart Suggestions
    // ======================

    /**
     * Get preset suggestions based on audio characteristics
     * This is a simplified implementation - in a real app, this would use
     * audio analysis to suggest appropriate presets
     */
    @NonNull
    public List<Preset> getSuggestedPresets(float bassLevel, float midLevel, float trebleLevel) {
        List<Preset> allPresets = getAllPresets();

        // Score each preset based on how well it matches the audio characteristics
        PresetScore[] scoredPresets = new PresetScore[allPresets.size()];
        for (int i = 0; i < allPresets.size(); i++) {
            Preset preset = allPresets.get(i);
            float score = calculateMatchScore(preset, bassLevel, midLevel, trebleLevel);
            scoredPresets[i] = new PresetScore(preset, score);
        }

        // Sort by score (highest first)
        java.util.Arrays.sort(scoredPresets, (a, b) -> Float.compare(b.score, a.score));

        // Return top 5 suggestions
        List<Preset> suggestions = new ArrayList<>();
        int count = Math.min(5, scoredPresets.length);
        for (int i = 0; i < count; i++) {
            suggestions.add(scoredPresets[i].preset);
        }

        return suggestions;
    }

    /**
     * Calculate how well a preset matches given audio characteristics
     * @param preset The preset to evaluate
     * @param bassLevel Normalized bass level (0-1)
     * @param midLevel Normalized mid-level (0-1)
     * @param trebleLevel Normalized treble level (0-1)
     * @return Match score (0-1, higher is better)
     */
    private float calculateMatchScore(Preset preset, float bassLevel, float midLevel, float trebleLevel) {
        if (preset.getEqBands() == null || preset.getEqBands().isEmpty()) {
            return 0.5f; // Neutral score for presets with no bands
        }

        // Calculate average gain in different frequency ranges
        float bassGain = 0f;
        float midGain = 0f;
        float trebleGain = 0f;
        int bassCount = 0, midCount = 0, trebleCount = 0;

        for (ParametricEQBand band : preset.getEqBands()) {
            float freq = band.getFrequencyHz();
            float gain = band.getGainDb(); // -15 to +15 dB

            // Normalize gain to 0-1 range (where 0.5 is 0dB)
            float normalizedGain = (gain + 15f) / 30f;

            if (freq < 250) { // Bass range
                bassGain += normalizedGain;
                bassCount++;
            } else if (freq < 4000) { // Mid-range
                midGain += normalizedGain;
                midCount++;
            } else { // Treble range
                trebleGain += normalizedGain;
                trebleCount++;
            }
        }

        // Calculate averages (avoid division by zero)
        if (bassCount > 0) bassGain /= bassCount;
        if (midCount > 0) midGain /= midCount;
        if (trebleCount > 0) trebleGain /= trebleCount;

        // Calculate how well the preset's frequency response matches the audio
        // We want the preset to compensate for the audio characteristics
        float bassMatch = 1f - Math.abs(bassGain - (1f - bassLevel)); // Inverse relationship
        float midMatch = 1f - Math.abs(midGain - (1f - midLevel));
        float trebleMatch = 1f - Math.abs(trebleGain - (1f - trebleLevel));

        // Clamp to 0-1 range
        bassMatch = Math.clamp(bassMatch, 0f, 1f);
        midMatch = Math.clamp(midMatch, 0f, 1f);
        trebleMatch = Math.clamp(trebleMatch, 0f, 1f);

        // Weighted average (you could adjust these weights based on importance)
        return (bassMatch * 0.4f + midMatch * 0.3f + trebleMatch * 0.3f);
    }

    // ======================
    // Inner Classes
    // ======================

    /**
     * Helper class for scoring presets during suggestion generation
     */
    private static class PresetScore {
        Preset preset;
        float score;

        PresetScore(Preset preset, float score) {
            this.preset = preset;
            this.score = score;
        }
    }
}