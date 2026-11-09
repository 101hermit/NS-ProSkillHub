/**
 * 服务接口层的适配器实现（按"被适配的外部世界"分子包）。
 *
 * <p>{@code persistence} 子包放 MyBatis Mapper（见 {@code org.skillhub.mapper}）；
 * 其余子包放文件系统、打包、系统能力、外部服务等适配器。
 * 适配器负责协议转换、重试、超时与错误翻译（把技术异常翻成 {@code ApiException}）。
 */
package org.skillhub.adapter;
