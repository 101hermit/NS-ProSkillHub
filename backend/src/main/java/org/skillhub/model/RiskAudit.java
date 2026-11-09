package org.skillhub.model;

import java.time.LocalDateTime;

/** 一次风险评估的结论（表 {@code risk_audit}）。 */
public class RiskAudit {

    public Long id;
    public String skillId;
    public Integer riskScore;
    public String riskLevel;
    public String engine;
    public String dimensionJson;
    public Integer signalTotal;
    public String summary;
    public String conclusion;
    public LocalDateTime createdAt;
}
