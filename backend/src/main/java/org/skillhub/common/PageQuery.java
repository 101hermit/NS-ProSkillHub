package org.skillhub.common;

/**
 * 分页入参基类：{@code page} 从 1 开始，{@code size} 默认 20、上限 100。
 *
 * <p>越界不抛异常，而是**收敛到合法区间**（用户手改 URL 不该看到报错页）。
 */
public class PageQuery {

    private static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /** 收敛后的页码（>=1）。 */
    public int safePage() {
        return Math.max(1, page);
    }

    /** 收敛后的每页条数（1..100）。 */
    public int safeSize() {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** SQL OFFSET（Java 侧用）。 */
    public int offset() {
        return (safePage() - 1) * safeSize();
    }

    /** SQL OFFSET（供 MyBatis {@code #{offset}} 取值，OGNL 需要 getter）。 */
    public int getOffset() {
        return offset();
    }

    /** SQL LIMIT（供 MyBatis {@code #{limit}} 取值）。 */
    public int getLimit() {
        return safeSize();
    }
}
