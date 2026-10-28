package io.github.xiaowuiot.jt.simulator.jt808;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.protocol.jt808.Jt808Header;
import io.github.xiaowuiot.protocol.jt808.Jt808Message;
import io.github.xiaowuiot.protocol.jt808.Jt808Version;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.AreaAlarmInfo;
import io.github.xiaowuiot.protocol.jt808.body.AreaQuery;
import io.github.xiaowuiot.protocol.jt808.body.Auth;
import io.github.xiaowuiot.protocol.jt808.body.CircleArea;
import io.github.xiaowuiot.protocol.jt808.body.DriverInfo;
import io.github.xiaowuiot.protocol.jt808.body.GeneralResponse;
import io.github.xiaowuiot.protocol.jt808.body.InfoBodies;
import io.github.xiaowuiot.protocol.jt808.body.Jt808Body;
import io.github.xiaowuiot.protocol.jt808.body.LinkBodies;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.protocol.jt808.body.LocationBatch;
import io.github.xiaowuiot.protocol.jt808.body.LocationQueryResponse;
import io.github.xiaowuiot.protocol.jt808.body.Multimedia;
import io.github.xiaowuiot.protocol.jt808.body.PassThrough;
import io.github.xiaowuiot.protocol.jt808.body.PolygonArea;
import io.github.xiaowuiot.protocol.jt808.body.Recorder;
import io.github.xiaowuiot.protocol.jt808.body.RectangleArea;
import io.github.xiaowuiot.protocol.jt808.body.Register;
import io.github.xiaowuiot.protocol.jt808.body.RegisterResponse;
import io.github.xiaowuiot.protocol.jt808.body.Route;
import io.github.xiaowuiot.protocol.jt808.body.SimpleBodies;
import io.github.xiaowuiot.protocol.jt808.body.StoredMultimedia;
import io.github.xiaowuiot.protocol.jt808.body.TerminalAttributes;
import io.github.xiaowuiot.protocol.jt808.body.TerminalControl;
import io.github.xiaowuiot.protocol.jt808.body.TerminalParams;
import io.github.xiaowuiot.protocol.jt808.body.TextMessage;
import io.github.xiaowuiot.protocol.jt808.body.UpgradePackage;
import io.github.xiaowuiot.protocol.jt808.body.UpgradeResult;
import io.github.xiaowuiot.protocol.jt808.codec.Jt808Codecs;
import io.github.xiaowuiot.protocol.jt808.codec.Jt808Subpackages;
import io.github.xiaowuiot.protocol.jt808.sensor.Sensors;
import io.github.xiaowuiot.jt.simulator.SimulatorStats;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 一台模拟终端：注册 → 鉴权 → 定时心跳和位置上报，并应答平台下发的指令。
 * <p>
 * 所有状态只在这条连接的 IO 线程里读写（定时任务也提交到同一个 EventLoop），所以不需要加锁。
 * <p>
 * 断线重连时同一个实例会被加到新连接的流水线上（同一时间只属于一条连接），所以标记为 Sharable。
 */
@ChannelHandler.Sharable
public class Jt808Terminal extends SimpleChannelInboundHandler<Jt808Message> {

    private static final Logger log = LoggerFactory.getLogger(Jt808Terminal.class);

    /**
     * 808 规定时间用 GMT+8，不能用本机时区：服务器、模拟器不一定部署在东八区
     */
    private static final ZoneOffset GMT8 = ZoneOffset.ofHours(8);

    private final String phone;
    private final Jt808Version version;
    private final Bootstrap bootstrap;
    /**
     * 平台地址可以有多个：连接失败时换下一个，模拟负载均衡后面某个节点挂掉、终端重连到其他节点
     */
    private final List<InetSocketAddress> remotes;
    private int remoteIndex;
    private final SocketAddress local;
    private final SimulatorStats stats;
    private final Track track;
    private final boolean verbose;

    private final Map<Long, Object> params = new LinkedHashMap<>();
    private final Map<Integer, Long> sendTimes = new HashMap<>();

    private Channel channel;
    private String authCode;
    private int serialNo;
    private boolean online;
    private volatile boolean stopped;
    private String firmwareVersion = "V1.0.0";
    /** SIM 卡 ICCID，查询终端属性时上报；不设就按手机号拼一个 */
    private String iccid;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> locationTask;
    private int muteAfter;
    // 需要平台确认才清除的报警（比如紧急报警），一直带在位置里，直到收到 0x8203
    private long latchedAlarm;
    private long mediaSeq = 1000;
    private boolean followRoute;
    private int deviateAfter;
    private boolean doorLocked;
    private int driverCardAfter;
    private boolean driverOnDuty;
    private final Map<Integer, String> events = new LinkedHashMap<>();
    private int blindAfter;
    private int blindSeconds;
    private volatile long blindUntil;
    private final List<Location> blindBuffer = new ArrayList<>();
    private int emergencyAfter;
    private ScheduledFuture<?> trackingRestore;
    private boolean randomAlarms;
    private final Set<Integer> sensors = new LinkedHashSet<>();
    private ScheduledFuture<?> sensorTask;
    private int sensorPeriod = 60;
    private int tirePuncture;
    private String driverName = "张三";
    private String driverLicense = "440300199001011234";
    private String driverCert = "440300000000000001";
    private long transientAlarm;
    private long transientUntil;

    public Jt808Terminal(String phone, Jt808Version version, Bootstrap bootstrap, List<InetSocketAddress> remotes,
                         InetSocketAddress local, int heartbeatSeconds, int reportSeconds,
                         SimulatorStats stats, Track track, boolean verbose) {
        this.phone = phone;
        this.version = version;
        this.bootstrap = bootstrap;
        this.remotes = remotes;
        InetSocketAddress remote = remotes.get(0);
        this.local = local;
        this.stats = stats;
        this.track = track;
        this.verbose = verbose;
        params.put(TerminalParams.HEARTBEAT_INTERVAL, (long) heartbeatSeconds);
        params.put(TerminalParams.TCP_RESPONSE_TIMEOUT, 10L);
        params.put(TerminalParams.TCP_RETRANSMIT_TIMES, 3L);
        params.put(TerminalParams.MAIN_SERVER_ADDRESS, remote.getHostString());
        params.put(TerminalParams.SERVER_TCP_PORT, (long) remote.getPort());
        params.put(TerminalParams.DEFAULT_REPORT_INTERVAL, (long) reportSeconds);
        params.put(TerminalParams.PLATE, "粤B" + phone.substring(phone.length() - 5));
    }

    // ==================== 连接 ====================

    public void connect() {
        if (stopped) {
            return;
        }
        Bootstrap b = bootstrap.clone().handler(new Jt808ClientInitializer(this));
        InetSocketAddress remote = remotes.get(remoteIndex % remotes.size());
        ChannelFuture f = local == null ? b.connect(remote) : b.connect(remote, local);
        f.addListener((ChannelFuture cf) -> {
            if (!cf.isSuccess()) {
                stats.connectFailed(cf.cause());
                remoteIndex++;
                log.debug("{} 连接失败：{}", phone, cf.cause().toString());
                reconnectLater(cf.channel());
            }
        });
    }

    public void stop() {
        stopped = true;
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 断线重连要加随机延迟：平台重启后，几万台终端如果同时重连，会在同一秒把平台再次打满
     */
    private void reconnectLater(Channel ch) {
        if (stopped) {
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(1000, 5000);
        ch.eventLoop().schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel = ctx.channel();
        if (version == Jt808Version.V2011) {
            // 平台下发的消息头同样看不出 2011 还是 2013，解码时要知道自己是 2011 版
            Jt808Codecs.setVersion(channel, Jt808Version.V2011);
        }
        stats.connected.incrementAndGet();
        if (authCode == null) {
            send(MessageId.REGISTER, new Register().setProvince(44).setCity(300).setMakerId("SIMU1")
                    .setModel("SIM-808").setTerminalId(terminalId())
                    .setPlateColor(2).setPlate((String) params.get(TerminalParams.PLATE)));
        } else {
            sendAuth();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        stats.connected.decrementAndGet();
        stats.disconnects.increment();
        if (online) {
            online = false;
            stats.online.decrementAndGet();
        }
        cancelTimers();
        sendTimes.clear();
        reconnectLater(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("{} 连接异常：{}", phone, cause.toString());
        ctx.close();
    }

    // ==================== 收到平台的消息 ====================

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Jt808Message msg) {
        if (verbose) {
            log.info("{} 收到 {} {}", phone, MessageId.name(msg.getMsgId()), msg);
        }
        Jt808Header h = msg.getHeader();
        if (msg.isFragment()) {
            // 平台分包下发（比如升级包），每一包都要应答
            reply(h, GeneralResponse.SUCCESS);
            return;
        }
        if (h.getMsgId() >= 0x8000 && h.getMsgId() != MessageId.PLATFORM_GENERAL_RESPONSE
                && h.getMsgId() != MessageId.REGISTER_RESPONSE && h.getMsgId() != MessageId.SERVER_TIME_RESPONSE
                && h.getMsgId() != MessageId.MULTIMEDIA_RESPONSE) {
            stats.commands.increment();
        }
        switch (h.getMsgId()) {
            case MessageId.REGISTER_RESPONSE:
                onRegisterResponse(msg.getBody());
                break;
            case MessageId.PLATFORM_GENERAL_RESPONSE:
                onGeneralResponse(msg.getBody());
                break;
            case MessageId.SERVER_TIME_RESPONSE:
                log.info("{} 平台时间（UTC）{}", phone, msg.<LinkBodies.ServerTime>getBody().getUtc());
                break;
            case MessageId.QUERY_PARAMS:
                send(MessageId.QUERY_PARAMS_RESPONSE, new TerminalParams(h.getSerialNo(), new LinkedHashMap<>(params)));
                break;
            case MessageId.SET_PARAMS:
                onSetParams(msg.getBody());
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.LOCATION_QUERY:
                send(MessageId.LOCATION_QUERY_RESPONSE, new LocationQueryResponse(h.getSerialNo(), currentLocation()));
                break;
            case MessageId.TEXT_MESSAGE:
                log.info("{} 收到文本：{}", phone, msg.<TextMessage>getBody().getText());
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.TERMINAL_CONTROL:
                onControl(h, msg.getBody());
                break;
            case MessageId.UPGRADE_PACKAGE:
                onUpgrade(h, msg.getBody());
                break;
            case MessageId.QUERY_PARAMS_BY_ID: {
                Map<Long, Object> selected = new LinkedHashMap<>();
                for (Long id : msg.<SimpleBodies.QueryParamsById>getBody().getIds()) {
                    if (params.containsKey(id)) {
                        selected.put(id, params.get(id));
                    }
                }
                send(MessageId.QUERY_PARAMS_RESPONSE, new TerminalParams(h.getSerialNo(), selected));
                break;
            }
            case MessageId.QUERY_ATTRIBUTES:
                send(MessageId.ATTRIBUTES_RESPONSE, new TerminalAttributes().setTerminalType(0b100)
                        .setMakerId("SIMU1").setModel("SIM-808").setTerminalId(terminalId())
                        .setIccid(iccid != null ? iccid : "898600" + String.format("%14s", phone).replace(' ', '0'))
                        .setHardwareVersion("H1.0").setFirmwareVersion(firmwareVersion)
                        .setGnssAttr(0b11).setCommAttr(1 << 5));
                break;
            case MessageId.TRACKING_CONTROL:
                reply(h, GeneralResponse.SUCCESS);
                onTracking(msg.getBody());
                break;
            case MessageId.ALARM_CONFIRM: {
                SimpleBodies.AlarmConfirm c = msg.getBody();
                latchedAlarm &= ~(c.getTypes() & Jt808Flags.CONFIRM_REQUIRED);
                log.info("{} 平台确认报警 {}，清除后剩余报警 {}", phone, Jt808Flags.alarmNames(c.getTypes()),
                        Jt808Flags.alarmNames(latchedAlarm));
                reply(h, GeneralResponse.SUCCESS);
                // 报警状态变了，立即上报一条位置
                send(MessageId.LOCATION, reportLocation());
                break;
            }
            case MessageId.CAMERA_COMMAND:
                onCamera(h, msg.getBody());
                break;
            case MessageId.MULTIMEDIA_RESPONSE:
                log.info("{} 平台确认收到多媒体 {}", phone, msg.<Multimedia.DataResponse>getBody());
                break;
            case MessageId.SET_CIRCLE_AREA:
            case MessageId.SET_RECTANGLE_AREA:
            case MessageId.SET_POLYGON_AREA:
            case MessageId.DELETE_CIRCLE_AREA:
            case MessageId.DELETE_RECTANGLE_AREA:
            case MessageId.DELETE_POLYGON_AREA:
            case MessageId.DELETE_ROUTE:
                // 基础版模拟终端只应答，不在终端侧判断围栏
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.SET_ROUTE:
                onSetRoute(msg.getBody());
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.AREA_QUERY:
                // 模拟器不回传围栏明细，只告诉平台查到 0 个
                send(MessageId.AREA_QUERY_RESPONSE, new AreaQuery.Response(
                        msg.<AreaQuery.Request>getBody().getType(), 0, new byte[0]));
                break;
            case MessageId.DRIVER_INFO_REQUEST:
                // 0x8702 的应答就是 0x0702，不回通用应答
                send(MessageId.DRIVER_INFO, driverInfo());
                break;
            case MessageId.EVENT_SETTING:
                onEventSetting(msg.getBody());
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.QUESTION:
                onQuestion(h, msg.getBody());
                break;
            case MessageId.VEHICLE_CONTROL: {
                InfoBodies.VehicleControl c = msg.getBody();
                doorLocked = c.isLock();
                log.info("{} {}", phone, c);
                // 应答 0x0500 带一整条位置，状态位 bit12 反映车门是否真的锁上了
                send(MessageId.VEHICLE_CONTROL_RESPONSE, new InfoBodies.VehicleControlResponse(h.getSerialNo(), currentLocation()));
                break;
            }
            case MessageId.STORED_MEDIA_QUERY:
                // 终端里没有存储的多媒体
                send(MessageId.STORED_MEDIA_QUERY_RESPONSE,
                        new StoredMultimedia.QueryResponse(h.getSerialNo(), Collections.emptyList()));
                break;
            case MessageId.PASS_THROUGH_DOWN:
                log.info("{} 收到透传 {}", phone, msg.<PassThrough>getBody());
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.LINK_CHECK:
            case MessageId.CALL_BACK:
            case MessageId.PHONE_BOOK:
            case MessageId.INFO_MENU_SETTING:
            case MessageId.INFO_SERVICE:
            case MessageId.RECORDER_PARAMS:
            case MessageId.STORED_MEDIA_UPLOAD:
            case MessageId.STORED_MEDIA_UPLOAD_ONE:
            case MessageId.RECORD_AUDIO:
            case MessageId.PLATFORM_RESEND_REQUEST:
                reply(h, GeneralResponse.SUCCESS);
                break;
            case MessageId.PLATFORM_RSA_KEY:
                log.info("{} 收到 {}：{}", phone, MessageId.name(h.getMsgId()), msg.getBody());
                reply(h, GeneralResponse.SUCCESS);
                break;
            default:
                reply(h, GeneralResponse.UNSUPPORTED);
        }
    }

    private void onRegisterResponse(RegisterResponse r) {
        if (r.getResult() != RegisterResponse.SUCCESS) {
            log.warn("{} 注册失败，结果 {}", phone, r.getResult());
            channel.close();
            return;
        }
        authCode = r.getAuthCode();
        sendAuth();
    }

    private void onGeneralResponse(GeneralResponse r) {
        Long sendTime = sendTimes.remove(r.getReplySerialNo());
        if (sendTime != null) {
            stats.responses.increment();
            stats.latencyMicros.add((System.nanoTime() - sendTime) / 1000);
        }
        if (r.getReplyMsgId() != MessageId.AUTH) {
            return;
        }
        if (!r.isSuccess()) {
            // 鉴权码失效（比如平台重启后丢了），重新注册
            log.info("{} 鉴权失败，重新注册", phone);
            authCode = null;
            channel.close();
            return;
        }
        online = true;
        stats.online.incrementAndGet();
        if (version == Jt808Version.V2019) {
            // 2019 版终端上线后可以先校一下时钟
            send(MessageId.QUERY_SERVER_TIME, null);
        }
        startTimers();
        if (muteAfter > 0) {
            channel.eventLoop().schedule(() -> {
                log.info("{} 进入静默：停止发送，保持连接", phone);
                cancelTimers();
            }, muteAfter, TimeUnit.SECONDS);
        }
    }

    private void onSetParams(TerminalParams p) {
        params.putAll(p.getParams());
        if (p.getParams().containsKey(TerminalParams.HEARTBEAT_INTERVAL)
                || p.getParams().containsKey(TerminalParams.DEFAULT_REPORT_INTERVAL)) {
            log.info("{} 参数已修改，重新安排上报：{}", phone, p);
            startTimers();
        }
    }

    private void onControl(Jt808Header h, TerminalControl c) {
        reply(h, GeneralResponse.SUCCESS);
        if (c.getCommand() == TerminalControl.RESET || c.getCommand() == TerminalControl.CLOSE_CONNECTION) {
            log.info("{} 收到控制指令 {}，断开重连", phone, c.getCommand());
            channel.eventLoop().schedule(() -> channel.close(), 200, TimeUnit.MILLISECONDS);
        }
    }

    private void onUpgrade(Jt808Header h, UpgradePackage up) {
        reply(h, GeneralResponse.SUCCESS);
        log.info("{} 收到升级包 {}，{} 字节，开始升级", phone, up.getVersion(), up.getData().length);
        // 模拟写入固件、重启的耗时
        channel.eventLoop().schedule(() -> {
            firmwareVersion = up.getVersion();
            send(MessageId.UPGRADE_RESULT, new UpgradeResult(up.getType(), UpgradeResult.SUCCESS));
            log.info("{} 升级完成，当前版本 {}", phone, firmwareVersion);
        }, 1, TimeUnit.SECONDS);
    }

    /**
     * 拍照：先回 0x0805 告诉平台多媒体 ID，再把照片按分包规则用 0x0801 传上去
     */
    private void onCamera(Jt808Header h, Multimedia.CameraCommand cmd) {
        long mediaId = mediaSeq++;
        send(MessageId.CAMERA_RESPONSE, new Multimedia.CameraResponse(h.getSerialNo(), Multimedia.CameraResponse.SUCCESS,
                Collections.singletonList(mediaId)));
        log.info("{} 通道 {} 拍照，多媒体 ID {}", phone, cmd.getChannel(), mediaId);
        channel.eventLoop().schedule(() -> {
            byte[] jpeg = Snapshot.jpeg((String) params.get(TerminalParams.PLATE), cmd.getChannel(), track.getSpeed(),
                    LocalDateTime.now(GMT8).withNano(0));
            Multimedia.Data data = new Multimedia.Data(mediaId, Multimedia.TYPE_IMAGE, Multimedia.FORMAT_JPEG,
                    Multimedia.EVENT_PLATFORM_COMMAND, cmd.getChannel(), currentLocation(), jpeg);
            int n = sendLarge(MessageId.MULTIMEDIA_DATA, data);
            log.info("{} 上传照片 {}，{} 字节，分 {} 包", phone, mediaId, jpeg.length, n);
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 临时跟踪：有效期内按新的间隔上报，到期后恢复原来的上报间隔；间隔为 0 表示立即停止临时跟踪
     */
    private void onTracking(SimpleBodies.TrackingControl t) {
        if (trackingRestore != null) {
            trackingRestore.cancel(false);
        }
        if (t.getIntervalSeconds() == 0) {
            log.info("{} 停止临时跟踪", phone);
            startTimers();
            return;
        }
        log.info("{} 临时跟踪：每 {} 秒上报，持续 {} 秒", phone, t.getIntervalSeconds(), t.getValidSeconds());
        locationTask.cancel(false);
        int interval = t.getIntervalSeconds();
        locationTask = channel.eventLoop().scheduleAtFixedRate(() -> {
            track.move(interval);
            send(MessageId.LOCATION, reportLocation());
        }, 0, interval, TimeUnit.SECONDS);
        trackingRestore = channel.eventLoop().schedule(() -> {
            log.info("{} 临时跟踪到期，恢复原来的上报间隔", phone);
            startTimers();
        }, t.getValidSeconds(), TimeUnit.SECONDS);
    }

    // ==================== 定时上报 ====================

    private void startTimers() {
        cancelTimers();
        int hb = ((Number) params.get(TerminalParams.HEARTBEAT_INTERVAL)).intValue();
        int report = ((Number) params.get(TerminalParams.DEFAULT_REPORT_INTERVAL)).intValue();
        // 首次延迟随机打散，避免所有终端在同一毫秒上报
        long jitter = ThreadLocalRandom.current().nextLong(report * 1000L);
        locationTask = channel.eventLoop().scheduleAtFixedRate(() -> {
            track.move(report);
            reportOrBuffer();
        }, jitter, report * 1000L, TimeUnit.MILLISECONDS);
        // 心跳也要打散：不打散的话，同一批上线的终端每隔一个心跳周期就一起发，形成尖峰
        long hbJitter = ThreadLocalRandom.current().nextLong(hb * 1000L);
        heartbeatTask = channel.eventLoop().scheduleAtFixedRate(() -> send(MessageId.HEARTBEAT, null),
                hb * 1000L + hbJitter, hb * 1000L, TimeUnit.MILLISECONDS);
        if (emergencyAfter > 0) {
            channel.eventLoop().schedule(() -> {
                log.info("{} 按下紧急报警按钮", phone);
                latchedAlarm |= 1;
                send(MessageId.LOCATION, reportLocation());
            }, emergencyAfter, TimeUnit.SECONDS);
            emergencyAfter = 0;
        }
        if (blindAfter > 0) {
            channel.eventLoop().schedule(() -> {
                blindUntil = System.currentTimeMillis() + blindSeconds * 1000L;
                log.info("{} 进入盲区（没有网络信号），{} 秒内的位置先存在终端里", phone, blindSeconds);
            }, blindAfter, TimeUnit.SECONDS);
            blindAfter = 0;
        }
        if (driverCardAfter > 0) {
            channel.eventLoop().schedule(this::insertDriverCard, driverCardAfter, TimeUnit.SECONDS);
            driverCardAfter = 0;
        }
        if (!sensors.isEmpty()) {
            sensorTask = channel.eventLoop().scheduleAtFixedRate(this::reportSensors,
                    ThreadLocalRandom.current().nextLong(5, sensorPeriod), sensorPeriod, TimeUnit.SECONDS);
        }
    }

    private void cancelTimers() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (locationTask != null) {
            locationTask.cancel(false);
        }
        if (sensorTask != null) {
            sensorTask.cancel(false);
        }
    }

    /**
     * 主动上报的 0x0200（定时上报、报警触发）：先做电子围栏判断。
     * 查询应答、控制应答里的位置只反映当前状态，不做围栏判断：否则"进入区域"这样的一次性事件会被应答消息"用掉"，
     * 平台按 0x0200 处理报警时就看不到了
     */
    private Location reportLocation() {
        if (randomAlarms) {
            maybeRaiseAlarm();
        }
        Location l = currentLocation();
        l.setAlarm(l.getAlarm() | latchedAlarm);
        return l;
    }

    private Location currentLocation() {
        long status = 0b11 | (doorLocked ? 1L << 12 : 0);   // ACC 开、已定位；bit12 车门加锁
        long alarm = (track.getSpeed() > 100 ? 0b10 : 0) | latchedAlarm;   // 超过 100 km/h 报超速
        if (System.currentTimeMillis() < transientUntil) {
            alarm |= transientAlarm;
        }
        return new Location().setAlarm(alarm).setStatus(status)
                .setLatitude(track.getLat()).setLongitude(track.getLng())
                .setAltitude(30).setSpeed(track.getSpeed()).setDirection(track.getDirection())
                .setTime(LocalDateTime.now(GMT8).withNano(0))
                .putExtra(Location.EXTRA_MILEAGE, Math.round(track.getMileageKm() * 10))
                .putExtra(Location.EXTRA_NETWORK_SIGNAL, 25L)
                .putExtra(Location.EXTRA_SATELLITES, 12L);
    }

    /**
     * 盲区（隧道、山区没有信号）里的位置先存在终端里，出了盲区用 0x0704（类型 1 盲区补报）批量补传
     */
    private void reportOrBuffer() {
        Location l = reportLocation();
        if (System.currentTimeMillis() < blindUntil) {
            blindBuffer.add(l);
            return;
        }
        if (!blindBuffer.isEmpty()) {
            log.info("{} 离开盲区，补传 {} 个位置", phone, blindBuffer.size());
            for (int i = 0; i < blindBuffer.size(); i += 10) {
                send(MessageId.LOCATION_BATCH, new LocationBatch(LocationBatch.TYPE_BLIND_AREA,
                        new ArrayList<>(blindBuffer.subList(i, Math.min(blindBuffer.size(), i + 10)))));
            }
            blindBuffer.clear();
        }
        send(MessageId.LOCATION, l);
    }

    /**
     * 演示用：偶尔产生一个持续几十秒的报警，让平台的报警列表、通知、大屏有数据可看
     */
    private void maybeRaiseAlarm() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        if (now < transientUntil) {
            return;
        }
        transientAlarm = 0;
        double p = r.nextDouble();
        if (p < 0.00015) {
            // 紧急报警：一直保持到平台人工确认
            latchedAlarm |= 1;
            log.info("{} 按下紧急报警按钮", phone);
            return;
        }
        if (p > 0.0025) {
            return;
        }
        int[] bits = {1, 1, 1, 2, 2, 14, 13, 5, 7, 29, 25, 19};
        int bit = bits[r.nextInt(bits.length)];
        transientAlarm = 1L << bit;
        transientUntil = now + r.nextLong(20_000, 120_000);
    }

    // ==================== 发送 ====================

    private void sendAuth() {
        if (version != Jt808Version.V2019) {
            send(MessageId.AUTH, new Auth(authCode));
            return;
        }
        // IMEI 固定 15 位，用手机号后 13 位拼一个
        String tail = phone.length() > 13 ? phone.substring(phone.length() - 13) : String.format("%13s", phone).replace(' ', '0');
        send(MessageId.AUTH, new Auth(authCode, "86" + tail, firmwareVersion));
    }

    private void reply(Jt808Header req, int result) {
        send(MessageId.TERMINAL_GENERAL_RESPONSE, new GeneralResponse(req.getSerialNo(), req.getMsgId(), result));
    }

    private void send(int msgId, Object body) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        int serial = serialNo++ & 0xFFFF;
        Jt808Header h = Jt808Header.of(version, phone, msgId, serial);
        if (msgId != MessageId.TERMINAL_GENERAL_RESPONSE && msgId != MessageId.QUERY_PARAMS_RESPONSE
                && msgId != MessageId.LOCATION_QUERY_RESPONSE) {
            sendTimes.put(serial, System.nanoTime());
        }
        stats.sent.increment();
        if (verbose) {
            log.info("{} 发送 {}", phone, MessageId.name(msgId));
        }
        channel.writeAndFlush(new Jt808Message(h, body));
    }

    // ==================== 路线、记录仪、驾驶员、提问、事件 ====================

    private void onSetRoute(Route r) {
        if (followRoute) {
            // 模拟司机按规定路线行驶：沿拐点开
            List<double[]> points = new ArrayList<>();
            for (Route.Point p : r.getPoints()) {
                points.add(new double[]{p.lat, p.lng});
            }
            track.follow(points);
            log.info("{} 开始沿路线 {} 行驶", phone, r.getId());
            if (deviateAfter > 0) {
                channel.eventLoop().schedule(() -> {
                    if (track.isFollowing()) {
                        log.info("{} 偏离路线（右转离开）", phone);
                        track.deviate();
                    }
                }, deviateAfter, TimeUnit.SECONDS);
            }
        }
    }

    private DriverInfo driverInfo() {
        LocalDateTime now = LocalDateTime.now(GMT8).withNano(0);
        if (!driverOnDuty) {
            return DriverInfo.cardOut(now);
        }
        return new DriverInfo(DriverInfo.CARD_IN, now, DriverInfo.READ_OK, driverName, driverCert,
                "深圳市交通运输局", LocalDate.of(2030, 6, 30), driverLicense);
    }

    /**
     * 每分钟上报一次外接传感器的数据（0x0900 透传）
     */
    private void reportSensors() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int type : sensors) {
            byte[] content;
            switch (type) {
                case Sensors.TYPE_TEMPERATURE: {
                    // 冷库车厢 -18℃ 上下；偶尔开门卸货，温度回升到 -12℃ 左右
                    double base = r.nextInt(100) < 4 ? -12 : -18;
                    content = Sensors.encodeTemperature(Arrays.asList(
                            new Sensors.Probe(1, Math.round((base + r.nextDouble(-1.2, 1.2)) * 10) / 10.0, 55 + r.nextInt(10)),
                            new Sensors.Probe(2, Math.round((base + 0.6 + r.nextDouble(-1.2, 1.2)) * 10) / 10.0, 55 + r.nextInt(10))));
                    break;
                }
                case Sensors.TYPE_LOAD: {
                    if (track != null && track.hasLoad()) {
                        // 收运车：每收一个点重一点，到中转站卸空
                        long kg = track.getLoadKg();
                        content = Sensors.encodeLoad(new Sensors.Load(kg, kg > 200 ? 2 : 0));
                        break;
                    }
                    // 渣土车：去消纳场的路上满载，回来空载；偶尔超载
                    boolean loaded = (System.currentTimeMillis() / 900_000 + phone.hashCode()) % 2 == 0;
                    long kg = loaded ? 24_000 + r.nextInt(6_000) : 300 + r.nextInt(500);
                    int status = !loaded ? 0 : kg > 29_000 ? 3 : 2;
                    content = Sensors.encodeLoad(new Sensors.Load(kg, status));
                    break;
                }
                case Sensors.TYPE_TIRE: {
                    List<Sensors.Tire> tires = new ArrayList<>();
                    for (int i = 1; i <= 6; i++) {
                        int kpa = 800 + r.nextInt(-30, 31);
                        int status = 0;
                        if (i == tirePuncture) {
                            kpa = 520 + r.nextInt(40);
                            status = 1;
                        }
                        tires.add(new Sensors.Tire(i, kpa, 32 + r.nextInt(12), status));
                    }
                    if (r.nextInt(400) == 0) {
                        // 偶尔有个轮胎慢漏气，持续一段时间
                        tirePuncture = 1 + r.nextInt(6);
                    } else if (tirePuncture > 0 && r.nextInt(30) == 0) {
                        tirePuncture = 0;
                    }
                    content = Sensors.encodeTires(tires);
                    break;
                }
                default:
                    continue;
            }
            send(MessageId.PASS_THROUGH_UP, new PassThrough(type, content));
        }
    }

    /**
     * 超过 1023 字节的消息分包发送
     *
     * @return 分了几包
     */
    private int sendLarge(int msgId, Jt808Body body) {
        if (channel == null || !channel.isActive()) {
            return 0;
        }
        Jt808Header template = Jt808Header.of(version, phone, msgId, 0);
        List<Jt808Message> packets = Jt808Subpackages.split(template, body, Jt808Subpackages.DEFAULT_PACKET_SIZE,
                () -> serialNo++ & 0xFFFF);
        for (Jt808Message p : packets) {
            stats.sent.increment();
            channel.write(p);
        }
        channel.flush();
        return packets.size();
    }

    private void insertDriverCard() {
        driverOnDuty = true;
        log.info("{} 驾驶员插卡上班", phone);
        send(MessageId.DRIVER_INFO, driverInfo());
    }

    private void onEventSetting(InfoBodies.EventSetting s) {
        switch (s.type) {
            case InfoBodies.EventSetting.DELETE_ALL:
                events.clear();
                break;
            case InfoBodies.EventSetting.UPDATE:
                events.clear();
                events.putAll(s.events);
                break;
            case InfoBodies.EventSetting.DELETE:
                s.events.keySet().forEach(events::remove);
                break;
            default:
                events.putAll(s.events);
        }
        log.info("{} 事件设置，当前事件 {}", phone, events);
        if (!events.isEmpty()) {
            // 模拟司机 5 秒后在屏幕上点了第一个事件
            int id = events.keySet().iterator().next();
            channel.eventLoop().schedule(() -> {
                log.info("{} 司机上报事件 {}：{}", phone, id, events.get(id));
                send(MessageId.EVENT_REPORT, new InfoBodies.EventReport(id));
            }, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * 提问：先回通用应答表示收到，司机选好答案后再用 0x0302 应答。这里模拟司机 3 秒后选了第一个答案
     */
    private void onQuestion(Jt808Header h, InfoBodies.Question q) {
        log.info("{} 收到提问：{} 候选答案 {}", phone, q.question, q.answers);
        reply(h, GeneralResponse.SUCCESS);
        int answer = q.answers.keySet().iterator().next();
        channel.eventLoop().schedule(() -> {
            log.info("{} 司机选择答案 {}：{}", phone, answer, q.answers.get(answer));
            send(MessageId.QUESTION_ANSWER, new InfoBodies.QuestionAnswer(h.getSerialNo(), answer));
        }, 3, TimeUnit.SECONDS);
    }

    private String terminalId() {
        return "T" + phone.substring(phone.length() - 6);
    }

    // ==================== 选项 ====================

    /**
     * 上线 seconds 秒后停止发送，但不断开连接。
     * <p>
     * 真实场景里终端断电、进隧道、4G 掉线时，不会发 TCP 断开报文，平台这边的连接还是"已连接"状态，
     * 只能靠"多久没收到数据"判断离线。本地直接关掉模拟器的话，操作系统会帮它发 FIN，测不出这种情况。
     */
    public void setMuteAfter(int seconds) {
        this.muteAfter = seconds;
    }

    /**
     * 上线 seconds 秒后"按下紧急报警按钮"，报警一直保持到平台用 0x8203 确认
     */
    public void setEmergencyAfter(int seconds) {
        this.emergencyAfter = seconds;
    }

    /**
     * SIM 卡 ICCID（查询终端属性时上报）
     */
    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    /**
     * 随机产生报警（超速、疲劳、碰撞预警、电源欠压……），平台演示用
     */
    public void setRandomAlarms(boolean on) {
        this.randomAlarms = on;
    }

    /**
     * 接哪些外接传感器，见 {@link Sensors}
     */
    /** 外接传感器多久报一次（秒），默认 60；收运车载重要细一点，到点判定才看得出前后的变化 */
    public void setSensorPeriod(int seconds) {
        this.sensorPeriod = Math.max(5, seconds);
    }

    public void setSensors(int... types) {
        sensors.clear();
        for (int t : types) {
            sensors.add(t);
        }
    }

    /**
     * 驾驶员：插卡上报 0x0702、记录仪 01H 都用它
     */
    public void setDriver(String name, String license) {
        this.driverName = name;
        this.driverLicense = license;
        this.driverCert = "QC" + license.substring(Math.max(0, license.length() - 16));
    }

    /**
     * @param followRoute  收到 0x8606 后沿路线行驶
     * @param deviateAfter 沿路线行驶多少秒后偏离，0 表示不偏离
     */
    public void setRouteOptions(boolean followRoute, int deviateAfter) {
        this.followRoute = followRoute;
        this.deviateAfter = deviateAfter;
    }

    /**
     * 上线多少秒后驾驶员插卡（0x0702 主动上报），0 表示不插卡
     */
    public void setDriverCardAfter(int seconds) {
        this.driverCardAfter = seconds;
    }

    /**
     * 上线 after 秒后进入盲区，持续 seconds 秒
     */
    public void setBlindArea(int after, int seconds) {
        this.blindAfter = after;
        this.blindSeconds = seconds;
    }

    public String getPhone() {
        return phone;
    }
}
