package org.skillhub.adapter.system;

import java.util.UUID;
import org.skillhub.port.IdPort;
import org.springframework.stereotype.Component;

/** 基于 UUID 的短标识实现。 */
@Component
public class ShortIdAdapter implements IdPort {

    @Override
    public String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Override
    public String code(String prefix) {
        return (prefix == null || prefix.isBlank() ? "" : prefix + "-") + shortId();
    }
}
