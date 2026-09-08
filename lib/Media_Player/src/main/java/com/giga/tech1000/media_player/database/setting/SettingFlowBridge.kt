package com.giga.tech1000.media_player.database.setting

import com.giga.tech1000.media_player.models.extended_models.SettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object SettingFlowBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun <SettingEntity> collect(
        flow: Flow<SettingEntity>,
        onEach: (SettingEntity) -> Unit
    ) {
        scope.launch {
            flow.collect {
                onEach(it)
            }
        }
    }

}