package io.github.xiaowuiot.jt.server.device;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物联网卡（SIM 卡）。
 * <pre>
 * GET  /api/inventory/sims?keyword=&status=&alert=expiring|flow|idle   卡列表（快到期、流量快用完、没插设备）
 * POST /api/inventory/sims/import                                      批量导入或更新（CSV 文本，按 ICCID 对上已有的卡就更新流量和到期时间）
 * GET  /api/inventory/sims/export                                      导出 CSV
 * </pre>
 */
@RestController
@RequestMapping("/api/inventory/sims")
@Roles.On(Roles.PLATFORM)
public class SimController {

    private final SimCardRepository sims;
    private final DeviceRepository devices;
    private final DeviceService service;
    private final VehicleCache vehicles;
    private final DataScope scope;

    public SimController(SimCardRepository sims, DeviceRepository devices, DeviceService service, VehicleCache vehicles, DataScope scope) {
        this.sims = sims;
        this.devices = devices;
        this.service = service;
        this.vehicles = vehicles;
        this.scope = scope;
    }

    public record SimView(Long id, String iccid, String msisdn, String operator, int planMb, double usedMb, Double usedRate,
                          LocalDate activateDate, LocalDate expireDate, Long daysLeft, SimCard.Status status, String statusLabel,
                          Long deviceId, String deviceNo, String plate, String remark) {
    }

    private List<SimView> views() {
        Map<Long, Device> byId = devices.findAll().stream().collect(Collectors.toMap(Device::getId, d -> d));
        Set<Long> fleets = scope.fleetIds();
        LocalDate today = LocalDate.now();
        List<SimView> list = new ArrayList<>();
        for (SimCard s : sims.findAll()) {
            Device d = s.getDeviceId() == null ? null : byId.get(s.getDeviceId());
            VehicleInfo v = d == null ? null : vehicles.byId(d.getVehicleId());
            if (fleets != null && (v == null || !fleets.contains(v.fleetId()))) {
                continue;
            }
            list.add(new SimView(s.getId(), s.getIccid(), s.getMsisdn(), s.getOperator(), s.getPlanMb(), s.getUsedMb(),
                    s.getPlanMb() > 0 ? Math.round(s.getUsedMb() * 1000 / s.getPlanMb()) / 10.0 : null, s.getActivateDate(), s.getExpireDate(),
                    s.getExpireDate() == null ? null : ChronoUnit.DAYS.between(today, s.getExpireDate()),
                    s.getStatus(), s.getStatus().label(), s.getDeviceId(), d == null ? null : d.getDeviceNo(), v == null ? null : v.plate(), s.getRemark()));
        }
        list.sort(Comparator.comparing(SimView::id).reversed());
        return list;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public List<SimView> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) SimCard.Status status,
                              @RequestParam(required = false) String alert) {
        String k = keyword == null ? null : keyword.trim().toUpperCase();
        return views().stream().filter(s -> status == null || s.status() == status)
                .filter(s -> k == null || k.isEmpty() || s.iccid().contains(k) || s.msisdn() != null && s.msisdn().contains(k)
                        || s.plate() != null && s.plate().toUpperCase().contains(k) || s.deviceNo() != null && s.deviceNo().contains(k))
                .filter(s -> alert == null || switch (alert) {
                    case "expiring" -> s.daysLeft() != null && s.daysLeft() <= 30 && s.status() != SimCard.Status.CANCELED;
                    case "flow" -> s.usedRate() != null && s.usedRate() >= 90;
                    case "idle" -> s.deviceId() == null && s.status() != SimCard.Status.CANCELED;
                    default -> true;
                }).toList();
    }

    public record SimForm(String iccid, String msisdn, String operator, Integer planMb, Double usedMb, LocalDate activateDate,
                          LocalDate expireDate, SimCard.Status status, String remark, Long deviceId) {
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public Map<String, Object> create(@RequestBody SimForm f) {
        String iccid = iccid(f.iccid());
        if (sims.findByIccid(iccid).isPresent()) {
            throw ApiException.badRequest("ICCID " + iccid + " 已经有了");
        }
        SimCard s = sims.save(copy(f, new SimCard(iccid)));
        bind(s, f.deviceId());
        return Map.of("id", s.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody SimForm f) {
        SimCard s = sims.findById(id).orElseThrow(() -> ApiException.notFound("SIM 卡不存在"));
        s = sims.save(copy(f, s));
        if (!Objects.equals(f.deviceId(), s.getDeviceId())) {
            bind(s, f.deviceId());
        }
        return Map.of("id", id);
    }

    private void bind(SimCard s, Long deviceId) {
        if (deviceId == null) {
            if (s.getDeviceId() != null) {
                service.detachSim(s.getId());
            }
            return;
        }
        Device d = devices.findById(deviceId).orElseThrow(() -> ApiException.badRequest("设备不存在"));
        service.attachSim(d, s.getIccid());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public void delete(@PathVariable Long id) {
        SimCard s = sims.findById(id).orElseThrow(() -> ApiException.notFound("SIM 卡不存在"));
        if (s.getDeviceId() != null) {
            throw ApiException.badRequest("卡还插在设备上，先拔下来");
        }
        sims.delete(s);
    }

    private static SimCard copy(SimForm f, SimCard s) {
        s.setMsisdn(blank(f.msisdn()));
        s.setOperator(blank(f.operator()));
        s.setPlanMb(f.planMb() == null ? 0 : Math.max(0, f.planMb()));
        s.setUsedMb(f.usedMb() == null ? 0 : Math.max(0, f.usedMb()));
        s.setActivateDate(f.activateDate());
        s.setExpireDate(f.expireDate());
        if (f.status() != null) {
            s.setStatus(f.status());
        }
        s.setRemark(blank(f.remark()));
        return s;
    }

    static String iccid(String s) {
        if (s == null || s.isBlank()) {
            throw ApiException.badRequest("请填写 ICCID");
        }
        String v = s.trim().toUpperCase();
        if (!v.matches("[0-9A-F]{19,20}")) {
            throw ApiException.badRequest("ICCID 是 19~20 位：" + s);
        }
        return v;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * 每行 ICCID,卡号,运营商,套餐MB,已用MB,激活日期,到期日期,备注。ICCID 已有的就更新流量、到期时间（运营商平台每月导出一次直接导进来）
     */
    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public Map<String, Object> importCsv(@RequestBody String text) {
        int added = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        String[] lines = text.replace("﻿", "").split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.toUpperCase().startsWith("ICCID")) {
                continue;
            }
            String[] c = Csv.split(line);
            try {
                String iccid = iccid(c[0]);
                SimCard s = sims.findByIccid(iccid).orElse(null);
                if (s == null) {
                    s = new SimCard(iccid);
                    added++;
                } else {
                    updated++;
                }
                if (Csv.at(c, 1) != null) {
                    s.setMsisdn(Csv.at(c, 1));
                }
                if (Csv.at(c, 2) != null) {
                    s.setOperator(Csv.at(c, 2));
                }
                if (Csv.at(c, 3) != null) {
                    s.setPlanMb(Integer.parseInt(Csv.at(c, 3)));
                }
                if (Csv.at(c, 4) != null) {
                    s.setUsedMb(Double.parseDouble(Csv.at(c, 4)));
                }
                if (Csv.at(c, 5) != null) {
                    s.setActivateDate(Csv.date(Csv.at(c, 5)));
                }
                if (Csv.at(c, 6) != null) {
                    s.setExpireDate(Csv.date(Csv.at(c, 6)));
                }
                if (Csv.at(c, 7) != null) {
                    s.setRemark(Csv.at(c, 7));
                }
                sims.save(s);
            } catch (RuntimeException e) {
                errors.add("第 " + (i + 1) + " 行：" + e.getMessage());
            }
        }
        return Map.of("added", added, "updated", updated, "errors", errors);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public ResponseEntity<byte[]> export() {
        StringBuilder sb = new StringBuilder("﻿ICCID,卡号,运营商,套餐MB,已用MB,激活日期,到期日期,状态,设备号,车牌,备注\n");
        for (SimView s : views()) {
            sb.append(Csv.line(s.iccid(), s.msisdn(), s.operator(), String.valueOf(s.planMb()), String.valueOf(s.usedMb()),
                    s.activateDate() == null ? "" : s.activateDate().toString(), s.expireDate() == null ? "" : s.expireDate().toString(),
                    s.statusLabel(), s.deviceNo(), s.plate(), s.remark()));
        }
        return Csv.download("SIM卡", sb);
    }
}
