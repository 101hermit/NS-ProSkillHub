package org.skillhub.model;

import java.time.LocalDateTime;

/** 在线文档页（表 {@code doc_page}）。三系统的站点指南都由这张表驱动。 */
public class DocPage {

    public Long id;
    public String docKey;
    public String title;
    public String section;
    public String summary;
    public String body;
    public Integer sortNo;
    public LocalDateTime updatedAt;
}
