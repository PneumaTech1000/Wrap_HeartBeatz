package com.giga.tech1000.heartbeatz.ui;

import com.giga.tech1000.heartbeatz.view_models.extended_models.SettingViewModel;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;

public class SettingUiUpdate {
    private final SettingViewModel settingViewModel;
    private SettingEntity setting;

    public SettingUiUpdate(SettingViewModel model) {
        settingViewModel = model;
    }

    public void applyGlobalSetting() {
    }
}
