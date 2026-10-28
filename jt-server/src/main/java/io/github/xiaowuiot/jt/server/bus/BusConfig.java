package io.github.xiaowuiot.jt.server.bus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 单机模式的总线。集群模式会提供 Kafka 实现，这里的 Bean 就不再创建。
 */
@Configuration
public class BusConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(UplinkBus.class)
    public LocalUplinkBus uplinkBus(@Value("${jt.bus.capacity:100000}") int capacity) {
        return new LocalUplinkBus(capacity);
    }
}
