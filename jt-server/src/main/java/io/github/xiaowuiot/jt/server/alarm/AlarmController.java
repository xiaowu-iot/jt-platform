package io.github.xiaowuiot.jt.server.alarm;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.SimpleBodies;
import io.github.xiaowuiot.jt.server.command.CommandService;
import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.security.CurrentUser;
import io.github.xiaowuiot.jt.server.security.DataScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报警查询和处理。
 * <pre>
 * GET  /api/alarms?status=PENDING&level=3&type=T0&vehicleId=&from=&to=   分页
 * GET  /api/alarms/latest?afterId=123                                    比某条新的报警，页面轮询弹提醒用
 * POST /api/alarms/12/handle   {"status": "HANDLED", "remark": "已电话联系司机", "confirm": true}
 * POST /api/alarms/handle-batch {"ids": [1, 2], "status": "HANDLED", "remark": "..."}
 * </pre>
 * 处理时勾选 confirm：给终端下发 0x8203 人工确认报警，紧急报警这类"要平台确认才清除"的报警位终端才会清零。
 */
@RestController
@RequestMapping("/api/alarms")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_ALARM_VIEW')")
public class AlarmController {

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final AlarmRepository repo;
    private final DataScope scope;
    private final CurrentUser current;
    private final CommandService commands;

    public AlarmController(AlarmRepository repo, DataScope scope, CurrentUser current, CommandService commands) {
        this.repo = repo;
        this.scope = scope;
        this.current = current;
        this.commands = commands;
    }

    public record AlarmView(Long id, Long vehicleId, String plate, String deviceId, Alarm.Source source, String type,
                            String typeName, int level, String levelName, LocalDateTime startTime, LocalDateTime endTime,
                            double lat, double lng, double speed, int count, String detail, Alarm.Status status,
                            String handlerName, String handleRemark, LocalDateTime handleTime, boolean confirmed,
                            boolean needConfirm, Map<String, Object> ai) {

        static AlarmView of(Alarm a) {
            return of(a, null);
        }

        static AlarmView of(Alarm a, Map<String, Object> ai) {
            double[] g = Geo.wgs84ToGcj02(a.getLat(), a.getLng());
            return new AlarmView(a.getId(), a.getVehicleId(), a.getPlate(), a.getDeviceId(), a.getSource(), a.getType(),
                    a.getTypeName(), a.getLevel(), AlarmTypes.levelName(a.getLevel()), a.getStartTime(), a.getEndTime(),
                    g[0], g[1], a.getSpeed(), a.getCount(), a.getDetail(), a.getStatus(), a.getHandlerName(),
                    a.getHandleRemark(), a.getHandleTime(), a.isConfirmed(), confirmBit(a) >= 0, ai);
        }
    }

    public record HandleForm(Alarm.Status status, String remark, Boolean confirm) {
    }

    public record BatchForm(List<Long> ids, Alarm.Status status, String remark) {
    }

    @GetMapping
    public PageResult<AlarmView> list(@RequestParam(required = false) Alarm.Status status,
                                      @RequestParam(required = false) Integer level,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) Long vehicleId,
                                      @RequestParam(required = false) String plate,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = PATTERN) LocalDateTime from,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = PATTERN) LocalDateTime to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Set<Long> visible = scope.fleetIds();
        Specification<Alarm> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (level != null) {
                ps.add(cb.equal(root.get("level"), level));
            }
            if (type != null && !type.isBlank()) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (vehicleId != null) {
                ps.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (plate != null && !plate.isBlank()) {
                ps.add(cb.like(cb.upper(root.get("plate")), "%" + plate.trim().toUpperCase() + "%"));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("startTime"), to));
            }
            if (visible != null) {
                ps.add(root.get("fleetId").in(visible));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var p = repo.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "startTime", "id")));
        return new PageResult<>(p.getContent().stream().map(AlarmView::of).toList(),
                p.getTotalElements(), p.getNumber(), p.getSize());
    }

    @GetMapping("/latest")
    public List<AlarmView> latest(@RequestParam(defaultValue = "0") long afterId) {
        Set<Long> visible = scope.fleetIds();
        List<Alarm> list = afterId <= 0 ? repo.findTop20ByOrderByIdDesc() : repo.findTop50ByIdGreaterThanOrderByIdDesc(afterId);
        return list.stream().filter(a -> visible == null || visible.contains(a.getFleetId())).map(AlarmView::of).toList();
    }

    @GetMapping("/types")
    public Map<String, String> types() {
        return AlarmTypes.all();
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('PERM_ALARM_HANDLE')")
    @Transactional
    public AlarmView handle(@PathVariable Long id, @RequestBody HandleForm f) {
        Alarm a = repo.findById(id).orElseThrow(() -> ApiException.notFound("报警不存在"));
        if (!scope.canSee(a.getFleetId())) {
            throw ApiException.forbidden("没有权限处理这条报警");
        }
        String name = current.get().getName();
        a.handle(f.status() == null ? Alarm.Status.HANDLED : f.status(), name, f.remark());
        int bit = confirmBit(a);
        if (Boolean.TRUE.equals(f.confirm()) && bit >= 0) {
            // 人工确认报警 0x8203：流水号填 0 表示确认这一类的所有报警
            commands.send(a.getDeviceId(), MessageId.ALARM_CONFIRM, new SimpleBodies.AlarmConfirm(0, 1L << bit), 10,
                    "确认报警：" + a.getTypeName(), name);
            a.setConfirmed(true);
        }
        return AlarmView.of(repo.save(a));
    }

    @PostMapping("/handle-batch")
    @PreAuthorize("hasAuthority('PERM_ALARM_HANDLE')")
    @Transactional
    public Map<String, Integer> handleBatch(@RequestBody BatchForm f) {
        String name = current.get().getName();
        int n = 0;
        for (Alarm a : repo.findAllById(f.ids())) {
            if (scope.canSee(a.getFleetId()) && a.getStatus() == Alarm.Status.PENDING) {
                a.handle(f.status() == null ? Alarm.Status.HANDLED : f.status(), name, f.remark());
                repo.save(a);
                n++;
            }
        }
        return Map.of("count", n);
    }

    /**
     * 需要平台确认才清除的终端报警位，返回位号；其他报警返回 -1
     */
    static int confirmBit(Alarm a) {
        if (a.getSource() != Alarm.Source.TERMINAL || !a.getType().startsWith("T")) {
            return -1;
        }
        int bit = Integer.parseInt(a.getType().substring(1));
        return (Jt808Flags.CONFIRM_REQUIRED >> bit & 1) == 1 ? bit : -1;
    }
}
