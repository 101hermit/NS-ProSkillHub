package org.skillhub.model;

import java.time.LocalDateTime;

/**
 * Skill 主表实体（表 {@code skill}）。
 *
 * <p>注意：{@code content} 是最大的列（单条最大约 40 KB）。列表查询**不得**带上它
 * （见 {@link org.skillhub.port.SkillRepository} 中 {@code findCard*} 与 {@code findDetail*} 的区分）。
 */
public class Skill {

    public Long id;
    public String skillId;
    public String slug;
    public String name;
    public String title;
    public String description;
    public String categoryCode;
    public String sceneCode;
    public String tags;
    public String version;
    public String source;
    public String sourceUrl;
    public String sourceRepo;
    public String license;
    public String author;
    public String authorKind;
    public Integer riskScore;
    public String riskLevel;
    public String status;
    public String content;
    public Integer contentBytes;
    public Integer fileCount;
    public Long packageBytes;
    public String contentSha256;
    public Integer downloadCount;
    public Integer installCount;
    public Integer starCount;
    public Integer evalCount;
    public java.math.BigDecimal avgScore;
    public String skillsetCode;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
