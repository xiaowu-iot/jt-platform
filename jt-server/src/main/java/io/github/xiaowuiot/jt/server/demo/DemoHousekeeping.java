package io.github.xiaowuiot.jt.server.demo;

import io.github.xiaowuiot.jt.server.alarm.Alarm;
import io.github.xiaowuiot.jt.server.alarm.AlarmRepository;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示环境：模拟值班人员处理报警。超过 20 分钟没人处理的报警，由"值班调度"处理掉，
 * 演示站的待处理报警数才像一个有人在值班的平台，不会一直往上涨。
 */
@Component
@Roles.On(Roles.PLATFORM)
public class DemoHousekeeping {

    private static final String[] REMARKS = {"已电话提醒司机", "已联系车队长核实", "司机已停车检查", "已通知维修", "误报"};

    private final AlarmRepository alarms;
    private final JtProperties props;

    public DemoHousekeeping(AlarmRepository alarms, JtProperties props) {
        this.alarms = alarms;
        this.props = props;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void handleOldAlarms() {
        if (!props.getDemo().isEnabled() || !props.getDemo().isSimulator()) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusMinutes(20);
        Specification<Alarm> spec = (root, q, cb) -> cb.and(cb.equal(root.get("status"), Alarm.Status.PENDING),
                cb.lessThan(root.get("startTime"), before));
        List<Alarm> list = alarms.findAll(spec, PageRequest.of(0, 1000, Sort.by("id"))).getContent();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Alarm a : list) {
            int i = r.nextInt(REMARKS.length);
            // 处理时间按报警后几分钟算，平均处理用时这类统计才像真的
            LocalDateTime t = a.getStartTime().plusMinutes(2 + r.nextInt(14));
            a.handle(i == REMARKS.length - 1 ? Alarm.Status.IGNORED : Alarm.Status.HANDLED, "值班调度", REMARKS[i],
                    t.isAfter(LocalDateTime.now()) ? LocalDateTime.now() : t);
        }
        alarms.saveAll(list);
    }
}
