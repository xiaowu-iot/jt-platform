package io.github.xiaowuiot.jt.server.track;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 轨迹存储：按月分表 jt_track_yyyyMM。
 * <p>
 * 为什么分表：一辆车 10 秒一个点，一天 8640 条，1000 辆车一个月 2.6 亿条。全放一张表，索引越来越大，写入越来越慢，
 * 删过期数据还要 delete 几亿行。按月分表后，写入只碰当月的表，查询按时间范围定位到一两张表，过期直接 drop 整张表。
 * <p>
 * 不用 JPA：几千条一批的写入，JdbcTemplate 批量插入比 JPA 快一个数量级。
 */
@Component
public class TrackStore {

    private static final Logger log = LoggerFactory.getLogger(TrackStore.class);

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String PREFIX = "jt_track_";

    private final JdbcTemplate jdbc;
    private final boolean mysql;
    private final Set<String> created = ConcurrentHashMap.newKeySet();

    public TrackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
        this.mysql = product != null && product.toLowerCase().contains("mysql");
    }

    public static String table(YearMonth month) {
        return PREFIX + month.format(MONTH);
    }

    void ensureTable(String table) {
        if (created.contains(table)) {
            return;
        }
        String cols = "vehicle_id BIGINT NOT NULL, device_id VARCHAR(20) NOT NULL, gps_time TIMESTAMP NOT NULL, "
                + "recv_time TIMESTAMP NOT NULL, lat DOUBLE NOT NULL, lng DOUBLE NOT NULL, speed DOUBLE, direction INT, "
                + "altitude INT, mileage DOUBLE, alarm BIGINT, status BIGINT, blind SMALLINT";
        if (mysql) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" + cols
                    + ", KEY idx_" + table + " (vehicle_id, gps_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } else {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" + cols + ")");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + table + " ON " + table + " (vehicle_id, gps_time)");
        }
        created.add(table);
    }

    /**
     * 批量写入，按定位时间所在的月份分到不同的表（盲区补传的点可能属于上个月）
     */
    public void insert(List<TrackPoint> points) {
        Map<String, List<TrackPoint>> byTable = points.stream()
                .collect(Collectors.groupingBy(p -> table(YearMonth.from(p.gpsTime()))));
        byTable.forEach((table, list) -> {
            ensureTable(table);
            jdbc.batchUpdate("INSERT INTO " + table + " (vehicle_id, device_id, gps_time, recv_time, lat, lng, speed, "
                            + "direction, altitude, mileage, alarm, status, blind) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    list, 1000, (ps, p) -> {
                        ps.setLong(1, p.vehicleId());
                        ps.setString(2, p.deviceId());
                        ps.setTimestamp(3, Timestamp.valueOf(p.gpsTime()));
                        ps.setTimestamp(4, Timestamp.valueOf(p.recvTime()));
                        ps.setDouble(5, p.lat());
                        ps.setDouble(6, p.lng());
                        ps.setDouble(7, p.speed());
                        ps.setInt(8, p.direction());
                        ps.setInt(9, p.altitude());
                        if (p.mileage() == null) {
                            ps.setNull(10, java.sql.Types.DOUBLE);
                        } else {
                            ps.setDouble(10, p.mileage());
                        }
                        ps.setLong(11, p.alarm());
                        ps.setLong(12, p.status());
                        ps.setInt(13, p.blind() ? 1 : 0);
                    });
        });
    }

    /**
     * 查一辆车在时间范围内的轨迹，按定位时间排序。跨月时依次查每个月的表
     */
    public List<TrackPoint> query(long vehicleId, LocalDateTime from, LocalDateTime to, int limit) {
        List<TrackPoint> result = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)) && result.size() < limit; m = m.plusMonths(1)) {
            String table = table(m);
            if (!exists(table)) {
                continue;
            }
            result.addAll(jdbc.query("SELECT * FROM " + table
                            + " WHERE vehicle_id = ? AND gps_time BETWEEN ? AND ? ORDER BY gps_time LIMIT " + (limit - result.size()),
                    (rs, i) -> new TrackPoint(rs.getLong("vehicle_id"), rs.getString("device_id"),
                            rs.getTimestamp("gps_time").toLocalDateTime(), rs.getTimestamp("recv_time").toLocalDateTime(),
                            rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("speed"), rs.getInt("direction"),
                            rs.getInt("altitude"), (Double) rs.getObject("mileage"), rs.getLong("alarm"),
                            rs.getLong("status"), rs.getInt("blind") == 1),
                    vehicleId, Timestamp.valueOf(from), Timestamp.valueOf(to)));
        }
        return result;
    }

    /**
     * 一辆车最后一个轨迹点（本月和上个月的表里找），没有返回 null
     */
    public TrackPoint last(long vehicleId) {
        YearMonth now = YearMonth.now();
        for (YearMonth m = now; !m.isBefore(now.minusMonths(1)); m = m.minusMonths(1)) {
            String table = table(m);
            if (!exists(table)) {
                continue;
            }
            List<TrackPoint> list = jdbc.query("SELECT * FROM " + table + " WHERE vehicle_id = ? ORDER BY gps_time DESC LIMIT 1",
                    (rs, i) -> new TrackPoint(rs.getLong("vehicle_id"), rs.getString("device_id"),
                            rs.getTimestamp("gps_time").toLocalDateTime(), rs.getTimestamp("recv_time").toLocalDateTime(),
                            rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("speed"), rs.getInt("direction"),
                            rs.getInt("altitude"), (Double) rs.getObject("mileage"), rs.getLong("alarm"),
                            rs.getLong("status"), rs.getInt("blind") == 1),
                    vehicleId);
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    /**
     * 删掉早于 keepFrom 那个月的整张表
     */
    public int dropBefore(YearMonth keepFrom) {
        int dropped = 0;
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE LOWER(table_name) LIKE '" + PREFIX + "%'" + schemaFilter(),
                String.class);
        for (String t : tables) {
            String name = t.toLowerCase();
            String ym = name.substring(PREFIX.length());
            if (ym.length() == 6 && ym.chars().allMatch(Character::isDigit)
                    && YearMonth.parse(ym, MONTH).isBefore(keepFrom)) {
                jdbc.execute("DROP TABLE " + t);
                created.remove(name);
                dropped++;
                log.info("轨迹表 {} 超过保留期，已删除", t);
            }
        }
        return dropped;
    }

    /**
     * MySQL 的 information_schema 里有所有库的表，只看当前库
     */
    private String schemaFilter() {
        return mysql ? " AND table_schema = DATABASE()" : "";
    }

    private boolean exists(String table) {
        if (created.contains(table)) {
            return true;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?" + schemaFilter(), Integer.class, table);
        if (n != null && n > 0) {
            created.add(table);
            return true;
        }
        return false;
    }
}
