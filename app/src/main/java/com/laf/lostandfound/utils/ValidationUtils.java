package com.laf.lostandfound.utils;

import android.util.Patterns;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        String trimmed = email.trim();
        try {
            if (Patterns.EMAIL_ADDRESS != null) {
                return Patterns.EMAIL_ADDRESS.matcher(trimmed).matches();
            }
        } catch (Throwable ignored) {
        }
        return EMAIL_PATTERN.matcher(trimmed).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.trim().length() >= 6;
    }

    public static boolean isValidName(String name) {
        return name != null && name.trim().length() >= 2;
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String clean = phone.replaceAll("[^0-9+]", "");
        return clean.length() >= 7;
    }

    public static boolean isValidTitle(String title) {
        return title != null && title.trim().length() >= 3;
    }

    public static boolean isValidDescription(String description) {
        return description != null && description.trim().length() >= 5;
    }

    public static boolean isNotEmpty(String... fields) {
        if (fields == null) return false;
        for (String field : fields) {
            if (field == null || field.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
