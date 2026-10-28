package io.github.xiaowuiot.jt.server.system;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.Mask;
import io.github.xiaowuiot.jt.server.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 系统管理：用户（可以限定负责的车队）、角色（权限点自由组合）。
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    public SystemController(UserRepository users, RoleRepository roles, PasswordEncoder encoder, CurrentUser current) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.current = current;
    }

    // ===================== 用户 =====================

    public record UserRequest(
            @NotBlank(message = "请输入用户名") @Pattern(regexp = "[A-Za-z0-9_]{3,20}", message = "用户名为 3–20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入姓名") String name,
            @Pattern(regexp = "^$|1\\d{10}|1\\d{2}\\*{4}\\d{4}", message = "手机号格式不正确") String phone,
            String password, Boolean enabled, List<Long> roleIds, List<Long> fleetIds) {
    }

    /** 选人用的简单列表 */
    public record UserOption(Long id, String name, List<String> roleNames, List<String> permissions) {
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    public List<UserView> users() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    @GetMapping("/users/options")
    public List<UserOption> userOptions() {
        current.get();
        return users.findByEnabledTrueOrderById().stream().map(u -> new UserOption(u.getId(), u.getName(),
                u.getRoles().stream().map(Role::getName).toList(), u.permissions().stream().map(Enum::name).toList())).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public UserView createUser(@Valid @RequestBody UserRequest body) {
        if (users.existsByUsername(body.username())) {
            throw ApiException.badRequest("用户名已存在");
        }
        if (body.password() == null || body.password().length() < 6) {
            throw ApiException.badRequest("密码至少 6 位");
        }
        User u = new User(body.username(), encoder.encode(body.password()), body.name().strip());
        apply(u, body);
        return UserView.of(users.save(u));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest body) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (!u.getUsername().equals(body.username()) && users.existsByUsername(body.username())) {
            throw ApiException.badRequest("用户名已存在");
        }
        if (u.getId().equals(current.id()) && Boolean.FALSE.equals(body.enabled())) {
            throw ApiException.badRequest("不能停用自己");
        }
        u.setUsername(body.username());
        u.setName(body.name().strip());
        if (body.password() != null && !body.password().isBlank()) {
            if (body.password().length() < 6) {
                throw ApiException.badRequest("密码至少 6 位");
            }
            u.setPasswordHash(encoder.encode(body.password()));
        }
        apply(u, body);
        return UserView.of(users.save(u));
    }

    private void apply(User u, UserRequest body) {
        // 回传的还是脱敏号码，说明没改
        if (!Mask.isMasked(body.phone())) {
            u.setPhone(body.phone() == null || body.phone().isBlank() ? null : body.phone());
        }
        if (body.enabled() != null) {
            u.setEnabled(body.enabled());
        }
        u.setRoles(new HashSet<>(roles.findAllById(body.roleIds() == null ? List.of() : body.roleIds())));
        u.setFleetIds(new HashSet<>(body.fleetIds() == null ? List.of() : body.fleetIds()));
    }

    // ===================== 角色 =====================

    public record RoleView(Long id, String name, String remark, boolean builtin, Set<Permission> permissions) {
        static RoleView of(Role r) {
            return new RoleView(r.getId(), r.getName(), r.getRemark(), r.isBuiltin(), r.getPermissions());
        }
    }

    public record RoleRequest(@NotBlank(message = "请输入角色名称") String name, String remark, Set<Permission> permissions) {
    }

    public record PermissionView(String code, String label) {
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        current.get();
        return roles.findAll().stream().map(RoleView::of).toList();
    }

    @GetMapping("/roles/permissions")
    public List<PermissionView> permissions() {
        return Arrays.stream(Permission.values()).map(p -> new PermissionView(p.name(), p.label())).toList();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public RoleView createRole(@Valid @RequestBody RoleRequest body) {
        if (roles.findByName(body.name().strip()).isPresent()) {
            throw ApiException.badRequest("角色名称已存在");
        }
        return RoleView.of(roles.save(new Role(body.name().strip(), body.remark(), false,
                body.permissions() == null ? Set.of() : body.permissions())));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public RoleView updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest body) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        Set<Permission> perms = body.permissions() == null ? Set.of() : body.permissions();
        if (r.getPermissions().contains(Permission.SYSTEM_ADMIN) && !perms.contains(Permission.SYSTEM_ADMIN)
                && current.get().getRoles().stream().anyMatch(x -> x.getId().equals(id))) {
            throw ApiException.badRequest("不能去掉自己所在角色的系统管理权限，否则就没人能管理系统了");
        }
        r.setName(body.name().strip());
        r.setRemark(body.remark());
        r.setPermissions(perms);
        return RoleView.of(roles.save(r));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
    @Transactional
    public Map<String, Boolean> deleteRole(@PathVariable Long id) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (r.isBuiltin()) {
            throw ApiException.badRequest("内置角色不能删除");
        }
        boolean used = users.findAll().stream().anyMatch(u -> u.getRoles().stream().anyMatch(x -> x.getId().equals(id)));
        if (used) {
            throw ApiException.badRequest("还有用户属于这个角色，请先调整这些用户的角色");
        }
        roles.delete(r);
        return Map.of("ok", true);
    }
}
