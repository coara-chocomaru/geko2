package com.coara.browser2.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DayLabeler {
    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年M月d日 (E)", Locale.JAPAN);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("H:mm", Locale.JAPAN);
    private final Date scratch = new Date();
    private int todayKey;
    private int yesterdayKey;

    public DayLabeler() {
        reset();
    }

    public void reset() {
        calendar.setTimeInMillis(System.currentTimeMillis());
        todayKey = keyOfCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        yesterdayKey = keyOfCalendar();
    }

    private int keyOfCalendar() {
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
    }

    public int key(long timestamp) {
        if (timestamp <= 0L) {
            return 0;
        }
        calendar.setTimeInMillis(timestamp);
        return keyOfCalendar();
    }

    public String label(long timestamp) {
        if (timestamp <= 0L) {
            return "日付不明";
        }
        int key = key(timestamp);
        scratch.setTime(timestamp);
        String date = dateFormat.format(scratch);
        if (key == todayKey) {
            return "今日 - " + date;
        }
        if (key == yesterdayKey) {
            return "昨日 - " + date;
        }
        return date;
    }

    public String time(long timestamp) {
        if (timestamp <= 0L) {
            return "";
        }
        scratch.setTime(timestamp);
        return timeFormat.format(scratch);
    }

    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            String host = android.net.Uri.parse(url).getHost();
            if (host == null || host.isEmpty()) {
                return url;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }
}
