package com.laf.lostandfound.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.laf.lostandfound.databinding.ActivityLoginBinding;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.utils.ValidationUtils;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = AuthRepository.getInstance();

        setupListeners();
    }

    private void setupListeners() {
        binding.btnSignIn.setOnClickListener(v -> attemptLogin());

        binding.tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void showForgotPasswordDialog() {
        EditText inputEmail = new EditText(this);
        inputEmail.setHint("Enter your registered email");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        // Pre-fill if user already typed an email
        String currentEmail = binding.etEmail.getText().toString().trim();
        if (!currentEmail.isEmpty()) {
            inputEmail.setText(currentEmail);
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(inputEmail);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset Password")
                .setMessage("We will send a password reset link to your email address.")
                .setView(container)
                .setPositiveButton("Send Reset Link", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (!ValidationUtils.isValidEmail(email)) {
                        Toast.makeText(LoginActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    showLoading(true);
                    authRepository.sendPasswordResetEmail(email, new AuthRepository.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            showLoading(false);
                            new MaterialAlertDialogBuilder(LoginActivity.this)
                                    .setTitle("Check Your Email")
                                    .setMessage("A password reset link has been dispatched to " + email + ". Follow the instructions in the email to set a new password.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            showLoading(false);
                            Toast.makeText(LoginActivity.this, "Reset failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (!ValidationUtils.isValidEmail(email)) {
            binding.etEmail.setError("Please enter a valid email");
            binding.etEmail.requestFocus();
            return;
        }

        if (!ValidationUtils.isValidPassword(password)) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus();
            return;
        }

        showLoading(true);

        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                if (!authRepository.isEmailVerified()) {
                    new MaterialAlertDialogBuilder(LoginActivity.this)
                            .setTitle("Email Verification Required")
                            .setMessage("Your email address is not yet verified. Please verify your email via the link sent to your inbox before accessing Lost & Found.")
                            .setPositiveButton("Resend Verification", (dialog, which) -> {
                                authRepository.sendEmailVerification(new AuthRepository.AuthCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Toast.makeText(LoginActivity.this, "Verification email sent to " + email, Toast.LENGTH_SHORT).show();
                                        authRepository.signOut();
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Toast.makeText(LoginActivity.this, error, Toast.LENGTH_SHORT).show();
                                        authRepository.signOut();
                                    }
                                });
                            })
                            .setNegativeButton("OK", (dialog, which) -> authRepository.signOut())
                            .show();
                } else {
                    proceedToMain();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                showLoading(false);
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void proceedToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSignIn.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.tvForgotPassword.setEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
