/**
 * MyBatis Mapper 接口（数据持久层的 SQL 入口）。
 *
 * <p>约束（docs/01 §2.4）：
 * <ul>
 *   <li>SQL 必须同时能在 H2(MODE=MySQL) 与 MySQL 8 上执行 —— 见 docs/02 §1 的可移植子集</li>
 *   <li>不做业务判断，只做数据搬运与聚合</li>
 *   <li>列表查询禁止 {@code SELECT *}，尤其是不得在列表里查 {@code skill.content}</li>
 * </ul>
 */
package org.skillhub.mapper;
