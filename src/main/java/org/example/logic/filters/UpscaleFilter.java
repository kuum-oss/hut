package org.example.logic.filters;

import java.awt.*;
import java.awt.image.BufferedImage;

public class UpscaleFilter implements ImageFilter {

    private static final int TARGET_WIDTH = 1600;

    @Override
    public BufferedImage apply(BufferedImage src) {
        if (src.getWidth() >= TARGET_WIDTH) {
            return src; // Не увеличиваем, если уже большая
        }

        // Вычисляем масштаб
        double scale = (double) TARGET_WIDTH / src.getWidth();
        scale = Math.min(scale, 2.5); // Ограничение x2.5

        if (scale <= 1.05) return src;

        int w = (int) (src.getWidth() * scale);
        int h = (int) (src.getHeight() * scale);

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = dst.createGraphics();

        // Максимальное качество
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();

        return dst;
    }
}