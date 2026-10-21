package io.github.xiaowuiot.jt.server.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * 角色。配置类上加 {@code @Roles.On(Roles.ACCESS)}，只有 {@code jt.roles} 里包含 access 时才会加载。
 * <pre>
 * jt:
 *   roles: [access]          # 只做接入
 *   roles: [access, platform] # 单体模式（默认）
 * </pre>
 */
public final class Roles {

    public static final String ACCESS = "access";
    public static final String PLATFORM = "platform";
    /**
     * 没有配置时启用全部角色（单体模式）
     */
    public static final List<String> ALL = List.of(ACCESS, PLATFORM);

    private Roles() {
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Conditional(OnRoleCondition.class)
    public @interface On {
        String value();
    }

    static class OnRoleCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attrs = metadata.getAnnotationAttributes(On.class.getName());
            String role = (String) attrs.get("value");
            List<String> roles = Binder.get(context.getEnvironment())
                    .bind("jt.roles", Bindable.listOf(String.class))
                    .orElse(ALL);
            return roles.contains(role);
        }
    }
}
