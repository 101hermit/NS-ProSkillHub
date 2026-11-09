/**
 * 数据库实体（与 {@code db/schema.sql} 的表一一对应）。
 *
 * <p><b>为什么用 public 字段而不是 getter/setter</b>：这些类是纯粹的"行数据搬运器"，
 * 没有不变量、没有行为。MyBatis 的 field access 与 Jackson 的 field 序列化都直接支持
 * public 字段，因此每张表可以少写 40 行样板代码，而可读性更好。
 * <b>需要校验或行为的类请放 {@code dto}</b>。
 */
package org.skillhub.model;
