package com.giga.tech1000.heartbeatz.view_models;

import androidx.lifecycle.MutableLiveData;

public class SessionIdViewModel {
    private final MutableLiveData<Integer> sessionId = new MutableLiveData<>();


    public MutableLiveData<Integer> getSessionId() {
        return sessionId;
    }

    public void setSessionId(int id) {
        sessionId.setValue(id);
    }

}
