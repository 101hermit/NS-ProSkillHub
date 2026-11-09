/**
 * 数据传输对象：动作层与业务层之间、以及返回给前端的形状。
 *
 * <p>这里用**嵌套 record** 而不是一表一文件的 class：record 天然不可变、字段即契约，
 * 把同一域的几个形状放在一起更容易一眼看清"这个模块对外长什么样"。
 * 字段名由 Jackson 统一转成 snake_case（见 {@code JacksonConfig}），
 * 因此 Java 侧写 camelCase、JSON 侧得到 snake_case。
 */
package org.skillhub.dto;
