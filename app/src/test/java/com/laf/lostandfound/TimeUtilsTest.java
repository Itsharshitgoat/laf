package com.laf.lostandfound;

import com.laf.lostandfound.utils.TimeUtils;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimeUtilsTest {

    @Test
    public void testJustNow() {
        long now = System.currentTimeMillis();
        assertEquals("Just now", TimeUtils.getTimeAgo(now - 1000));
    }

    @Test
    public void testMinutesAgo() {
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - TimeUnit.MINUTES.toMillis(5);
        assertEquals("5m ago", TimeUtils.getTimeAgo(fiveMinutesAgo));
    }

    @Test
    public void testHoursAgo() {
        long now = System.currentTimeMillis();
        long twoHoursAgo = now - TimeUnit.HOURS.toMillis(2);
        assertEquals("2h ago", TimeUtils.getTimeAgo(twoHoursAgo));
    }

    @Test
    public void testYesterday() {
        long now = System.currentTimeMillis();
        long yesterday = now - TimeUnit.DAYS.toMillis(1);
        assertEquals("Yesterday", TimeUtils.getTimeAgo(yesterday));
    }

    @Test
    public void testDaysAgo() {
        long now = System.currentTimeMillis();
        long threeDaysAgo = now - TimeUnit.DAYS.toMillis(3);
        assertEquals("3 days ago", TimeUtils.getTimeAgo(threeDaysAgo));
    }

    @Test
    public void testGreetingNotNull() {
        assertTrue(TimeUtils.getGreeting().length() > 0);
    }
}
