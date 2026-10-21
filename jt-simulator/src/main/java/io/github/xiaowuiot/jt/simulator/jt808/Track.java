package io.github.xiaowuiot.jt.simulator.jt808;

import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟行驶轨迹：从起点出发，按速度和方向前进，每隔一段随机转个弯。
 * <p>
 * 不追求真实路网，目的是让平台收到"会动的、方向和速度合理的"位置，画出来是一条连续的线。
 */
public class Track {

    /**
     * 1 度纬度约等于 111 km
     */
    private static final double METERS_PER_DEGREE = 111_000;

    private double lat;
    private double lng;
    private double speed;
    private int direction;
    private double mileageKm;
    private int stoppedSeconds;
    private int minSpeed = 0;
    private int maxSpeed = 120;
    private List<double[]> waypoints;
    private int waypointIndex;
    private boolean loop;
    private double stopChance;
    /** 路线上要停靠的拐点：拐点序号 → 停多少秒（收集点、公交站） */
    private Map<Integer, Integer> dwellPoints;
    private Map<Integer, Integer> loadPoints;
    private double loadKg = 200;
    /** 路线上要等到某个时间再走的拐点（公交、校车的首末站等发车时间）：拐点序号 → 现在还要等几秒 */
    private Map<Integer, IntSupplier> holdPoints;

    public Track(double lat, double lng) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        this.lat = lat;
        this.lng = lng;
        this.speed = 20 + r.nextInt(60);
        this.direction = r.nextInt(360);
    }

    /**
     * 前进 seconds 秒
     */
    public void move(int seconds) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (stoppedSeconds > 0) {
            speed = 0;
            stoppedSeconds -= seconds;
            if (stoppedSeconds <= 0) {
                speed = Math.max(minSpeed, Math.min(maxSpeed, 20 + r.nextInt(20)));
            }
            return;
        }
        if (stopChance > 0 && r.nextDouble() < stopChance) {
            // 环卫车停下来收垃圾、渣土车排队装卸、物流车卸货
            stop(r.nextInt(60, 300));
            return;
        }
        if (r.nextInt(10) == 0) {
            direction = (direction + r.nextInt(-90, 91) + 360) % 360;
        }
        speed = Math.max(minSpeed, Math.min(maxSpeed, speed + r.nextInt(-10, 11)));
        double meters = speed / 3.6 * seconds;
        if (waypoints != null) {
            followStep(meters);
            return;
        }
        double rad = Math.toRadians(direction);
        // 正北为 0 度，顺时针：北向分量是 cos，东向分量是 sin
        lat += meters * Math.cos(rad) / METERS_PER_DEGREE;
        lng += meters * Math.sin(rad) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        mileageKm += meters / 1000;
    }

    /**
     * 原地停车 seconds 秒（加油、装卸货），之后继续行驶
     */
    public void stop(int seconds) {
        stoppedSeconds = seconds;
        speed = 0;
    }

    /**
     * 限制车速范围，比如洒水车作业时 10~25 km/h
     */
    public void setSpeedRange(int min, int max) {
        this.minSpeed = min;
        this.maxSpeed = max;
        speed = Math.max(min, Math.min(max, speed));
    }

    public boolean isStopped() {
        return stoppedSeconds > 0;
    }

    /**
     * 沿着一串拐点开（lat, lng），开到最后一个点后恢复随机行驶
     */
    public void follow(List<double[]> points) {
        this.waypoints = points;
        this.waypointIndex = 0;
    }

    /**
     * 沿一条闭合路线循环行驶，从第 startIndex 个点出发。演示时同一条路线上放几辆车，从不同位置出发
     */
    public void followLoop(List<double[]> points, int startIndex) {
        this.waypoints = points;
        this.waypointIndex = Math.floorMod(startIndex, points.size());
        this.loop = true;
        this.lat = points.get(waypointIndex)[0];
        this.lng = points.get(waypointIndex)[1];
    }

    /**
     * 沿路线开到这些拐点时停一会儿再走，演示收运车到收集点停车收垃圾、公交车进站
     */
    public void setDwellPoints(Map<Integer, Integer> dwellPoints) {
        this.dwellPoints = dwellPoints;
    }

    /**
     * 载重模型（环卫车）：拐点序号 → 1 收集点装上 20~80 kg / 2 拉上压缩箱 / -1 卸空。设置了才按这个报载重
     */
    public void setLoadPoints(Map<Integer, Integer> loadPoints) {
        this.loadPoints = loadPoints;
    }

    public boolean hasLoad() {
        return loadPoints != null;
    }

    public long getLoadKg() {
        return Math.round(loadKg);
    }

    /**
     * 开到这些拐点时，停到发车时间再走。等待秒数在到站那一刻现算（离下一班发车还有多久）
     */
    public void setHoldPoints(Map<Integer, IntSupplier> holdPoints) {
        this.holdPoints = holdPoints;
    }

    /**
     * 每次前进时停车的概率
     */
    public void setStopChance(double stopChance) {
        this.stopChance = stopChance;
    }

    /**
     * 当前里程表读数（km），演示时每辆车给一个不同的起始里程
     */
    public void setMileageKm(double mileageKm) {
        this.mileageKm = mileageKm;
    }

    /**
     * 不再沿路线走，右转 90 度离开
     */
    public void deviate() {
        waypoints = null;
        direction = (direction + 90) % 360;
    }

    public boolean isFollowing() {
        return waypoints != null;
    }

    private void followStep(double meters) {
        // 路线上的点很密（二三十米一个），一次上报间隔可能要走过好几个点
        while (meters > 0 && waypoints != null) {
            double[] wp = waypoints.get(waypointIndex);
            double dy = (wp[0] - lat) * METERS_PER_DEGREE;
            double dx = (wp[1] - lng) * METERS_PER_DEGREE * Math.cos(Math.toRadians(lat));
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0.5) {
                direction = (int) Math.round((Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360);
            }
            if (meters < dist) {
                lat += dy / dist * meters / METERS_PER_DEGREE;
                lng += dx / dist * meters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));
                mileageKm += meters / 1000;
                return;
            }
            lat = wp[0];
            lng = wp[1];
            mileageKm += dist / 1000;
            meters -= dist;
            int reached = waypointIndex;
            if (++waypointIndex >= waypoints.size()) {
                if (loop) {
                    waypointIndex = 0;
                } else {
                    waypoints = null;
                }
            }
            if (holdPoints != null && holdPoints.containsKey(reached)) {
                int wait = holdPoints.get(reached).getAsInt();
                if (wait > 0) {
                    stop(wait);
                    return;
                }
            }
            if (loadPoints != null && loadPoints.containsKey(reached)) {
                // 1 收集点装上 20~80 kg；2 转运车在中转站拉上一个压缩箱（8~12 吨）；-1 卸空（留一点残余）
                int kind = loadPoints.get(reached);
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                loadKg = kind == 1 ? loadKg + 20 + rnd.nextInt(61) : kind == 2 ? 8_000 + rnd.nextInt(4_000) : 150 + rnd.nextInt(100);
            }
            if (dwellPoints != null && dwellPoints.containsKey(reached)) {
                stop(dwellPoints.get(reached));
                return;
            }
        }
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getSpeed() {
        return speed;
    }

    public int getDirection() {
        return direction;
    }

    public double getMileageKm() {
        return mileageKm;
    }
}
