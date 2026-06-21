package com.ichi.entry.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 갤러리 그리드용 썸네일 생성 (이미지 한정). ImageIO 로 JPEG 썸네일을 만든다.
 * 실패하면 null 을 반환(썸네일 없이 원본만 쓰면 됨).
 */
@Component
public class Thumbnailer {

    private static final Logger log = LoggerFactory.getLogger(Thumbnailer.class);
    private static final int MAX_EDGE = 320; // 긴 변 기준 축소

    public static final String THUMB_MIME = "image/jpeg";

    /** 원본 이미지 바이트 → 썸네일 JPEG 바이트. 만들 수 없으면 null. */
    public byte[] generate(byte[] original) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) {
                return null; // 디코딩 불가(예: 일부 webp)
            }
            int w = src.getWidth();
            int h = src.getHeight();
            double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));

            BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, tw, th, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.debug("썸네일 생성 실패(원본만 사용): {}", e.getMessage());
            return null;
        }
    }
}
