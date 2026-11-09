package org.skillhub.port;

import java.util.List;

/**
 * 打包端口：把一个 Skill 做成可下载的归档。
 *
 * <p>当前只有 ZIP（跨平台最稳）。将来若要出 {@code .skhub}（npm 风格）或 OCI 层，
 * 在这里加方法并新增实现即可，业务层不用改。
 */
public interface PackagePort {

    /**
     * 打包。
     *
     * @param entryName 归档内的一级目录名（通常是 Skill 的 slug）
     * @param files     文件清单（path 为相对路径）
     * @return ZIP 字节流
     */
    byte[] zip(String entryName, List<FileContent> files);

    /** 待打包的文件内容。 */
    record FileContent(String path, byte[] data) {
    }
}
