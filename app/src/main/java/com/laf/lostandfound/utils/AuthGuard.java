package com.laf.lostandfound.utils;

import android.app.Activity;
import android.content.Intent;

import com.laf.lostandfound.activities.LoginActivity;
import com.laf.lostandfound.repositories.AuthRepository;

/** Small helper that keeps unauthenticated visitors out of write screens. */
public final class AuthGuard {

    private AuthGuard() {
    }

    public static boolean isSignedIn() {
        return AuthRepository.getInstance().isLoggedIn();
    }

    public static String uidOrEmpty() {
        return AuthRepository.getInstance().getCurrentUserId();
    }

    /**
     * @return true when signed in. When false, the login screen is opened and
     * the caller should stop its work / finish.
     */
    public static boolean requireSignedIn(Activity activity) {
        if (isSignedIn()) return true;
        if (activity != null) {
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        }
        return false;
    }
}
