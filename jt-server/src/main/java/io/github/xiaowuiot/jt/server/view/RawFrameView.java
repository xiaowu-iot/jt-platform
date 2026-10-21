package io.github.xiaowuiot.jt.server.view;

import io.github.xiaowuiot.protocol.codec.stats.RawMessageRecorder;
import io.github.xiaowuiot.protocol.codec.util.HexUtils;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.codec.Jt808Codecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

/**
 * 原始报文的视图。一次 TCP 读写里可能有好几帧（粘包），也可能只有半帧（拆包），这里按 7E 切开，
 * 每帧标出消息 ID 和名称，报文监视页面上一眼就能看出终端发了什么。
 *
 * @param direction in 终端发给平台，out 平台发给终端
 * @param frames    按 7E 切出来的帧，半帧也会列出来，msgId 为 -1
 */
public record RawFrameView(long time, String direction, String remote, String hex, List<Frame> frames) {

    public record Frame(int msgId, String name, String hex) {
    }

    public static RawFrameView of(RawMessageRecorder.Record r) {
        return new RawFrameView(r.time, r.direction, r.remote, r.hex(), split(r.hex()));
    }

    static List<Frame> split(String hex) {
        // 超长的记录被截断过，末尾带 "..."，切掉后按完整的字节解析
        String clean = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.length() % 2 != 0) {
            clean = clean.substring(0, clean.length() - 1);
        }
        byte[] bytes = HexUtils.fromHex(clean);
        List<Frame> frames = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != Jt808Codecs.DELIMITER) {
                continue;
            }
            if (start >= 0 && i > start + 1) {
                frames.add(frame(bytes, start, i));
                start = -1;
            } else {
                start = i;
            }
        }
        return frames;
    }

    private static Frame frame(byte[] bytes, int start, int end) {
        ByteBuf escaped = Unpooled.wrappedBuffer(bytes, start, end - start + 1);
        ByteBuf content = Unpooled.buffer(end - start);
        int msgId = -1;
        if (Jt808Codecs.ESCAPER.unescape(escaped, 1, end - start - 1, content) && content.readableBytes() >= 2) {
            msgId = content.getUnsignedShort(0);
        }
        content.release();
        String hex = HexUtils.toHex(escaped);
        return new Frame(msgId, msgId < 0 ? "无法识别" : MessageId.name(msgId), hex);
    }
}
