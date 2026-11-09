package org.skillhub.model;

import java.time.LocalDateTime;

/** 贡献者档案（表 {@code contributor}）。{@code kind} 区分 {@code personal} / {@code department}。 */
public class Contributor {

    public Long id;
    public String source;
    public String author;
    public String name;
    public String kind;
    public String platform;
    public String description;
    public String gitProfile;
    public LocalDateTime createdAt;
}
