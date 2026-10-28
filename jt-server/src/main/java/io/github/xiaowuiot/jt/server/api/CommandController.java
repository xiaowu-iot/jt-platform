package io.github.xiaowuiot.jt.server.api;

import io.github.xiaowuiot.protocol.codec.util.HexUtils;
import io.github.xiaowuiot.protocol.jt808.Jt808Message;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.DriverInfo;
import io.github.xiaowuiot.protocol.jt808.body.GeneralResponse;
import io.github.xiaowuiot.protocol.jt808.body.InfoBodies;
import io.github.xiaowuiot.protocol.jt808.body.LocationQueryResponse;
import io.github.xiaowuiot.protocol.jt808.body.Multimedia;
import io.github.xiaowuiot.protocol.jt808.body.PassThrough;
import io.github.xiaowuiot.protocol.jt808.body.PhoneBodies;
import io.github.xiaowuiot.protocol.jt808.body.Recorder;
import io.github.xiaowuiot.protocol.jt808.body.SimpleBodies;
import io.github.xiaowuiot.protocol.jt808.body.TerminalAttributes;
import io.github.xiaowuiot.protocol.jt808.body.TerminalControl;
import io.github.xiaowuiot.protocol.jt808.body.TerminalParams;
import io.github.xiaowuiot.protocol.jt808.body.TextMessage;
import io.github.xiaowuiot.protocol.jt808.server.Jt808CommandSender;
import io.github.xiaowuiot.jt.server.command.CommandService;
import io.github.xiaowuiot.jt.server.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import io.github.xiaowuiot.jt.server.view.LocationView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 808 指令下发。接口是异步的：等终端应答期间不占 Tomcat 线程。
 * <pre>
 * GET  /api/devices/13800138000/params              查询终端参数 0x8104
 * PUT  /api/devices/13800138000/params              设置终端参数 0x8103，body：{"0x0001": 30}
 * GET  /api/devices/13800138000/params/select?ids=0x0001,0x0029  查询指定参数 0x8106
 * GET  /api/devices/13800138000/attributes          查询终端属性 0x8107
 * GET  /api/devices/13800138000/location            位置查询 0x8201
 * POST /api/devices/13800138000/tracking            临时位置跟踪 0x8202，body：{"interval": 5, "valid": 600}
 * POST /api/devices/13800138000/alarm-confirm       人工确认报警 0x8203，body：{"serialNo": 0, "types": 1}
 * POST /api/devices/13800138000/link-check          链路检测 0x8204（2019 版）
 * POST /api/devices/13800138000/text                文本下发 0x8300，body：{"text": "请注意安全"}
 * POST /api/devices/13800138000/call-back           电话回拨 0x8400，body：{"phone": "13900001111", "listen": true}
 * POST /api/devices/13800138000/vehicle-control     车辆控制 0x8500，body：{"lock": true}
 * POST /api/devices/13800138000/control             终端控制 0x8105，body：{"command": 4}
 * POST /api/devices/13800138000/camera              立即拍照 0x8801，body：{"channel": 1, "photos": 1}
 * POST /api/devices/13800138000/recorder            行驶记录仪采集 0x8700，body：{"cmd": 2}
 * POST /api/devices/13800138000/driver-info         上报驾驶员身份 0x8702
 * POST /api/devices/13800138000/passthrough         下行透传 0x8900，body：{"type": 65, "hex": "540D0A"}
 * </pre>
 * 都可以带 {@code ?timeout=秒} 指定等待应答的时间。终端不在线返回 404，超时 504，发送失败 502。
 */
@RestController
@RequestMapping("/api/devices/{deviceId}")
@PreAuthorize("hasAuthority('PERM_COMMAND_SEND')")
@Roles.On(Roles.PLATFORM)
public class CommandController {

    private final CommandService commands;
    private final CurrentUser current;
    private final int defaultTimeout;
    private final DataScope scope;
    private final VehicleCache vehicles;

    public CommandController(CommandService commands, CurrentUser current, JtProperties props, DataScope scope,
                             VehicleCache vehicles) {
        this.scope = scope;
        this.vehicles = vehicles;
        this.commands = commands;
        this.current = current;
        this.defaultTimeout = props.getAccess().getCommandTimeoutSeconds();
    }

    @GetMapping("/params")
    public CompletableFuture<ResponseEntity<Object>> queryParams(@PathVariable String deviceId,
                                                                 @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.QUERY_PARAMS, null, timeout, msg -> paramsView(msg.getBody()));
    }

    @PutMapping("/params")
    public CompletableFuture<ResponseEntity<Object>> setParams(@PathVariable String deviceId,
                                                               @RequestBody Map<String, Object> params,
                                                               @RequestParam(required = false) Integer timeout) {
        Map<Long, Object> p = new LinkedHashMap<>();
        params.forEach((k, v) -> p.put(Long.decode(k), v instanceof Number n ? n.longValue() : v));
        return call(deviceId, MessageId.SET_PARAMS, new TerminalParams(p), timeout, CommandController::generalResult);
    }

    @GetMapping("/params/select")
    public CompletableFuture<ResponseEntity<Object>> queryParamsById(@PathVariable String deviceId,
                                                                     @RequestParam String ids,
                                                                     @RequestParam(required = false) Integer timeout) {
        List<Long> list = new ArrayList<>();
        for (String s : ids.split(",")) {
            list.add(Long.decode(s.trim()));
        }
        return call(deviceId, MessageId.QUERY_PARAMS_BY_ID, new SimpleBodies.QueryParamsById(list), timeout,
                msg -> paramsView(msg.getBody()));
    }

    @GetMapping("/attributes")
    public CompletableFuture<ResponseEntity<Object>> attributes(@PathVariable String deviceId,
                                                                @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.QUERY_ATTRIBUTES, null, timeout, msg -> msg.<TerminalAttributes>getBody());
    }

    @GetMapping("/location")
    public CompletableFuture<ResponseEntity<Object>> location(@PathVariable String deviceId,
                                                              @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.LOCATION_QUERY, null, timeout,
                msg -> LocationView.of(msg.<LocationQueryResponse>getBody().getLocation()));
    }

    @PostMapping("/tracking")
    public CompletableFuture<ResponseEntity<Object>> tracking(@PathVariable String deviceId,
                                                              @RequestBody Map<String, Number> body,
                                                              @RequestParam(required = false) Integer timeout) {
        SimpleBodies.TrackingControl t = new SimpleBodies.TrackingControl(body.get("interval").intValue(),
                body.getOrDefault("valid", 0).longValue());
        return call(deviceId, MessageId.TRACKING_CONTROL, t, timeout, CommandController::generalResult);
    }

    @PostMapping("/alarm-confirm")
    public CompletableFuture<ResponseEntity<Object>> alarmConfirm(@PathVariable String deviceId,
                                                                  @RequestBody Map<String, Number> body,
                                                                  @RequestParam(required = false) Integer timeout) {
        SimpleBodies.AlarmConfirm c = new SimpleBodies.AlarmConfirm(body.getOrDefault("serialNo", 0).intValue(),
                body.get("types").longValue());
        return call(deviceId, MessageId.ALARM_CONFIRM, c, timeout, CommandController::generalResult);
    }

    @PostMapping("/link-check")
    public CompletableFuture<ResponseEntity<Object>> linkCheck(@PathVariable String deviceId,
                                                               @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.LINK_CHECK, null, timeout, CommandController::generalResult);
    }

    @PostMapping("/text")
    public CompletableFuture<ResponseEntity<Object>> text(@PathVariable String deviceId,
                                                          @RequestBody Map<String, Object> body,
                                                          @RequestParam(required = false) Integer timeout) {
        int flag = TextMessage.FLAG_DISPLAY | TextMessage.FLAG_TTS
                | (Boolean.TRUE.equals(body.get("urgent")) ? TextMessage.FLAG_URGENT : 0);
        return call(deviceId, MessageId.TEXT_MESSAGE, new TextMessage(flag, (String) body.get("text")), timeout,
                CommandController::generalResult);
    }

    @PostMapping("/call-back")
    public CompletableFuture<ResponseEntity<Object>> callBack(@PathVariable String deviceId,
                                                              @RequestBody Map<String, Object> body,
                                                              @RequestParam(required = false) Integer timeout) {
        int flag = Boolean.TRUE.equals(body.get("listen")) ? PhoneBodies.CallBack.LISTEN : PhoneBodies.CallBack.NORMAL;
        return call(deviceId, MessageId.CALL_BACK, new PhoneBodies.CallBack(flag, (String) body.get("phone")), timeout,
                CommandController::generalResult);
    }

    @PostMapping("/vehicle-control")
    public CompletableFuture<ResponseEntity<Object>> vehicleControl(@PathVariable String deviceId,
                                                                    @RequestBody Map<String, Boolean> body,
                                                                    @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.VEHICLE_CONTROL, new InfoBodies.VehicleControl(body.get("lock")), timeout,
                msg -> {
                    InfoBodies.VehicleControlResponse r = msg.getBody();
                    // 车门是否真的锁上，看应答里位置的状态位 bit12
                    return Map.of("doorLocked", (r.location.getStatus() >> 12 & 1) == 1,
                            "location", LocationView.of(r.location));
                });
    }

    @PostMapping("/control")
    public CompletableFuture<ResponseEntity<Object>> control(@PathVariable String deviceId,
                                                             @RequestBody Map<String, Object> body,
                                                             @RequestParam(required = false) Integer timeout) {
        TerminalControl c = new TerminalControl(((Number) body.get("command")).intValue(), (String) body.get("params"));
        return call(deviceId, MessageId.TERMINAL_CONTROL, c, timeout, CommandController::generalResult);
    }

    @PostMapping("/camera")
    public CompletableFuture<ResponseEntity<Object>> camera(@PathVariable String deviceId,
                                                            @RequestBody Map<String, Number> body,
                                                            @RequestParam(required = false) Integer timeout) {
        Multimedia.CameraCommand cmd = new Multimedia.CameraCommand(body.getOrDefault("channel", 1).intValue(),
                body.getOrDefault("photos", 1).intValue(), body.getOrDefault("interval", 0).intValue());
        return call(deviceId, MessageId.CAMERA_COMMAND, cmd, timeout, msg -> {
            Multimedia.CameraResponse r = msg.getBody();
            return Map.of("result", r.getResult(), "mediaIds", r.getMediaIds());
        });
    }

    @PostMapping("/driver-info")
    public CompletableFuture<ResponseEntity<Object>> driverInfo(@PathVariable String deviceId,
                                                                @RequestParam(required = false) Integer timeout) {
        return call(deviceId, MessageId.DRIVER_INFO_REQUEST, null, timeout, msg -> msg.<DriverInfo>getBody());
    }

    @PostMapping("/passthrough")
    public CompletableFuture<ResponseEntity<Object>> passThrough(@PathVariable String deviceId,
                                                                 @RequestBody Map<String, Object> body,
                                                                 @RequestParam(required = false) Integer timeout) {
        int type = ((Number) body.get("type")).intValue();
        byte[] content = body.get("hex") != null ? HexUtils.fromHex((String) body.get("hex"))
                : ((String) body.get("text")).getBytes(StandardCharsets.US_ASCII);
        return call(deviceId, MessageId.PASS_THROUGH_DOWN, new PassThrough(type, content), timeout,
                CommandController::generalResult);
    }

    private static Map<String, Object> paramsView(TerminalParams p) {
        Map<String, Object> view = new LinkedHashMap<>();
        p.getParams().forEach((k, v) -> view.put(String.format("0x%04X", k), v));
        return view;
    }

    private static Object generalResult(Jt808Message msg) {
        GeneralResponse r = msg.getBody();
        return Map.of("result", r.getResult(), "success", r.isSuccess());
    }

    private CompletableFuture<ResponseEntity<Object>> call(String deviceId, int msgId, Object body, Integer timeout,
                                                           Function<Jt808Message, Object> mapper) {
        // 只能给自己能看到的车队里的车下发指令（不限车队的账号不受影响）
        java.util.Set<Long> visible = scope.fleetIds();
        if (visible != null) {
            VehicleInfo v = vehicles.byDevice(deviceId);
            if (v == null || !visible.contains(v.fleetId())) {
                return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "只能给自己车队的车下发指令")));
            }
        }
        int t = timeout == null ? defaultTimeout : timeout;
        String params = body == null ? "" : String.valueOf(body);
        return commands.send(deviceId, msgId, body, t, params, current.get().getName())
                .thenApply(msg -> ResponseEntity.ok(mapper.apply(msg)))
                .exceptionally(e -> {
                    Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                    HttpStatus status = cause instanceof Jt808CommandSender.DeviceOfflineException ? HttpStatus.NOT_FOUND
                            : cause instanceof TimeoutException ? HttpStatus.GATEWAY_TIMEOUT
                            : HttpStatus.BAD_GATEWAY;
                    return ResponseEntity.status(status).body(Map.of("error", String.valueOf(cause.getMessage())));
                });
    }
}
