package com.navatar.common;

public interface TextToSpeechProvider {

    void speak(String text);

    void speak(int resource);

}
