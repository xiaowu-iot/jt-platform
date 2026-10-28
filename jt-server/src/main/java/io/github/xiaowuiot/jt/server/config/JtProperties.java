package io.github.xiaowuiot.jt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * jt.* 配置。
 */
@ConfigurationProperties(prefix = "jt")
public class JtProperties {

    /**
     * 本进程启用的角色，默认全部启用
     */
    private List<String> roles = new ArrayList<>(Roles.ALL);

    private Access access = new Access();

    private Demo demo = new Demo();

    private MapSettings map = new MapSettings();

    private Track track = new Track();

    /**
     * 数据目录：多媒体文件、附件等
     */
    private String dataDir = "./data";

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * 首次启动时创建的管理员 admin 的密码。留空则随机生成，写到 data/initial-admin.txt
     */
    private String adminPassword = "";

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public MapSettings getMap() {
        return map;
    }

    public void setMap(MapSettings map) {
        this.map = map;
    }

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public static class Demo {

        /**
         * 首次启动时生成演示数据：车队、车辆、演示账号
         */
        private boolean enabled = true;

        /**
         * 内置模拟终端：平台自己按演示路线起一批终端，连到本机的接入端口
         */
        private boolean simulator = true;

        /**
         * 演示账号的密码
         */
        private String password = "demo123";

        /**
         * 模拟终端的位置上报间隔（秒）
         */
        private int interval = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSimulator() {
            return simulator;
        }

        public void setSimulator(boolean simulator) {
            this.simulator = simulator;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }

    }

    /**
     * 地图配置，原样给前端
     */
    public static class MapSettings {

        /**
         * 逆地理编码（经纬度转地址）：auto 按配置的 key 自动选、nominatim（OpenStreetMap，免 key，每秒最多 1 次）、amap（高德 Web 服务，要 key）、tianditu（天地图，用 tianditu-key）、none 不转
         */
        private String geocoder = "auto";

        /**
         * 高德 Web 服务 key（逆地理用）。这个配置会随地图设置发给前端，key 不能跟着发出去
         */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String amapWebKey = "";

        public String getGeocoder() {
            return geocoder;
        }

        public void setGeocoder(String geocoder) {
            this.geocoder = geocoder;
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public String getAmapWebKey() {
            return amapWebKey;
        }

        public void setAmapWebKey(String amapWebKey) {
            this.amapWebKey = amapWebKey;
        }

        /**
         * 底图：amap 高德瓦片（GCJ-02），tianditu 天地图（需要 key，CGCS2000）
         */
        private String provider = "amap";

        /**
         * 天地图的 key
         */
        private String tiandituKey = "";

        /**
         * 地图默认中心（GCJ-02）和缩放级别
         */
        private double centerLat = 22.585;
        private double centerLng = 114.03;
        private int zoom = 11;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getTiandituKey() {
            return tiandituKey;
        }

        public void setTiandituKey(String tiandituKey) {
            this.tiandituKey = tiandituKey;
        }

        public double getCenterLat() {
            return centerLat;
        }

        public void setCenterLat(double centerLat) {
            this.centerLat = centerLat;
        }

        public double getCenterLng() {
            return centerLng;
        }

        public void setCenterLng(double centerLng) {
            this.centerLng = centerLng;
        }

        public int getZoom() {
            return zoom;
        }

        public void setZoom(int zoom) {
            this.zoom = zoom;
        }
    }

    public static class Track {

        /**
         * 轨迹保留多少个月，按月分表，过期的整张表删掉
         */
        private int retentionMonths = 6;

        public int getRetentionMonths() {
            return retentionMonths;
        }

        public void setRetentionMonths(int retentionMonths) {
            this.retentionMonths = retentionMonths;
        }
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    public static class Access {

        /**
         * 808 终端接入端口
         */
        private int port = 7611;

        /**
         * 读空闲超时（秒），一般设为终端心跳间隔的 3 倍
         */
        private int idleSeconds = 180;

        /**
         * IO 线程数，0 表示 CPU 核数 × 2
         */
        private int workerThreads = 0;

        /**
         * 指令等待终端应答的默认超时（秒）
         */
        private int commandTimeoutSeconds = 10;

        /**
         * 档案里没有的终端注册时自动建档。演示、联调环境打开，生产环境关掉，只允许登记过的终端接入
         */
        private boolean autoRegister = true;

        /**
         * 接入端口绑定的地址。只跑内置模拟终端的演示环境可以设成 127.0.0.1
         */
        private String bindAddress = "0.0.0.0";

        /**
         * 连上之后多少秒内不鉴权就断开：防止大量空连接占满句柄
         */
        private int authTimeoutSeconds = 60;

        /**
         * 连接数上限（总数、单个 IP），0 表示不限。物联网卡专网很多车共用一个出口 IP，单 IP 上限要按现场配
         */
        private int maxConnections = 0;
        private int maxConnectionsPerIp = 0;

        /**
         * 分包下发（升级包、长文本）同时在途的包数，和每包等终端应答的超时（毫秒）
         */
        private int uploadWindow = 8;
        private long uploadAckTimeoutMillis = 3000;

        /**
         * RSA 消息体加密（0x0A00 / 0x8A00）。平台密钥对第一次启动时生成，保存在数据目录下，重启不变
         */
        private boolean rsaEnabled = false;

        /**
         * 拿到终端公钥后，平台下发的消息是否加密
         */
        private boolean rsaEncryptDownlink = true;

        public int getAuthTimeoutSeconds() {
            return authTimeoutSeconds;
        }

        public void setAuthTimeoutSeconds(int authTimeoutSeconds) {
            this.authTimeoutSeconds = authTimeoutSeconds;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getMaxConnectionsPerIp() {
            return maxConnectionsPerIp;
        }

        public void setMaxConnectionsPerIp(int maxConnectionsPerIp) {
            this.maxConnectionsPerIp = maxConnectionsPerIp;
        }

        public int getUploadWindow() {
            return uploadWindow;
        }

        public void setUploadWindow(int uploadWindow) {
            this.uploadWindow = uploadWindow;
        }

        public long getUploadAckTimeoutMillis() {
            return uploadAckTimeoutMillis;
        }

        public void setUploadAckTimeoutMillis(long uploadAckTimeoutMillis) {
            this.uploadAckTimeoutMillis = uploadAckTimeoutMillis;
        }

        public boolean isRsaEnabled() {
            return rsaEnabled;
        }

        public void setRsaEnabled(boolean rsaEnabled) {
            this.rsaEnabled = rsaEnabled;
        }

        public boolean isRsaEncryptDownlink() {
            return rsaEncryptDownlink;
        }

        public void setRsaEncryptDownlink(boolean rsaEncryptDownlink) {
            this.rsaEncryptDownlink = rsaEncryptDownlink;
        }

        public boolean isAutoRegister() {
            return autoRegister;
        }

        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }

        public String getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getIdleSeconds() {
            return idleSeconds;
        }

        public void setIdleSeconds(int idleSeconds) {
            this.idleSeconds = idleSeconds;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getCommandTimeoutSeconds() {
            return commandTimeoutSeconds;
        }

        public void setCommandTimeoutSeconds(int commandTimeoutSeconds) {
            this.commandTimeoutSeconds = commandTimeoutSeconds;
        }
    }
}
