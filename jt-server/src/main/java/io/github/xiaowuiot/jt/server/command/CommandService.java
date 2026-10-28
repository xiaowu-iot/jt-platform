package io.github.xiaowuiot.jt.server.command;

import io.github.xiaowuiot.protocol.jt808.Jt808Message;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.GeneralResponse;
import io.github.xiaowuiot.protocol.jt808.server.Jt808CommandSender;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * 平台下发指令统一走这里：先记一条下发记录，终端应答（或超时）后补上结果。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class CommandService {

    private final CommandGateway gateway;
    private final CommandRecordRepository records;
    private final VehicleCache vehicles;

    public CommandService(CommandGateway gateway, CommandRecordRepository records, VehicleCache vehicles) {
        this.gateway = gateway;
        this.records = records;
        this.vehicles = vehicles;
    }

    /**
     * @param params   给人看的参数说明，记到下发记录里
     * @param operator 操作人，系统自动下发的写"系统"
     */
    public CompletableFuture<Jt808Message> send(String deviceId, int msgId, Object body, int timeoutSeconds,
                                                String params, String operator) {
        VehicleInfo v = vehicles.byDevice(deviceId);
        CommandRecord r = records.save(new CommandRecord(v == null ? null : v.id(), v == null ? null : v.plate(), deviceId,
                msgId, MessageId.name(msgId), params, operator));
        long start = System.currentTimeMillis();
        return gateway.send(deviceId, msgId, body, timeoutSeconds).whenComplete((msg, e) -> {
            long cost = System.currentTimeMillis() - start;
            if (e == null) {
                Object b = msg.getBody();
                if (b instanceof GeneralResponse g && !g.isSuccess()) {
                    r.finish(CommandRecord.Status.FAILED, "终端应答失败，结果码 " + g.getResult(), cost);
                } else {
                    r.finish(CommandRecord.Status.SUCCESS, MessageId.name(msg.getMsgId()) + "：" + b, cost);
                }
            } else {
                Throwable c = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                CommandRecord.Status s = c instanceof Jt808CommandSender.DeviceOfflineException ? CommandRecord.Status.OFFLINE
                        : c instanceof TimeoutException ? CommandRecord.Status.TIMEOUT : CommandRecord.Status.FAILED;
                r.finish(s, c.getMessage(), cost);
            }
            records.save(r);
        });
    }

    public boolean isOnline(String deviceId) {
        return gateway.isOnline(deviceId);
    }
}
