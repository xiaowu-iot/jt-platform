package io.github.xiaowuiot.jt.server.vehicle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全部车辆档案的内存缓存，按设备号和车辆 ID 两种方式查。
 * <p>
 * 终端注册、鉴权在 Netty 的 IO 线程里处理，一台车查一次库，平台重启后几千台车同时重连就会把数据库打满、IO 线程卡住。
 * 所以启动时全量加载，之后档案有改动就同步更新缓存。一万台车也就几 MB 内存。
 */
@Component
public class VehicleCache {

    private static final Logger log = LoggerFactory.getLogger(VehicleCache.class);

    private final Map<String, VehicleInfo> byDevice = new ConcurrentHashMap<>();
    private final Map<Long, VehicleInfo> byId = new ConcurrentHashMap<>();

    public VehicleCache(VehicleRepository repo) {
        repo.findAll().forEach(this::put);
        log.info("车辆档案已加载 {} 台", byId.size());
    }

    public void put(Vehicle v) {
        VehicleInfo info = VehicleInfo.of(v);
        VehicleInfo old = byId.put(v.getId(), info);
        if (old != null && old.deviceId() != null && !old.deviceId().equals(v.getDeviceId())) {
            byDevice.remove(old.deviceId());
        }
        if (v.getDeviceId() != null) {
            byDevice.put(v.getDeviceId(), info);
        }
    }

    /**
     * 注册时先在缓存里记下鉴权码和版本，档案稍后异步写库。新终端还没有档案时先放一条没有 ID 的临时记录
     */
    public void updateAuth(String deviceId, String authCode, int protocolVersion) {
        byDevice.compute(deviceId, (k, old) -> {
            VehicleInfo info = old == null
                    ? new VehicleInfo(null, "未登记" + deviceId, 0, VehicleType.OTHER, null, deviceId, authCode,
                    protocolVersion, true, VehicleType.OTHER.speedLimit(), VehicleType.OTHER.name())
                    : new VehicleInfo(old.id(), old.plate(), old.plateColor(), old.type(), old.fleetId(), deviceId,
                    authCode, protocolVersion, old.enabled(), old.speedLimit(), old.icon());
            if (info.id() != null) {
                byId.put(info.id(), info);
            }
            return info;
        });
    }

    public void remove(Long id) {
        VehicleInfo old = byId.remove(id);
        if (old != null && old.deviceId() != null) {
            byDevice.remove(old.deviceId(), old);
        }
    }

    public VehicleInfo byDevice(String deviceId) {
        return deviceId == null ? null : byDevice.get(deviceId);
    }

    public VehicleInfo byId(Long id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<VehicleInfo> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
