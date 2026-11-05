package com.laf.lostandfound.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.laf.lostandfound.databinding.ActivitySplashBinding;
import com.laf.lostandfound.repositories.AuthRepository;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private AuthRepository authRepository;

    private static final long SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = AuthRepository.getInstance();

        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, SPLASH_DELAY_MS);
    }

    private void navigateNext() {
        Intent intent;
        if (authRepository.isLoggedIn()) {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
