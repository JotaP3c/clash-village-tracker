package com.clashtracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TimeValues {
    public static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400L);

    private TimeValues() {
    }

    public static Long daysToSeconds(BigDecimal days) {
        if (days == null) {
            return null;
        }
        return days.multiply(SECONDS_PER_DAY).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long applyBoost(long baseSeconds, BigDecimal boost) {
        return BigDecimal.valueOf(baseSeconds)
                .multiply(BigDecimal.ONE.subtract(boost))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static String formatDuration(Long seconds) {
        if (seconds == null) {
            return "A VALIDAR";
        }
        long value = seconds;
        long days = value / 86_400L;
        long hours = (value % 86_400L) / 3_600L;
        long minutes = (value % 3_600L) / 60L;
        return "%dd %02dh %02dmin".formatted(days, hours, minutes);
    }
}
