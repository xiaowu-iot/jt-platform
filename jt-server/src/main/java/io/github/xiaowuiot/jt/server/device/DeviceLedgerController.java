package io.github.xiaowuiot.jt.server.device;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.security.CurrentUser;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 设备台账接口。
 * <pre>
 * GET  /api/inventory/devices                          设备列表（类型、状态、在线、是否装车、关键字）
 * GET  /api/inventory/devices/{id}                     设备详情 + 装车履历
 * POST /api/inventory/devices/{id}/install             装车 {vehicleId, reason}（定位终端会自动换下这辆车原来的）
 * POST /api/inventory/devices/{id}/uninstall           拆车 {to: IN_STOCK/REPAIR/SCRAPPED, reason}
 * POST /api/inventory/devices/{id}/attributes          从终端读属性（0x8107）
 * POST /api/inventory/devices/{id}/sim                 插卡 {iccid}
 * POST /api/inventory/devices/import                   批量导入（CSV 文本）
 * GET  /api/inventory/devices/export                   导出 CSV
 * GET  /api/inventory/devices/stats                    统计
 * GET  /api/inventory/devices/bindings?vehicleId=      装车记录
 * </pre>
 * 有数据权限的用户只能看到自己车队车上的设备，库存设备只有不限车队的用户能看到。
 */
@RestController
@RequestMapping("/api/inventory/devices")
@Roles.On(Roles.PLATFORM)
public class DeviceLedgerController {

    private final DeviceService service;
    private final DeviceRepository devices;
    private final SimCardRepository sims;
    private final DeviceBindingRepository bindings;
    private final VehicleCache vehicles;
    private final DataScope scope;
    private final CurrentUser current;

    public DeviceLedgerController(DeviceService service, DeviceRepository devices, SimCardRepository sims, DeviceBindingRepository bindings,
                            VehicleCache vehicles, DataScope scope, CurrentUser current) {
        this.service = service;
        this.devices = devices;
        this.sims = sims;
        this.bindings = bindings;
        this.vehicles = vehicles;
        this.scope = scope;
        this.current = current;
    }

    public record DeviceView(Long id, String deviceNo, DeviceType type, String typeLabel, Device.Status status, String statusLabel,
                             Long vehicleId, String plate, String terminalId, String makerId, String model, String hardwareVersion,
                             String firmwareVersion, String iccid, int protocolVersion, Long simId, String simMsisdn, LocalDate simExpire,
                             String supplier, LocalDate purchaseDate, LocalDate warrantyUntil, LocalDateTime lastOnline,
                             LocalDateTime lastRegisterAt, LocalDateTime attributesAt, boolean online, String remark, LocalDateTime createdAt) {
    }

    DeviceView view(Device d, Map<Long, SimCard> simMap) {
        VehicleInfo v = vehicles.byId(d.getVehicleId());
        SimCard s = d.getSimId() == null ? null : simMap != null ? simMap.get(d.getSimId()) : sims.findById(d.getSimId()).orElse(null);
        return new DeviceView(d.getId(), d.getDeviceNo(), d.getType(), d.getType().label(), d.getStatus(), d.getStatus().label(),
                d.getVehicleId(), v == null ? null : v.plate(), d.getTerminalId(), d.getMakerId(), d.getModel(), d.getHardwareVersion(),
                d.getFirmwareVersion(), d.getIccid(), d.getProtocolVersion(), d.getSimId(), s == null ? null : s.getMsisdn(),
                s == null ? null : s.getExpireDate(), d.getSupplier(), d.getPurchaseDate(), d.getWarrantyUntil(), d.getLastOnline(),
                d.getLastRegisterAt(), d.getAttributesAt(), service.online(d), d.getRemark(), d.getCreatedAt());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public PageResult<DeviceView> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) DeviceType type,
                                       @RequestParam(required = false) Device.Status status, @RequestParam(required = false) Boolean online,
                                       @RequestParam(required = false) Boolean pending, @RequestParam(required = false) Long vehicleId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<Device> list = filter(keyword, type, status, pending, vehicleId);
        if (online != null) {
            list = list.stream().filter(d -> service.online(d) == online).toList();
        }
        Map<Long, SimCard> simMap = sims.findAll().stream().collect(Collectors.toMap(SimCard::getId, s -> s));
        int from = Math.min(page * size, list.size());
        List<DeviceView> items = list.subList(from, Math.min(from + size, list.size())).stream().map(d -> view(d, simMap)).toList();
        return new PageResult<>(items, list.size(), page, size);
    }

    private List<Device> filter(String keyword, DeviceType type, Device.Status status, Boolean pending, Long vehicleId) {
        Set<Long> fleets = scope.fleetIds();
        String k = keyword == null || keyword.isBlank() ? null : keyword.trim().toUpperCase();
        List<Long> plateMatch = k == null ? List.of() : vehicles.all().stream().filter(v -> v.id() != null && v.plate().toUpperCase().contains(k))
                .map(VehicleInfo::id).toList();
        return devices.findAll((root, q, cb) -> {
            List<Predicate> w = new ArrayList<>();
            if (type != null) {
                w.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                w.add(cb.equal(root.get("status"), status));
            }
            if (vehicleId != null) {
                w.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (Boolean.TRUE.equals(pending)) {
                w.add(cb.isNotNull(root.get("lastRegisterAt")));
                w.add(cb.isNull(root.get("vehicleId")));
            }
            if (k != null) {
                String like = "%" + k + "%";
                List<Predicate> or = new ArrayList<>(List.of(cb.like(cb.upper(root.get("deviceNo")), like), cb.like(cb.upper(root.get("model")), like),
                        cb.like(cb.upper(root.get("terminalId")), like), cb.like(cb.upper(root.get("iccid")), like),
                        cb.like(cb.upper(root.get("firmwareVersion")), like)));
                if (!plateMatch.isEmpty()) {
                    or.add(root.get("vehicleId").in(plateMatch));
                }
                w.add(cb.or(or.toArray(Predicate[]::new)));
            }
            return cb.and(w.toArray(Predicate[]::new));
        }, Sort.by(Sort.Direction.DESC, "id")).stream().filter(d -> {
            if (fleets == null) {
                return true;
            }
            VehicleInfo v = vehicles.byId(d.getVehicleId());
            return v != null && fleets.contains(v.fleetId());
        }).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        Device d = visible(id);
        return Map.of("device", view(d, null), "bindings", bindings.findByDeviceIdOrderByInstallTimeDesc(id),
                "sim", d.getSimId() == null ? Map.of() : sims.findById(d.getSimId()).map(s -> (Object) s).orElse(Map.of()));
    }

    private Device visible(Long id) {
        Device d = service.get(id);
        Set<Long> fleets = scope.fleetIds();
        if (fleets != null) {
            VehicleInfo v = vehicles.byId(d.getVehicleId());
            if (v == null || !fleets.contains(v.fleetId())) {
                throw ApiException.notFound("设备不存在");
            }
        }
        return d;
    }

    public record DeviceForm(String deviceNo, DeviceType type, String terminalId, String makerId, String model, String hardwareVersion,
                             String firmwareVersion, String supplier, LocalDate purchaseDate, LocalDate warrantyUntil, Device.Status status,
                             String remark) {
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public DeviceView create(@RequestBody DeviceForm f) {
        String no = no(f.deviceNo(), f.type());
        if (service.exists(no)) {
            throw ApiException.badRequest("设备号 " + no + " 已经在台账里");
        }
        Device d = new Device(no, f.type() == null ? DeviceType.JT808 : f.type());
        copy(f, d);
        return view(devices.save(d), null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public DeviceView update(@PathVariable Long id, @RequestBody DeviceForm f) {
        Device d = visible(id);
        String no = no(f.deviceNo(), d.getType());
        if (!no.equals(d.getDeviceNo())) {
            if (d.getVehicleId() != null) {
                throw ApiException.badRequest("装在车上的设备不能改设备号，先拆下来");
            }
            if (service.exists(no)) {
                throw ApiException.badRequest("设备号 " + no + " 已经在台账里");
            }
            d.setDeviceNo(no);
        }
        if (f.type() != null && d.getVehicleId() == null) {
            d.setType(f.type());
        }
        copy(f, d);
        return view(devices.save(d), null);
    }

    private static void copy(DeviceForm f, Device d) {
        d.setTerminalId(blank(f.terminalId()));
        d.setMakerId(blank(f.makerId()));
        d.setModel(blank(f.model()));
        d.setHardwareVersion(blank(f.hardwareVersion()));
        d.setFirmwareVersion(blank(f.firmwareVersion()));
        d.setSupplier(blank(f.supplier()));
        d.setPurchaseDate(f.purchaseDate());
        d.setWarrantyUntil(f.warrantyUntil());
        d.setRemark(blank(f.remark()));
        // 状态只能在没装车时在库存、维修、报废之间改；装车、拆车走专门的接口
        if (f.status() != null && f.status() != Device.Status.INSTALLED && d.getVehicleId() == null) {
            d.setStatus(f.status());
        }
    }

    /** 808 终端号去掉前导 0（和消息头里的设备号一致）；VIN 转大写 */
    static String no(String s, DeviceType type) {
        if (s == null || s.isBlank()) {
            throw ApiException.badRequest("请填写设备号");
        }
        String n = s.trim().toUpperCase();
        if (type == null || type == DeviceType.JT808 || type == DeviceType.VIDEO || type == DeviceType.ADAS) {
            n = n.replaceFirst("^0+(?=.)", "");
        }
        return n;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public void delete(@PathVariable Long id) {
        Device d = visible(id);
        if (d.getVehicleId() != null) {
            throw ApiException.badRequest("设备还装在车上，先拆下来");
        }
        if (d.getSimId() != null) {
            service.detachSim(d.getSimId());
        }
        devices.delete(d);
    }

    @PostMapping("/{id}/install")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public DeviceView install(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        visible(id);
        Long vehicleId = body.get("vehicleId") == null ? null : Long.valueOf(String.valueOf(body.get("vehicleId")));
        VehicleInfo v = vehicles.byId(vehicleId);
        if (v == null || !scope.canSee(v.fleetId())) {
            throw ApiException.badRequest("请选择车辆");
        }
        return view(service.install(id, vehicleId, current.name(), (String) body.get("reason")), null);
    }

    @PostMapping("/{id}/uninstall")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public DeviceView uninstall(@PathVariable Long id, @RequestBody Map<String, String> body) {
        visible(id);
        Device.Status to = body.get("to") == null ? Device.Status.IN_STOCK : Device.Status.valueOf(body.get("to"));
        return view(service.uninstall(id, to, current.name(), body.get("reason")), null);
    }

    @PostMapping("/{id}/attributes")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE') and hasAuthority('PERM_COMMAND_SEND')")
    public DeviceView attributes(@PathVariable Long id) throws Exception {
        visible(id);
        try {
            return view(service.queryAttributes(id, current.name()).get(15, TimeUnit.SECONDS), null);
        } catch (ExecutionException | TimeoutException e) {
            throw ApiException.badRequest("终端没有应答：" + (e.getCause() != null ? e.getCause().getMessage() : "超时"));
        }
    }

    @PostMapping("/{id}/sim")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public DeviceView sim(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Device d = visible(id);
        String iccid = body.get("iccid");
        if (iccid == null || iccid.isBlank()) {
            if (d.getSimId() != null) {
                service.detachSim(d.getSimId());
            }
        } else {
            service.attachSim(d, iccid);
        }
        return view(service.get(id), null);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/bindings")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public List<DeviceBinding> bindingList(@RequestParam(required = false) Long vehicleId) {
        Set<Long> fleets = scope.fleetIds();
        List<DeviceBinding> list = vehicleId != null ? bindings.findByVehicleIdOrderByInstallTimeDesc(vehicleId) : bindings.findTop100ByOrderByIdDesc();
        return list.stream().filter(b -> {
            if (fleets == null) {
                return true;
            }
            VehicleInfo v = vehicles.byId(b.getVehicleId());
            return v != null && fleets.contains(v.fleetId());
        }).toList();
    }

    // ---------------------------------------------------------------- 导入导出

    /**
     * 批量导入：每行 类型,设备号,型号,厂商,固件版本,ICCID,供应商,采购日期,保修到期,备注（第一行是表头可以留着）。
     * 类型写 定位终端/视频终端/主动安全终端/新能源车载终端/排放远程终端/其他设备，或者 JT808/VIDEO/ADAS/GB32960/HJ1239/OTHER
     */
    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public Map<String, Object> importCsv(@RequestBody String text) {
        int added = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        String[] lines = text.replace("﻿", "").split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("类型") || line.toLowerCase().startsWith("type")) {
                continue;
            }
            String[] c = Csv.split(line);
            try {
                DeviceType type = type(c[0]);
                String no = no(c.length > 1 ? c[1] : null, type);
                if (service.exists(no)) {
                    skipped++;
                    continue;
                }
                Device d = new Device(no, type);
                d.setModel(Csv.at(c, 2));
                d.setMakerId(Csv.at(c, 3));
                d.setFirmwareVersion(Csv.at(c, 4));
                d.setSupplier(Csv.at(c, 6));
                d.setPurchaseDate(Csv.date(Csv.at(c, 7)));
                d.setWarrantyUntil(Csv.date(Csv.at(c, 8)));
                d.setRemark(Csv.at(c, 9));
                d = devices.save(d);
                String iccid = Csv.at(c, 5);
                if (iccid != null) {
                    service.attachSim(d, iccid);
                }
                added++;
            } catch (RuntimeException e) {
                errors.add("第 " + (i + 1) + " 行：" + e.getMessage());
            }
        }
        return Map.of("added", added, "skipped", skipped, "errors", errors);
    }

    static DeviceType type(String s) {
        String t = s == null ? "" : s.trim();
        for (DeviceType x : DeviceType.values()) {
            if (x.name().equalsIgnoreCase(t) || x.label().equals(t)) {
                return x;
            }
        }
        throw new IllegalArgumentException("不认识的设备类型 " + t);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword, @RequestParam(required = false) DeviceType type,
                                         @RequestParam(required = false) Device.Status status) {
        StringBuilder sb = new StringBuilder("﻿类型,设备号,状态,车牌,型号,厂商,终端ID,硬件版本,固件版本,ICCID,协议版本,在线,最近在线,供应商,采购日期,保修到期,备注\n");
        for (Device d : filter(keyword, type, status, null, null)) {
            VehicleInfo v = vehicles.byId(d.getVehicleId());
            sb.append(Csv.line(d.getType().label(), d.getDeviceNo(), d.getStatus().label(), v == null ? "" : v.plate(), d.getModel(), d.getMakerId(),
                    d.getTerminalId(), d.getHardwareVersion(), d.getFirmwareVersion(), d.getIccid(),
                    d.getProtocolVersion() > 0 ? String.valueOf(d.getProtocolVersion()) : "", service.online(d) ? "在线" : "离线",
                    d.getLastOnline() == null ? "" : d.getLastOnline().toString().replace('T', ' ').substring(0, 16), d.getSupplier(),
                    String.valueOf(d.getPurchaseDate() == null ? "" : d.getPurchaseDate()),
                    String.valueOf(d.getWarrantyUntil() == null ? "" : d.getWarrantyUntil()), d.getRemark()));
        }
        return Csv.download("设备台账", sb);
    }

    /** 分页取设备给 SIM 页面选（只要 808 和新能源终端） */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public List<Map<String, Object>> options() {
        Page<Device> p = devices.findAll(PageRequest.of(0, 5000, Sort.by("deviceNo")));
        return p.getContent().stream().filter(d -> d.getStatus() != Device.Status.SCRAPPED).map(d -> {
            VehicleInfo v = vehicles.byId(d.getVehicleId());
            return Map.<String, Object>of("id", d.getId(), "deviceNo", d.getDeviceNo(), "type", d.getType(), "status", d.getStatus(),
                    "plate", v == null ? "" : v.plate());
        }).toList();
    }
}
