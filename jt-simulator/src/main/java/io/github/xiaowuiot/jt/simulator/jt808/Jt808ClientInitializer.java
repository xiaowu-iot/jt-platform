package io.github.xiaowuiot.jt.simulator.jt808;

import io.github.xiaowuiot.protocol.jt808.codec.Jt808Codecs;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * 终端这一侧的流水线：和平台用的是同一套 808 编解码器。
 */
class Jt808ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final Jt808Terminal terminal;

    Jt808ClientInitializer(Jt808Terminal terminal) {
        this.terminal = terminal;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        Jt808Codecs.addTo(ch.pipeline());
        ch.pipeline().addLast(terminal);
    }
}
