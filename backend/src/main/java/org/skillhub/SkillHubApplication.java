package org.skillhub;

import org.skillhub.config.SiteProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EasySkillHub 1.0（ESKH）启动类。
 *
 * <p>资源站模式：Skill 的浏览、编辑、贡献、分发、静态风险评估。
 * <b>不发起任何外部网络调用</b>，断网可用。
 */
@SpringBootApplication
@EnableConfigurationProperties(SiteProperties.class)
public class SkillHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
