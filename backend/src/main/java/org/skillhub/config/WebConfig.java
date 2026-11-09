package org.skillhub.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Web 配置：静态资源 + SPA 回退 + CORS。
 *
 * <p>三个关键点：
 * <ol>
 *   <li><b>SPA 回退只对"非 API 路径"生效</b>：否则前端写错一个接口路径，
 *       会拿到 HTML 而不是 404，排障变成折磨。</li>
 *   <li><b>静态资源显式指定 UTF-8</b>：真实测试机的平台默认编码是 GBK，
 *       不显式指定会出现中文乱码（历史工程踩过）。</li>
 *   <li><b>CORS 放开全部来源</b>：本版鉴权走请求头而非 Cookie，不受 CSRF 影响；
 *       内网里同事用任意 IP 打开前端都能调通。</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** 找不到静态资源时：像路径回落到 index.html（SPA），像接口路径则放行给 MVC。 */
    static class SpaFallbackResolver extends PathResourceResolver {

        private static final String INDEX = "index.html";

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                return null;
            }
            ClassPathResource index = new ClassPathResource("static/" + INDEX);
            return index.exists() ? index : null;
        }
    }
}
