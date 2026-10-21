package io.github.xiaowuiot.jt.server.view;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.netty.buffer.ByteBufUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 位置的 JSON 视图：报警位、状态位翻译成文字，附加信息按 ID 列出。
 *
 * @param lat     纬度（WGS84，终端上报的原始坐标）
 * @param lng     经度
 * @param speed   速度 km/h
 * @param extras  附加信息，key 是十六进制 ID
 */
public record LocationView(double lat, double lng, int altitude, double speed, int direction, LocalDateTime time,
                           long alarm, List<String> alarms, long status, List<String> statuses,
                           Double mileageKm, Double fuelL, Integer satellites, Integer signal,
                           Map<String, Object> extras) {

    public static LocationView of(Location l) {
        Map<String, Object> extras = new LinkedHashMap<>();
        l.getExtras().forEach((id, v) -> extras.put(String.format("0x%02X", id),
                v instanceof byte[] b ? ByteBufUtil.hexDump(b) : v));
        return new LocationView(l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(), l.getDirection(),
                l.getTime(), l.getAlarm(), Jt808Flags.alarmNames(l.getAlarm()), l.getStatus(),
                Jt808Flags.statusNames(l.getStatus()), l.getMileageKm(), l.getFuelLiters(), l.getSatellites(),
                l.getNetworkSignal(), extras);
    }
}
