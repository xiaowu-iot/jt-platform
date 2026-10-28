package io.github.xiaowuiot.jt.simulator;

import io.github.xiaowuiot.jt.simulator.jt808.Jt808Command;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * 模拟器入口。
 * <pre>
 * java -jar jt-simulator.jar jt808 --host 127.0.0.1 --port 7611 --count 100
 * java -jar jt-simulator.jar jt808 --version 2019 --verbose
 * </pre>
 */
@Command(name = "jt-simulator",
        mixinStandardHelpOptions = true,
        version = "jt-simulator 0.1.0-SNAPSHOT",
        description = "车载终端模拟器：模拟 JT/T 808 终端，用于联调和压测",
        subcommands = {
                Jt808Command.class
        })
public class SimulatorApplication implements Runnable {

    public static void main(String[] args) {
        // 日志配置不叫 logback.xml：服务端把模拟器当依赖引入时，不能顶掉服务端自己的日志配置
        if (System.getProperty("logback.configurationFile") == null) {
            System.setProperty("logback.configurationFile", "jt-simulator-logback.xml");
        }
        System.exit(new CommandLine(new SimulatorApplication()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
