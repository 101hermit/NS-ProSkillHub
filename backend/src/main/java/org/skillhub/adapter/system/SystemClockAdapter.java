package org.skillhub.adapter.system;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.skillhub.port.ClockPort;
import org.springframework.stereotype.Component;

/** 系统时钟实现。时区固定 Asia/Shanghai —— 真实测试机在国内内网，避免跨时区歧义。 */
@Component
public class SystemClockAdapter implements ClockPort {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
