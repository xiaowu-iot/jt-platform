package io.github.xiaowuiot.jt.server.demo;

import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.device.Device;
import io.github.xiaowuiot.jt.server.device.DeviceRepository;
import io.github.xiaowuiot.jt.server.device.DeviceService;
import io.github.xiaowuiot.jt.server.device.DeviceType;
import io.github.xiaowuiot.jt.server.device.SimCard;
import io.github.xiaowuiot.jt.server.device.SimCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账的演示数据：给已装车的终端补上供应商、采购和保修日期，给 SIM 卡补上运营商、套餐、流量、到期时间；
 * 再放几台库存、维修、报废的设备和一台"待登记"的终端，各个功能都有东西可看。
 * <p>
 * 装车的设备由 {@link DeviceService#syncAll()} 从车辆档案同步过来，这里在它之后执行。
 */
@Component
@Roles.On(Roles.PLATFORM)
public class DemoDevices {

    private static final Logger log = LoggerFactory.getLogger(DemoDevices.class);
    private static final String[] SUPPLIERS = {"演示供应商甲", "演示供应商乙", "演示供应商丙"};
    private static final String[] OPERATORS = {"移动", "联通", "电信"};
    private static final int[] PLANS = {30, 50, 100};

    private final JtProperties props;
    private final DeviceService service;
    private final DeviceRepository devices;
    private final SimCardRepository sims;

    public DemoDevices(JtProperties props, DeviceService service, DeviceRepository devices, SimCardRepository sims) {
        this.props = props;
        this.service = service;
        this.devices = devices;
        this.sims = sims;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void seed() {
        if (!props.getDemo().isEnabled()) {
            return;
        }
        service.syncAll();
        if (devices.findAll().stream().anyMatch(d -> d.getSupplier() != null)) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<Device> all = devices.findAll();
        int i = 0;
        for (Device d : all) {
            i++;
            d.setSupplier(SUPPLIERS[i % SUPPLIERS.length]);
            d.setPurchaseDate(today.minusDays(100 + (i * 37L) % 700));
            // 有两台保修快到期了
            d.setWarrantyUntil(i % 23 == 0 ? today.plusDays(12) : d.getPurchaseDate().plusYears(3));
            devices.save(d);
        }
        int k = 0;
        for (SimCard s : sims.findAll()) {
            k++;
            s.setOperator(OPERATORS[k % 3]);
            s.setMsisdn(String.format("1440%09d", 100000000L + k * 7919L));
            s.setPlanMb(PLANS[k % 3]);
            // 大部分用了三到七成；有几张快用完了
            s.setUsedMb(Math.round(s.getPlanMb() * (k % 11 == 0 ? 0.95 : 0.3 + (k % 5) * 0.1) * 10) / 10.0);
            s.setActivateDate(today.minusDays(200 + (k * 53L) % 500));
            // 有几张一个月内到期，一张已经过期
            s.setExpireDate(k % 13 == 0 ? today.plusDays(10 + k % 15) : k == 7 ? today.minusDays(3) : s.getActivateDate().plusYears(2));
            sims.save(s);
        }

        // 库存：新到的 6 台终端，卡已经插好
        for (int n = 1; n <= 6; n++) {
            Device d = new Device(String.format("14500000%03d", n), DeviceType.JT808);
            d.setModel("SIM-808");
            d.setMakerId("SIMU1");
            d.setFirmwareVersion("V2.1.0");
            d.setSupplier(SUPPLIERS[n % 3]);
            d.setPurchaseDate(today.minusDays(20));
            d.setWarrantyUntil(today.minusDays(20).plusYears(3));
            d.setRemark("新到货，待安装");
            d = devices.save(d);
            SimCard s = service.attachSim(d, String.format("898604%014d", 90000000L + n));
            s.setStatus(SimCard.Status.IN_STOCK);
            s.setOperator(OPERATORS[n % 3]);
            s.setPlanMb(50);
            s.setExpireDate(today.plusYears(2));
            sims.save(s);
        }
        // 维修 2 台、报废 1 台
        String[][] other = {{"14500000101", "REPAIR", "GPS 天线故障，返厂维修"}, {"14500000102", "REPAIR", "主板进水"},
                {"14500000103", "SCRAPPED", "事故车拆下，外壳损坏"}};
        for (String[] o : other) {
            Device d = new Device(o[0], DeviceType.JT808);
            d.setModel("SIM-808");
            d.setFirmwareVersion("V1.8.3");
            d.setStatus(Device.Status.valueOf(o[1]));
            d.setSupplier(SUPPLIERS[0]);
            d.setPurchaseDate(today.minusYears(2));
            d.setWarrantyUntil(today.plusYears(1));
            d.setRemark(o[2]);
            devices.save(d);
        }
        // 一台没登记的终端来注册过（演示环境开了自动建档，真实环境里会出现在"待登记"里）
        Device p = new Device("13999990001", DeviceType.JT808);
        p.setModel("JT-G7");
        p.setMakerId("70111");
        p.setTerminalId("G7A00321");
        p.setProtocolVersion(2019);
        p.setLastRegisterAt(LocalDateTime.now().minusMinutes(25));
        p.setLastOnline(LocalDateTime.now().minusMinutes(25));
        p.setRemark("终端注册时上报车牌：粤B·D3721");
        devices.save(p);
        log.info("已生成设备台账演示数据：设备 {} 台，SIM 卡 {} 张", devices.count(), sims.count());
    }
}
