package io.github.xiaowuiot.jt.server.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.fleet.Fleet;
import io.github.xiaowuiot.jt.server.fleet.FleetRepository;
import io.github.xiaowuiot.jt.server.notify.MockDingTalkController;
import io.github.xiaowuiot.jt.server.notify.NotifyChannel;
import io.github.xiaowuiot.jt.server.notify.NotifyChannelRepository;
import io.github.xiaowuiot.jt.server.system.Permission;
import io.github.xiaowuiot.jt.server.system.Role;
import io.github.xiaowuiot.jt.server.system.RoleRepository;
import io.github.xiaowuiot.jt.server.system.User;
import io.github.xiaowuiot.jt.server.system.UserRepository;
import io.github.xiaowuiot.jt.server.vehicle.Vehicle;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleRepository;
import io.github.xiaowuiot.jt.server.vehicle.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首次启动的初始化：内置角色和管理员账号；演示模式下再建车队、车辆、演示账号和通知渠道。
 */
@Component
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String[] SURNAMES = {"张", "王", "李", "刘", "陈", "杨", "黄", "赵", "吴", "周", "徐", "孙", "马", "朱", "胡", "郭"};
    private static final String[] GIVEN = {"伟", "强", "磊", "军", "勇", "杰", "涛", "斌", "超", "明", "刚", "平", "辉", "鹏", "华", "飞"};

    private final RoleRepository roles;
    private final UserRepository users;
    private final FleetRepository fleets;
    private final VehicleRepository vehicles;
    private final VehicleCache cache;
    private final NotifyChannelRepository channels;
    private final PasswordEncoder encoder;
    private final JtProperties props;
    private final ObjectMapper json;
    private final DemoHistory history;
    private final int serverPort;
    private final String contextPath;

    public DataInitializer(RoleRepository roles, UserRepository users, FleetRepository fleets, VehicleRepository vehicles,
                           VehicleCache cache, NotifyChannelRepository channels, PasswordEncoder encoder,
                           JtProperties props, ObjectMapper json, DemoHistory history,
                           @Value("${server.port:8080}") int serverPort,
                           @Value("${server.servlet.context-path:}") String contextPath) {
        this.roles = roles;
        this.users = users;
        this.fleets = fleets;
        this.vehicles = vehicles;
        this.cache = cache;
        this.channels = channels;
        this.encoder = encoder;
        this.props = props;
        this.json = json;
        this.history = history;
        this.serverPort = serverPort;
        this.contextPath = contextPath;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role admin = role("系统管理员", "全部权限", EnumSet.allOf(Permission.class));
        Role monitor = role("监控员", "看车、看轨迹、处理报警", EnumSet.of(Permission.VEHICLE_VIEW, Permission.ALARM_VIEW,
                Permission.ALARM_HANDLE));
        Role dispatch = role("调度员", "看车、下发指令、处理报警", EnumSet.of(Permission.VEHICLE_VIEW,
                Permission.COMMAND_SEND, Permission.ALARM_VIEW, Permission.ALARM_HANDLE, Permission.RAW_VIEW));
        Role manager = role("管理人员", "看车、看报警", EnumSet.of(Permission.VEHICLE_VIEW, Permission.ALARM_VIEW));
        // 后来加的权限点：系统管理员始终是全部权限
        grant(admin, EnumSet.allOf(Permission.class));
        if (!users.existsByUsername("admin")) {
            User u = new User("admin", encoder.encode(props.getAdminPassword()), "系统管理员");
            u.setRoles(Set.of(admin));
            users.save(u);
            log.info("已创建管理员账号 admin");
        }
        if (props.getDemo().isEnabled() && vehicles.count() == 0) {
            demo(monitor, dispatch, manager);
        }
    }

    private Role role(String name, String remark, Set<Permission> perms) {
        return roles.findByName(name).orElseGet(() -> roles.save(new Role(name, remark, true, perms)));
    }

    private void grant(Role role, Set<Permission> perms) {
        if (!role.getPermissions().containsAll(perms)) {
            Set<Permission> p = EnumSet.noneOf(Permission.class);
            p.addAll(role.getPermissions());
            p.addAll(perms);
            role.setPermissions(p);
            roles.save(role);
        }
    }

    private void demo(Role monitor, Role dispatch, Role manager) {
        Fleet env = fleets.save(new Fleet("鹏城环境集团", null, 1));
        Fleet[] envTeams = {
                fleets.save(new Fleet("福田环卫一队", env.getId(), 1)),
                fleets.save(new Fleet("南山环卫二队", env.getId(), 2)),
                fleets.save(new Fleet("罗湖环卫三队", env.getId(), 3)),
                fleets.save(new Fleet("宝安龙华环卫队", env.getId(), 4))};
        Fleet muck = fleets.save(new Fleet("城建渣土运输公司", null, 2));
        Fleet[] muckTeams = {
                fleets.save(new Fleet("渣土一车队", muck.getId(), 1)),
                fleets.save(new Fleet("渣土二车队", muck.getId(), 2))};
        Fleet logistics = fleets.save(new Fleet("鹏程物流", null, 3));
        Fleet cold = fleets.save(new Fleet("冷链车队", logistics.getId(), 1));
        Fleet trunk = fleets.save(new Fleet("干线车队", logistics.getId(), 2));

        Map<String, DemoRoutes.Route> routes = DemoRoutes.load(json);
        // 路线 → 车队：环卫按片区，渣土两个车队轮流分
        Map<String, Fleet> routeFleet = Map.ofEntries(
                Map.entry("福田中心区清扫线", envTeams[0]), Map.entry("福田南片收运线", envTeams[0]),
                Map.entry("南山蛇口清扫线", envTeams[1]), Map.entry("罗湖东门收运线", envTeams[2]),
                Map.entry("宝安中心区清扫线", envTeams[3]), Map.entry("龙华民治收运线", envTeams[3]),
                Map.entry("前海工地-宝安消纳场", muckTeams[0]), Map.entry("科技园工地-龙华消纳场", muckTeams[0]),
                Map.entry("福田工地-布吉消纳场", muckTeams[1]), Map.entry("盐田港工地-沙头角", muckTeams[1]),
                Map.entry("机场物流园-盐田港", trunk), Map.entry("南山冷库-龙岗配送", cold));

        int seq = 0;
        List<Vehicle> created = new ArrayList<>();
        for (DemoRoutes.Route r : routes.values()) {
            VehicleType type = VehicleType.valueOf(r.type());
            int perRoute = 4;
            for (int i = 0; i < perRoute; i++) {
                seq++;
                String plate = plate(type, seq);
                String device = String.format("1380%07d", seq);
                Vehicle v = new Vehicle(plate, type, device);
                v.setPlateColor(type == VehicleType.SANITATION || type == VehicleType.COLD_CHAIN ? 5 : 2);
                v.setFleetId(routeFleet.getOrDefault(r.name(), env).getId());
                v.setDemoRoute(r.name() + "#" + i);
                v.setDriverName(SURNAMES[seq % SURNAMES.length] + GIVEN[(seq * 7) % GIVEN.length]);
                v.setDriverPhone(String.format("139%08d", 10000000 + seq * 7919 % 90000000));
                v.setSimNo(String.format("898604%014d", 20000000L + seq));
                v.setVin(String.format("LZZ%sDEMO%06d", type == VehicleType.MUCK ? "5" : "1", seq));
                // 演示的终端用三个版本混接：大部分 2013，五分之一 2019，十分之一 2011
                v.setProtocolVersion(seq % 10 == 0 ? 2011 : seq % 5 == 0 ? 2019 : 2013);
                v.setRemark(r.name());
                v = vehicles.save(v);
                cache.put(v);
                created.add(v);
            }
        }

        history.generate(created, routes);

        user("monitor", "李监控", monitor, List.of());
        user("dispatch", "王调度", dispatch, List.of());
        user("sanitation", "陈经理", monitor, List.of(env.getId()));
        user("manager", "赵总", manager, List.of());

        String mock = "http://127.0.0.1:" + serverPort + contextPath + "/mock/dingtalk/robot/send";
        channels.save(new NotifyChannel("调度中心钉钉群（演示）", NotifyChannel.Type.DINGTALK, mock,
                MockDingTalkController.SECRET, 2));
        log.info("已生成演示数据：{} 个车队，{} 辆车", fleets.count(), seq);
    }

    private void user(String username, String name, Role role, List<Long> fleetIds) {
        User u = new User(username, encoder.encode(props.getDemo().getPassword()), name);
        u.setRoles(Set.of(role));
        u.setFleetIds(Set.copyOf(fleetIds));
        users.save(u);
    }

    /**
     * 演示车牌：环卫车、冷链车用新能源绿牌（字母 D 开头的 6 位），其他用黄牌
     */
    private static String plate(VehicleType type, int seq) {
        return switch (type) {
            case SANITATION -> String.format("粤BD%05d", 10000 + seq * 37 % 90000);
            case COLD_CHAIN -> String.format("粤BF%05d", 20000 + seq * 53 % 70000);
            case MUCK -> String.format("粤B%s%04d", (char) ('A' + seq % 6), 1000 + seq * 97 % 9000);
            default -> String.format("粤B%s%04d", (char) ('G' + seq % 5), 2000 + seq * 131 % 8000);
        };
    }
}
