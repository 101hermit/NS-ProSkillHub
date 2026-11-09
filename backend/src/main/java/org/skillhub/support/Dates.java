package org.skillhub.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 时间格式化（全站统一 {@code yyyy-MM-dd HH:mm:ss}）。 */
public final class Dates {

    public static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Dates() {
    }

    public static String format(LocalDateTime time) {
        return time == null ? "" : FULL.format(time);
    }
}
