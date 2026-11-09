package org.skillhub.port;

/**
 * 标识符端口：业务层不直接调用 {@code UUID.randomUUID()}，便于测试中固定 ID。
 */
public interface IdPort {

    /** 短 ID（8 位十六进制），用于追踪号、运行编号等。 */
    String shortId();

    /** 带前缀的编号，例如 {@code run-3f9c1a2b}。 */
    String code(String prefix);
}
