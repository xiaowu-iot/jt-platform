package io.github.xiaowuiot.jt.server.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.system.User;
import io.github.xiaowuiot.jt.server.system.UserRepository;

/**
 * 取当前登录用户。每次都从数据库读一遍，这样禁用账号、改角色能立刻生效。
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return u.getId();
    }

    public User get() {
        User user = users.findById(id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"));
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DISABLED", "账号已被停用，请联系系统管理员");
        }
        return user;
    }

    /**
     * 当前用户的姓名，没填姓名用登录名，记操作人用
     */
    public String name() {
        User u = get();
        return u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName();
    }
}
