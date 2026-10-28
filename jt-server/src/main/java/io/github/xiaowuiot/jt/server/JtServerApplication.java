package io.github.xiaowuiot.jt.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 服务端入口。一个 jar，按 {@code jt.roles} 启用不同的角色：
 * <pre>
 * access    808 终端接入
 * platform  Web 平台（M2）
 * </pre>
 * 默认全部启用（单体模式）；集群部署时同一个 jar 起多份，每份只开一两个角色。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JtServerApplication {

    public static void main(String[] args) {
        // 808 的时间都是北京时间，平台统一按北京时间存储和显示，不受服务器所在时区影响
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(JtServerApplication.class, args);
    }
}
