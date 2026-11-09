package org.skillhub.model;

import java.time.LocalDateTime;

/** Skill 附件（表 {@code skill_file}）：一个 Skill 目录下的所有文件。 */
public class SkillFile {

    public Long id;
    public String skillId;
    public String path;
    public Long bytes;
    public String sha256;
    public Boolean isPrimary;
    public LocalDateTime createdAt;
}
