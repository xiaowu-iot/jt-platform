package io.github.xiaowuiot.jt.server.device;

import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.TerminalAttributes;
import io.github.xiaowuiot.jt.server.command.CommandService;
import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.platform.DeviceState;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import io.github.xiaowuiot.jt.server.vehicle.Vehicle;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleEvents;
import io.github.xiaowuiot.jt.server.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备台账：装车、拆车、换装，和车辆档案保持同步；从终端读属性（固件版本、ICCID）；统计。
 * <p>
 * 车辆档案里的终端号是接入时认终端用的，必须和"装在这辆车上的定位终端"一致：
 * <ul>
 *   <li>在设备台账里装车 / 拆车 / 换装 → 改车辆档案的终端号；</li>
 *   <li>在车辆档案里改终端号 → 设备台账跟着拆旧装新（{@link VehicleEvents.DeviceChanged}）。</li>
 * </ul>
 */
@Service
@Roles.On(Roles.PLATFORM)
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository devices;
    private final SimCardRepository sims;
    private final DeviceBindingRepository bindings;
    private final VehicleRepository vehicles;
    private final VehicleCache cache;
    private final DeviceStateService states;
    private final ObjectProvider<CommandService> commands;

    public DeviceService(DeviceRepository devices, SimCardRepository sims, DeviceBindingRepository bindings, VehicleRepository vehicles,
                         VehicleCache cache, DeviceStateService states, ObjectProvider<CommandService> commands) {
        this.devices = devices;
        this.sims = sims;
        this.bindings = bindings;
        this.vehicles = vehicles;
        this.cache = cache;
        this.states = states;
        this.commands = commands;
    }

    // ---------------------------------------------------------------- 和车辆档案同步

    /**
     * 启动时把车辆档案里已有的终端号、SIM 卡号补进设备台账（老版本升级上来、或者直接改库的情况）
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void syncAll() {
        int n = 0;
        for (Vehicle v : vehicles.findAll()) {
            if (v.getDeviceId() == null) {
                continue;
            }
            Device d = devices.findByDeviceNo(v.getDeviceId()).orElse(null);
            if (d == null) {
                d = new Device(v.getDeviceId(), DeviceType.JT808);
                fillFromVehicle(d, v);
                d.setStatus(Device.Status.INSTALLED);
                d.setVehicleId(v.getId());
                d.setRemark("从车辆档案同步");
                d = devices.save(d);
                bindings.save(new DeviceBinding(d, v.getId(), v.getPlate(), "系统", "从车辆档案同步"));
                n++;
            }
            if (v.getSimNo() != null && d.getSimId() == null) {
                attachSim(d, v.getSimNo());
            }
        }
        if (n > 0) {
            log.info("从车辆档案同步了 {} 台设备到设备台账", n);
        }
    }

    private static void fillFromVehicle(Device d, Vehicle v) {
        d.setTerminalId(v.getTerminalId());
        d.setMakerId(v.getMakerId());
        d.setModel(v.getTerminalModel());
        d.setProtocolVersion(v.getProtocolVersion());
    }

    /** 车辆档案里改了终端号、SIM 卡号 */
    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void onVehicleDevice(VehicleEvents.DeviceChanged e) {
        if (e.oldDeviceNo() != null && !e.oldDeviceNo().equals(e.newDeviceNo())) {
            devices.findByDeviceNo(e.oldDeviceNo()).filter(d -> e.vehicleId().equals(d.getVehicleId()))
                    .ifPresent(d -> detach(d, Device.Status.IN_STOCK, e.operator(), "车辆档案改了终端号"));
        }
        if (e.newDeviceNo() == null) {
            return;
        }
        Device d = devices.findByDeviceNo(e.newDeviceNo()).orElseGet(() -> new Device(e.newDeviceNo(), DeviceType.JT808));
        if (d.getId() == null || !e.vehicleId().equals(d.getVehicleId())) {
            if (d.getVehicleId() != null) {
                detach(d, Device.Status.IN_STOCK, e.operator(), "装到了 " + e.plate());
            }
            d.setVehicleId(e.vehicleId());
            d.setStatus(Device.Status.INSTALLED);
            d = devices.save(d);
            bindings.save(new DeviceBinding(d, e.vehicleId(), e.plate(), e.operator(), "车辆档案里填写终端号"));
        }
        if (e.simNo() != null) {
            attachSim(d, e.simNo());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public synchronized void onVehicleDeleted(VehicleEvents.Deleted e) {
        for (Device d : devices.findByVehicleId(e.vehicleId())) {
            detach(d, Device.Status.IN_STOCK, e.operator(), "车辆 " + e.plate() + " 删除");
        }
    }

    /** 终端注册：更新台账里的终端信息；没有档案的终端进"待登记" */
    @EventListener
    public synchronized void onRegister(VehicleEvents.TerminalRegistered e) {
        Device d = devices.findByDeviceNo(e.deviceNo()).orElseGet(() -> new Device(e.deviceNo(), DeviceType.JT808));
        d.setTerminalId(e.terminalId());
        d.setMakerId(e.makerId());
        d.setModel(e.model());
        d.setProtocolVersion(e.protocolVersion());
        d.setLastOnline(LocalDateTime.now());
        if (e.vehicleId() == null) {
            d.setLastRegisterAt(LocalDateTime.now());
            if (d.getRemark() == null) {
                d.setRemark("终端注册时上报车牌：" + (e.plate() == null || e.plate().isBlank() ? "无" : e.plate()));
            }
            devices.save(d);
            return;
        }
        boolean isNew = d.getId() == null || !e.vehicleId().equals(d.getVehicleId());
        d.setVehicleId(e.vehicleId());
        d.setStatus(Device.Status.INSTALLED);
        d = devices.save(d);
        if (isNew) {
            bindings.save(new DeviceBinding(d, e.vehicleId(), e.plate(), "系统", "终端注册"));
        }
    }

    // ---------------------------------------------------------------- 装车、拆车、换装

    /**
     * 装车。定位终端：这辆车原来的定位终端自动拆下（换装），车辆档案的终端号改成这台
     */
    public synchronized Device install(Long deviceId, Long vehicleId, String operator, String reason) {
        Device d = get(deviceId);
        if (d.getStatus() == Device.Status.SCRAPPED) {
            throw ApiException.badRequest("设备已报废");
        }
        Vehicle v = vehicles.findById(vehicleId).orElseThrow(() -> ApiException.badRequest("车辆不存在"));
        if (vehicleId.equals(d.getVehicleId())) {
            return d;
        }
        if (d.getVehicleId() != null) {
            uninstallFromVehicle(d, operator, "改装到 " + v.getPlate());
        }
        String why = reason == null || reason.isBlank() ? "安装" : reason;
        if (d.getType() == DeviceType.JT808) {
            // 一辆车只认一台定位终端
            for (Device old : devices.findByVehicleId(vehicleId)) {
                if (old.getType() == DeviceType.JT808 && !old.getId().equals(d.getId())) {
                    detach(old, Device.Status.IN_STOCK, operator, "换装：换成 " + d.getDeviceNo());
                }
            }
            if (vehicles.existsByDeviceId(d.getDeviceNo()) && !d.getDeviceNo().equals(v.getDeviceId())) {
                throw ApiException.badRequest("终端号 " + d.getDeviceNo() + " 还登记在别的车辆档案上");
            }
            v.setDeviceId(d.getDeviceNo());
            v.setAuthCode(null);
            v.setTerminalId(d.getTerminalId());
            v.setMakerId(d.getMakerId());
            v.setTerminalModel(d.getModel());
            if (d.getProtocolVersion() > 0) {
                v.setProtocolVersion(d.getProtocolVersion());
            }
            sims.findByDeviceId(d.getId()).ifPresent(s -> v.setSimNo(s.getIccid()));
            cache.put(vehicles.save(v));
        }
        d.setVehicleId(vehicleId);
        d.setStatus(Device.Status.INSTALLED);
        d = devices.save(d);
        bindings.save(new DeviceBinding(d, vehicleId, v.getPlate(), operator, why));
        return d;
    }

    /**
     * 拆下来：回库存、送修或报废
     */
    public synchronized Device uninstall(Long deviceId, Device.Status to, String operator, String reason) {
        Device d = get(deviceId);
        if (d.getVehicleId() != null) {
            uninstallFromVehicle(d, operator, reason == null || reason.isBlank() ? "拆除" : reason);
        }
        d.setStatus(to == null || to == Device.Status.INSTALLED ? Device.Status.IN_STOCK : to);
        if (d.getStatus() == Device.Status.SCRAPPED) {
            // 报废的设备，SIM 卡拔下来回库存
            sims.findByDeviceId(d.getId()).ifPresent(s -> {
                s.setDeviceId(null);
                s.setStatus(SimCard.Status.IN_STOCK);
                sims.save(s);
            });
            d.setSimId(null);
        }
        return devices.save(d);
    }

    /** 拆车：清掉车辆档案上的终端号 */
    private void uninstallFromVehicle(Device d, String operator, String reason) {
        Long vid = d.getVehicleId();
        if (d.getType() == DeviceType.JT808) {
            vehicles.findById(vid).filter(v -> d.getDeviceNo().equals(v.getDeviceId())).ifPresent(v -> {
                v.setDeviceId(null);
                v.setAuthCode(null);
                cache.put(vehicles.save(v));
            });
        }
        detach(d, Device.Status.IN_STOCK, operator, reason);
    }

    /** 只改台账：结束装车记录，设备回库存（车辆档案已经改好了或不用改） */
    private void detach(Device d, Device.Status to, String operator, String reason) {
        bindings.findFirstByDeviceIdAndRemoveTimeIsNullOrderByInstallTimeDesc(d.getId()).ifPresent(b -> {
            b.remove(operator, reason);
            bindings.save(b);
        });
        d.setVehicleId(null);
        d.setStatus(to);
        devices.save(d);
    }

    // ---------------------------------------------------------------- SIM 卡

    /** 把 SIM 卡插到设备上（按 ICCID 找卡，没有就建一张） */
    public synchronized SimCard attachSim(Device d, String iccid) {
        String no = iccid.trim().toUpperCase();
        SimCard s = sims.findByIccid(no).orElseGet(() -> new SimCard(no));
        if (d.getId().equals(s.getDeviceId())) {
            return s;
        }
        // 这台设备原来插的卡拔下来
        sims.findByDeviceId(d.getId()).filter(old -> !old.getIccid().equals(no)).ifPresent(old -> {
            old.setDeviceId(null);
            old.setStatus(SimCard.Status.IN_STOCK);
            sims.save(old);
        });
        if (s.getDeviceId() != null) {
            devices.findById(s.getDeviceId()).ifPresent(other -> {
                other.setSimId(null);
                devices.save(other);
            });
        }
        s.setDeviceId(d.getId());
        if (s.getStatus() == SimCard.Status.IN_STOCK) {
            s.setStatus(SimCard.Status.IN_USE);
        }
        s = sims.save(s);
        d.setSimId(s.getId());
        d.setIccid(no);
        devices.save(d);
        return s;
    }

    public synchronized void detachSim(Long simId) {
        SimCard s = sims.findById(simId).orElseThrow(() -> ApiException.notFound("SIM 卡不存在"));
        if (s.getDeviceId() != null) {
            devices.findById(s.getDeviceId()).ifPresent(d -> {
                d.setSimId(null);
                devices.save(d);
            });
        }
        s.setDeviceId(null);
        if (s.getStatus() == SimCard.Status.IN_USE) {
            s.setStatus(SimCard.Status.IN_STOCK);
        }
        sims.save(s);
    }

    // ---------------------------------------------------------------- 从终端读属性

    /**
     * 下发查询终端属性（0x8107），终端回 0x0107：型号、终端 ID、硬件版本、固件版本、ICCID
     */
    public CompletableFuture<Device> queryAttributes(Long deviceId, String operator) {
        Device d = get(deviceId);
        CommandService c = commands.getIfAvailable();
        if (d.getType() != DeviceType.JT808 || c == null) {
            throw ApiException.badRequest("只有 808 终端能查询属性");
        }
        if (!c.isOnline(d.getDeviceNo())) {
            throw ApiException.badRequest("终端不在线");
        }
        return c.send(d.getDeviceNo(), MessageId.QUERY_ATTRIBUTES, null, 10, "查询终端属性", operator)
                .thenApply(msg -> applyAttributes(deviceId, msg.getBody()));
    }

    private synchronized Device applyAttributes(Long deviceId, Object body) {
        Device d = get(deviceId);
        if (body instanceof TerminalAttributes a) {
            d.setModel(blank(a.getModel(), d.getModel()));
            d.setMakerId(blank(a.getMakerId(), d.getMakerId()));
            d.setTerminalId(blank(a.getTerminalId(), d.getTerminalId()));
            d.setHardwareVersion(blank(a.getHardwareVersion(), d.getHardwareVersion()));
            d.setFirmwareVersion(blank(a.getFirmwareVersion(), d.getFirmwareVersion()));
            d.setAttributesAt(LocalDateTime.now());
            d = devices.save(d);
            if (a.getIccid() != null && !a.getIccid().isBlank()) {
                attachSim(d, a.getIccid());
            }
        }
        return devices.findById(deviceId).orElse(d);
    }

    private static String blank(String s, String old) {
        return s == null || s.isBlank() ? old : s.trim();
    }

    /**
     * 后台慢慢把在线终端的属性读一遍：没读过的、超过 7 天的，每轮最多 20 台
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void refreshAttributes() {
        CommandService c = commands.getIfAvailable();
        if (c == null) {
            return;
        }
        LocalDateTime stale = LocalDateTime.now().minusDays(7);
        devices.findAll().stream()
                .filter(d -> d.getType() == DeviceType.JT808 && (d.getAttributesAt() == null || d.getAttributesAt().isBefore(stale)))
                .filter(d -> c.isOnline(d.getDeviceNo())).limit(20)
                .forEach(d -> {
                    try {
                        queryAttributes(d.getId(), "系统").exceptionally(ex -> null);
                    } catch (RuntimeException ex) {
                        // 终端刚好掉线
                    }
                });
    }

    /** 每 5 分钟记一次在线终端的"最近在线时间" */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void touchOnline() {
        LocalDateTime now = LocalDateTime.now();
        List<Device> changed = new ArrayList<>();
        for (Device d : devices.findAll()) {
            if (d.getType() != DeviceType.JT808) {
                continue;
            }
            DeviceState s = states.get(d.getDeviceNo());
            if (s != null && s.isOnline()) {
                d.setLastOnline(now);
                changed.add(d);
            }
        }
        devices.saveAll(changed);
    }

    // ---------------------------------------------------------------- 查询、统计

    public Device get(Long id) {
        return devices.findById(id).orElseThrow(() -> ApiException.notFound("设备不存在"));
    }

    public boolean online(Device d) {
        if (d.getType() == DeviceType.JT808) {
            DeviceState s = states.get(d.getDeviceNo());
            return s != null && s.isOnline();
        }
        // 新能源、排放终端：10 分钟内登入或上报过
        return d.getLastOnline() != null && d.getLastOnline().isAfter(LocalDateTime.now().minusMinutes(10));
    }

    public Map<String, Object> stats() {
        List<Device> all = devices.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("byStatus", count(all, d -> d.getStatus().name()));
        m.put("byType", count(all, d -> d.getType().name()));
        List<Device> installed = all.stream().filter(d -> d.getStatus() == Device.Status.INSTALLED).toList();
        long online = installed.stream().filter(this::online).count();
        m.put("installed", installed.size());
        m.put("online", online);
        m.put("onlineRate", installed.isEmpty() ? null : Math.round(online * 1000.0 / installed.size()) / 10.0);
        m.put("byModel", top(count(all, d -> d.getModel() == null ? "未知" : d.getModel())));
        m.put("byFirmware", top(count(all.stream().filter(d -> d.getType() == DeviceType.JT808).toList(),
                d -> d.getFirmwareVersion() == null ? "未读取" : d.getFirmwareVersion())));
        LocalDateTime week = LocalDateTime.now().minusDays(7);
        m.put("longOffline", installed.stream().filter(d -> !online(d) && (d.getLastOnline() == null || d.getLastOnline().isBefore(week))).count());
        m.put("pending", all.stream().filter(d -> d.getLastRegisterAt() != null && d.getVehicleId() == null).count());
        LocalDate soon = LocalDate.now().plusDays(30);
        List<SimCard> simList = sims.findAll();
        m.put("sims", simList.size());
        m.put("simExpiring", simList.stream().filter(s -> s.getExpireDate() != null && !s.getExpireDate().isAfter(soon)
                && s.getStatus() != SimCard.Status.CANCELED).count());
        m.put("simOverFlow", simList.stream().filter(s -> s.getPlanMb() > 0 && s.getUsedMb() >= s.getPlanMb() * 0.9).count());
        m.put("warrantyExpiring", all.stream().filter(d -> d.getWarrantyUntil() != null && !d.getWarrantyUntil().isAfter(soon)
                && d.getStatus() != Device.Status.SCRAPPED).count());
        return m;
    }

    private static Map<String, Long> count(List<Device> list, Function<Device, String> key) {
        return list.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    private static List<Map<String, Object>> top(Map<String, Long> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue())).toList();
    }

    /** 是否已经有这个设备号（导入时判重） */
    public boolean exists(String deviceNo) {
        return devices.findByDeviceNo(deviceNo).isPresent();
    }

}
