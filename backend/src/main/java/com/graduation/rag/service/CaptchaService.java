package com.graduation.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务（纯 Java2D 实现，零第三方依赖）
 *
 * 设计要点：
 * 1. 字符集剔除易混淆字符（0/O、1/l/I）
 * 2. 验证码存内存 ConcurrentHashMap，5 分钟过期，一次性使用（verify 即销毁，防暴力尝试）
 * 3. 图片带渐变背景、干扰线、字符随机旋转，防简单 OCR
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 4;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final long EXPIRE_MS = 5 * 60 * 1000L;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private record Entry(String code, long expireAt) {
    }

    /** 验证码响应：ID + Base64 图片（data URI，前端 img src 直接用） */
    public record CaptchaResult(String captchaId, String imageBase64) {
    }

    /**
     * 生成验证码
     */
    public CaptchaResult generate() {
        purgeExpired();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String code = sb.toString();
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new Entry(code, System.currentTimeMillis() + EXPIRE_MS));
        log.debug("验证码生成 {} -> {}", id, code);   // 仅 DEBUG 级别，生产不输出
        return new CaptchaResult(id, renderImage(code));
    }

    /**
     * 校验验证码（一次性：无论对错均销毁，防止同一验证码被暴力尝试）
     */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || input == null) {
            return false;
        }
        Entry entry = store.remove(captchaId);
        return entry != null
                && entry.expireAt() > System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(input.trim());
    }

    /** 惰性清理过期验证码 */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    /** Java2D 绘制验证码图片，返回 data:image/png;base64,... */
    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 渐变浅色背景
        g.setPaint(new GradientPaint(0, 0, new Color(238, 242, 255), WIDTH, HEIGHT, new Color(220, 245, 250)));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(150 + random.nextInt(80), 150 + random.nextInt(80), 200 + random.nextInt(55)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 字符（随机颜色 + 随机旋转）
        int slot = WIDTH / (LENGTH + 1);
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(20 + random.nextInt(110), 20 + random.nextInt(110), 20 + random.nextInt(110)));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28 + random.nextInt(6)));
            double angle = (random.nextDouble() - 0.5) * 0.55;
            int x = slot / 2 + slot * i + random.nextInt(6);
            int y = HEIGHT / 2 + 10 + random.nextInt(5) - 2;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }

        // 噪点
        for (int i = 0; i < 60; i++) {
            image.setRGB(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    new Color(120 + random.nextInt(120), 120 + random.nextInt(120), 120 + random.nextInt(120)).getRGB());
        }
        g.dispose();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }
}
