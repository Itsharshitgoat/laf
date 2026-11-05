package com.laf.lostandfound;

import android.app.Application;

import com.google.firebase.FirebaseApp;

/**
 * Application class for Lost & Found app.
 * Handles global application initialization.
 */
public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
    }

    public static App getInstance() {
        return instance;
    }
}
