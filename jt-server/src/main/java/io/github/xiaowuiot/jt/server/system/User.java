package io.github.xiaowuiot.jt.server.system;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String name;

    /** 钉钉通知 @ 报警处理人时用 */
    @Column(length = 20)
    private String phone;

    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "sys_user_role", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    /**
     * 负责的车队。为空表示不限车队，能看到全部车辆
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_user_fleet", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "fleet_id")
    private Set<Long> fleetIds = new LinkedHashSet<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    protected User() {
    }

    public User(String username, String passwordHash, String name) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public Set<Permission> permissions() {
        Set<Permission> all = EnumSet.noneOf(Permission.class);
        roles.forEach(r -> all.addAll(r.getPermissions()));
        return all;
    }

    public boolean has(Permission p) {
        return roles.stream().anyMatch(r -> r.getPermissions().contains(p));
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = new LinkedHashSet<>(roles);
    }

    public Set<Long> getFleetIds() {
        return fleetIds;
    }

    public void setFleetIds(Set<Long> fleetIds) {
        this.fleetIds = new LinkedHashSet<>(fleetIds);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
