package io.github.xiaowuiot.jt.server.system;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import io.github.xiaowuiot.jt.server.common.Mask;

/**
 * 返回给前端的用户信息，不含密码；手机号脱敏，看完整号码走验证码。
 */
public record UserView(Long id, String username, String name, String phone, boolean enabled,
                       List<Long> roleIds, List<String> roleNames, Set<Long> fleetIds,
                       List<String> permissions, LocalDateTime createdAt) {

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getName(), Mask.phone(u.getPhone()), u.isEnabled(),
                u.getRoles().stream().map(Role::getId).sorted().toList(),
                u.getRoles().stream().map(Role::getName).sorted().toList(),
                u.getFleetIds(),
                u.permissions().stream().map(Enum::name).toList(),
                u.getCreatedAt());
    }
}
