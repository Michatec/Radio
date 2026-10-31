package com.michatec.radio.api;

interface IRadioApi {
    void play();
    void pause();
    void stop();
    boolean isPlaying();
    String getCurrentStationName();
    String getCurrentMetadata();
    void playStationByUuid(String uuid);
    String getSavedStationsJson();
    String getFavoriteStationsJson();
}
