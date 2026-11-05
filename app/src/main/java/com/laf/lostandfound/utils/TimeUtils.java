package com.laf.lostandfound.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static String getTimeAgo(long timestamp) {
        if (timestamp <= 0) return "Recently";

        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 0) {
            long futureMinutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(-diff));
            if (futureMinutes < 60) return "in " + futureMinutes + "m";
            long futureHours = TimeUnit.MINUTES.toHours(futureMinutes);
            if (futureHours < 24) return "in " + futureHours + "h";
            return "in " + TimeUnit.HOURS.toDays(futureHours) + "d";
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String getFormattedDate(long timestamp) {
        if (timestamp <= 0) return "";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String getFormattedDateTime(long timestamp) {
        if (timestamp <= 0) return "";
        return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String getShortDateTime(long timestamp) {
        if (timestamp <= 0) return "";
        return new SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "Good morning";
        if (hour >= 12 && hour < 17) return "Good afternoon";
        return "Good evening";
    }

    public static String getIncidentLabel(String type, long incidentAt) {
        String verb = Constants.TYPE_FOUND.equalsIgnoreCase(type) ? "Found" : "Lost";
        return verb + " " + getTimeAgo(incidentAt);
    }
}
