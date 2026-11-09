package org.skillhub.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 库读取端口：把"库在哪儿、长什么样"与业务隔开。
 *
 * <p>当前实现是 {@code FileLibraryAdapter}（读工程根目录下的 {@code skills/}）。
 * 未来要换成"从远端 Registry 拉取"时，只需新写一个实现。
 */
public interface LibraryPort {

    /** 库是否可用（目录与 catalog.json 都存在）。 */
    boolean available();

    /** 库版本（来自 {@code catalog.json.version}）。 */
    String libraryVersion();

    /** 库根目录，用于日志与排障。 */
    Path root();

    /** 全部条目（只含元数据，不含正文）。 */
    List<Entry> catalog();

    /** 读取单个 Skill 的正文与文件清单；不存在时返回 {@code null}。 */
    SkillContent read(String slug);

    /** 读取 Skill 目录下的单个文件（用于打包下载）；不存在时返回 {@code null}。 */
    byte[] readFile(String slug, String relativePath);

    /**
     * 一个库条目（来自 {@code catalog.json}）。
     *
     * @param slug        目录名 / 唯一键
     * @param name        frontmatter 里的 name
     * @param description 一句话描述
     * @param category    分类 code（15 类之一）
     * @param scene       场景 code
     * @param version     版本号
     * @param license     许可
     * @param source      来源（library/github/gitee/gitlab）
     * @param sourceUrl   上游原文地址
     * @param sourceRepo  上游仓库 owner/repo
     * @param author      作者/组织
     * @param authorKind  personal / department
     * @param tags        标签串（逗号分隔）
     * @param fileCount   文件数
     * @param bytes       总字节
     */
    record Entry(String slug, String name, String description, String category, String scene,
                 String version, String license, String source, String sourceUrl, String sourceRepo,
                 String author, String authorKind, String tags, int fileCount, long bytes) {
    }

    /** 单个 Skill 的正文与文件清单。 */
    record SkillContent(String slug, String content, int contentBytes, String contentSha256,
                        List<FileRef> files) {
    }

    /** 附件引用。 */
    record FileRef(String path, long bytes, String sha256) {
    }
}
