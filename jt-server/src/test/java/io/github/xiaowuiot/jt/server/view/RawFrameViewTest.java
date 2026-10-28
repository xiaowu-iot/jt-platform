package io.github.xiaowuiot.jt.server.view;

import io.github.xiaowuiot.protocol.jt808.MessageId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawFrameViewTest {

    /**
     * 心跳 + 位置汇报粘在一次读里
     */
    private static final String STICKY = "7E 0002 0000 013800138000 0001 A9 7E"
            + "7E 02 00 00 25 01 38 00 13 80 00 00 07 00 00 00 00 00 00 00 03 01 57 FA F8"
            + "06 CC 62 89 00 1E 02 58 00 5A 26 10 20 08 30 05 01 04 00 00 30 39 31 01 0C F9 7E";

    @Test
    void splitsStickyFrames() {
        List<RawFrameView.Frame> frames = RawFrameView.split(STICKY);
        assertEquals(2, frames.size());
        assertEquals(MessageId.HEARTBEAT, frames.get(0).msgId());
        assertEquals("终端心跳", frames.get(0).name());
        assertEquals(MessageId.LOCATION, frames.get(1).msgId());
    }

    @Test
    void escapedMessageIdIsRestored() {
        // 消息 ID 0x7E01 不存在，只是为了让 ID 本身被转义成 7D 02 01
        List<RawFrameView.Frame> frames = RawFrameView.split("7E 7D02 01 0000 013800138000 0001 00 7E");
        assertEquals(0x7E01, frames.get(0).msgId());
    }

    @Test
    void truncatedRecordDoesNotFail() {
        // 超长的记录被截断，末尾带 "..."，长度还可能是奇数
        List<RawFrameView.Frame> frames = RawFrameView.split("7E00020000013800138000 0001A97E7E08010...");
        assertEquals(1, frames.size());
    }
}
