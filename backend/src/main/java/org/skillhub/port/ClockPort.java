package org.skillhub.port;

import java.time.LocalDateTime;

/**
 * 时钟端口：业务层不直接调用 {@code LocalDateTime.now()}，便于时间相关断言。
 */
public interface ClockPort {

    LocalDateTime now();
}
