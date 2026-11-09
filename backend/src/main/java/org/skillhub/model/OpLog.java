package org.skillhub.model;

import java.time.LocalDateTime;

/** 操作审计流水（表 {@code op_log}）。 */
public class OpLog {

    public Long id;
    public String actor;
    public String role;
    public String action;
    public String targetType;
    public String targetId;
    public String detail;
    public String traceId;
    public LocalDateTime createdAt;
}
