package org.skillhub.model;

/** 命中的风险信号明细（表 {@code risk_signal}）。 */
public class RiskSignal {

    public Long id;
    public Long auditId;
    public String signalCode;
    public String indicator;
    public String dimension;
    public String severity;
    public Integer weight;
    public String filePath;
    public Integer lineNo;
    public String remediation;
}
