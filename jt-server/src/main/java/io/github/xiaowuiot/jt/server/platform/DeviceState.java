package io.github.xiaowuiot.jt.server.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.jt.server.view.LocationView;

/**
 * 一台终端的最新状态。只在总线线程里写，接口线程里读，字段用 volatile 保证可见性。
 */
public class DeviceState {

    private final String deviceId;
    private volatile boolean online;
    private volatile long onlineTime;
    private volatile long offlineTime;
    private volatile long lastActiveTime;
    private volatile String remote;
    private volatile Location location;

    DeviceState(String deviceId) {
        this.deviceId = deviceId;
    }

    void online(long time, String remote) {
        this.online = true;
        this.onlineTime = time;
        this.lastActiveTime = time;
        this.remote = remote;
    }

    void offline(long time) {
        this.online = false;
        this.offlineTime = time;
    }

    void touch(long time) {
        this.lastActiveTime = time;
    }

    void location(Location l) {
        Location cur = location;
        if (cur == null || l.getTime() == null || cur.getTime() == null || !l.getTime().isBefore(cur.getTime())) {
            location = l;
        }
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isOnline() {
        return online;
    }

    public long getOnlineTime() {
        return onlineTime;
    }

    public long getOfflineTime() {
        return offlineTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public String getRemote() {
        return remote;
    }

    @JsonIgnore
    public Location getRawLocation() {
        return location;
    }

    public LocationView getLocation() {
        Location l = location;
        return l == null ? null : LocationView.of(l);
    }
}
