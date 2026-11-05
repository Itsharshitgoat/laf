package com.laf.lostandfound.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.laf.lostandfound.databinding.ActivityRegisterBinding;
import com.laf.lostandfound.models.User;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.utils.ValidationUtils;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = AuthRepository.getInstance();

        setupListeners();
    }

    private void setupListeners() {
        binding.btnSignUp.setOnClickListener(v -> attemptRegister());

        binding.tvSignIn.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etName.setError("Name is required");
            binding.etName.requestFocus();
            return;
        }

        if (!ValidationUtils.isValidEmail(email)) {
            binding.etEmail.setError("Please enter a valid email");
            binding.etEmail.requestFocus();
            return;
        }

        if (!ValidationUtils.isValidPhone(phone)) {
            binding.etPhone.setError("Please enter a valid phone number");
            binding.etPhone.requestFocus();
            return;
        }

        if (!ValidationUtils.isValidPassword(password)) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            binding.etConfirmPassword.requestFocus();
            return;
        }

        showLoading(true);

        authRepository.register(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess() {
                User user = new User();
                user.setName(name);
                user.setEmail(email);
                user.setPhone(phone);
                user.setUserId(authRepository.getCurrentUserId());

                authRepository.createUserProfile(user, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        // Trigger email verification dispatch
                        authRepository.sendEmailVerification(new AuthRepository.AuthCallback() {
                            @Override
                            public void onSuccess() {
                                // Silent success or logged
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                // Non-fatal
                            }
                        });

                        showLoading(false);
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(RegisterActivity.this)
                                .setTitle("Verify Your Email")
                                .setMessage("Account created for " + name + "! A verification link has been sent to " + email + ". Please verify your email before logging in.")
                                .setPositiveButton("Go to Login", (dialog, which) -> {
                                    authRepository.signOut();
                                    finish();
                                })
                                .setCancelable(false)
                                .show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showLoading(false);
                        Toast.makeText(RegisterActivity.this,
                                "Profile creation failed: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                showLoading(false);
                Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSignUp.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPhone.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
