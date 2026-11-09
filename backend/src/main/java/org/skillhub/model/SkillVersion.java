package org.skillhub.model;

import java.time.LocalDateTime;

/** 版本快照（表 {@code skill_version}）：每个版本的 SKILL.md 正文。 */
public class SkillVersion {

    public Long id;
    public String skillId;
    public String version;
    public String content;
    public String notes;
    public Integer sizeBytes;
    public String origin;
    public LocalDateTime createdAt;
}
