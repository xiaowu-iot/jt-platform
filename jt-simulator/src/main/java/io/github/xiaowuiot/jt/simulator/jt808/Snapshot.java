package io.github.xiaowuiot.jt.simulator.jt808;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟终端拍照：画一张像行车记录仪画面的 JPEG（道路、车道线、时间水印、车牌、速度），
 * 平台收到后能正常显示，比随机字节更直观。
 */
public final class Snapshot {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private Snapshot() {
    }

    public static byte[] jpeg(String plate, int channel, double speed, LocalDateTime time) {
        return jpeg(plate, channel, speed, time, null);
    }

    /**
     * @param caption 报警抓拍时画在画面上的报警名称，比如"前向碰撞报警"
     */
    public static byte[] jpeg(String plate, int channel, double speed, LocalDateTime time, String caption) {
        int w = 640;
        int h = 360;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            boolean night = time.getHour() < 6 || time.getHour() >= 19;
            // 天空
            g.setPaint(night ? new GradientPaint(0, 0, new Color(10, 16, 36), 0, 170, new Color(40, 50, 80))
                    : new GradientPaint(0, 0, new Color(120, 170, 225), 0, 170, new Color(205, 225, 240)));
            g.fillRect(0, 0, w, 170);
            // 远处的楼
            for (int x = 0; x < w; x += 26 + r.nextInt(30)) {
                int bh = 40 + r.nextInt(90);
                int c = night ? 30 + r.nextInt(25) : 140 + r.nextInt(60);
                g.setColor(new Color(c, c + 5, c + 15));
                g.fillRect(x, 170 - bh, 22 + r.nextInt(20), bh);
            }
            // 路面
            g.setPaint(new GradientPaint(0, 170, night ? new Color(35, 38, 45) : new Color(95, 98, 105), 0, h,
                    night ? new Color(20, 22, 26) : new Color(60, 62, 68)));
            g.fillRect(0, 170, w, h - 170);
            g.setColor(night ? new Color(80, 80, 60) : new Color(230, 230, 220));
            g.setStroke(new BasicStroke(4));
            g.drawLine(0, h, 290, 170);
            g.drawLine(w, h, 350, 170);
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{26, 22}, r.nextInt(48)));
            g.drawLine(w / 2, h, 320, 170);
            // 前车
            if (r.nextBoolean()) {
                int cx = 260 + r.nextInt(120);
                int cy = 215 + r.nextInt(40);
                g.setColor(new Color(r.nextInt(80, 200), r.nextInt(30, 120), r.nextInt(30, 90)));
                g.fillRoundRect(cx, cy, 90, 56, 10, 10);
                g.setColor(new Color(20, 20, 25));
                g.fillRect(cx + 10, cy + 8, 70, 18);
                g.setColor(new Color(255, 60, 50));
                g.fillRect(cx + 4, cy + 34, 14, 8);
                g.fillRect(cx + 72, cy + 34, 14, 8);
            }
            // 引擎盖
            Polygon hood = new Polygon(new int[]{0, w, w, 0}, new int[]{h - 30, h - 30, h, h}, 4);
            g.setColor(new Color(25, 28, 34));
            g.fill(hood);
            // 水印
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(0, 0, w, 30);
            g.setColor(Color.WHITE);
            g.drawString(time.format(TIME) + "   CH" + channel, 12, 21);
            g.drawString(plate, w - 140, 21);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString(String.format("%.0f km/h", speed), 14, h - 8);
            if (caption != null) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                int tw = g.getFontMetrics().stringWidth(caption);
                g.setColor(new Color(220, 30, 30, 200));
                g.fillRoundRect(w - tw - 36, 44, tw + 24, 36, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(caption, w - tw - 24, 70);
                g.setColor(new Color(255, 60, 50));
                g.setStroke(new BasicStroke(3));
                g.drawRect(250, 190, 150, 100);
            }
        } finally {
            g.dispose();
        }
        return encode(img);
    }

    private static byte[] encode(BufferedImage img) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.75f);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
