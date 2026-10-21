package io.github.xiaowuiot.jt.server.platform;

import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.protocol.jt808.body.LocationBatch;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkMessage;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每台终端的最新状态：在线与否、最后一条位置。实时地图直接读这里，不查库。
 * <p>
 * 只保留"最新"：补传的盲区数据（0x0704）比当前位置旧，不能覆盖掉当前位置，按定位时间比较。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class DeviceStateService {

    private final ConcurrentHashMap<String, DeviceState> states = new ConcurrentHashMap<>();

    public DeviceStateService(UplinkBus bus) {
        bus.subscribe("device-state", this::onMessage);
    }

    void onMessage(UplinkMessage m) {
        if (m.kind() == UplinkMessage.Kind.ATTACHMENT || m.kind() == UplinkMessage.Kind.EV || m.kind() == UplinkMessage.Kind.EMISSION) {
            return;
        }
        DeviceState s = states.computeIfAbsent(m.deviceId(), DeviceState::new);
        switch (m.kind()) {
            case ONLINE -> s.online(m.receiveTime(), m.remote());
            case OFFLINE -> s.offline(m.receiveTime());
            case MESSAGE -> {
                s.touch(m.receiveTime());
                if (m.msgId() == MessageId.LOCATION) {
                    s.location(m.<Location>body());
                } else if (m.msgId() == MessageId.LOCATION_BATCH) {
                    m.<LocationBatch>body().getLocations().forEach(s::location);
                }
            }
        }
    }

    public DeviceState get(String deviceId) {
        return states.get(deviceId);
    }

    public List<DeviceState> all() {
        Collection<DeviceState> values = states.values();
        List<DeviceState> list = new ArrayList<>(values);
        list.sort(Comparator.comparing(DeviceState::getDeviceId));
        return list;
    }

    public long onlineCount() {
        return states.values().stream().filter(DeviceState::isOnline).count();
    }
}
