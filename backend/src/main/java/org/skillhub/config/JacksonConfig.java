package org.skillhub.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置：对外字段一律 snake_case，时间格式统一 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * <p>为什么在 Jackson 层做命名转换而不是手写字段名：
 * Java 侧可以保持 camelCase（IDE 友好、可重构），JSON 侧得到 snake_case（契约稳定）。
 * 这是一处"两边都要好看"的取舍点，集中在这里配置一次即可。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snakeCaseCustomizer() {
        return builder -> {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            builder.modules(new JavaTimeModule());
            builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss");
        };
    }
}
