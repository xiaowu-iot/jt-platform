package io.github.xiaowuiot.jt.server.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.xiaowuiot.jt.server.system.UserRepository;

/**
 * 每个接口请求都按数据库里的最新角色重新计算权限：
 * 后台给用户改了角色、或者停用了账号，不用等用户重新登录就立刻生效。
 */
public class RefreshAuthoritiesFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public RefreshAuthoritiesFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser current) {
            users.findById(current.getId()).filter(u -> u.isEnabled()).ifPresentOrElse(u -> {
                AuthUser fresh = new AuthUser(u);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities()));
            }, () -> {
                // 账号被删或被停用：当作未登录处理
                SecurityContextHolder.clearContext();
                if (req.getSession(false) != null) {
                    req.getSession(false).invalidate();
                }
            });
        }
        chain.doFilter(req, resp);
    }
}
