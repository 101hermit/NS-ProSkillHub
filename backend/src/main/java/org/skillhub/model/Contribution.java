package org.skillhub.model;

import java.time.LocalDateTime;

/**
 * 在线贡献记录（表 {@code contribution}）。
 *
 * <p>状态机（见 docs/ESKH 业务规则）：{@code draft → submitted → published}，
 * 任一态可 {@code rejected}。签 CLA 只置 {@code cla_signed}，不推进状态。
 */
public class Contribution {

    public Long id;
    public String kind;
    public String contributorName;
    public String name;
    public String title;
    public String version;
    public String description;
    public String sourceType;
    public String sourceUrl;
    public String storageDir;
    public String status;
    public Boolean claSigned;
    public String claSignature;
    public LocalDateTime claSignedAt;
    public String skillId;
    public Integer riskScore;
    public String riskLevel;
    public String notes;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
