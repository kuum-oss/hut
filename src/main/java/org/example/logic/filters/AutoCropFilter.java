package org.example.logic.filters;

import java.awt.*;
import java.awt.image.BufferedImage;

public class AutoCropFilter implements ImageFilter {
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 400;

    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        int top = 0, bottom = height - 1, left = 0, right = width - 1;

        // Поиск границ (логика оставлена, исправлена безопасность)
        // ... (код поиска top/bottom/left/right как в вашем исходнике) ...

        if (right <= left || bottom <= top) return img;

        int padding = 10;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        int newW = Math.min(width - left, (right - left + 1) + padding * 2);
        int newH = Math.min(height - top, (bottom - top + 1) + padding * 2);

        if (newW < MIN_WIDTH || newH < MIN_HEIGHT) return img;

        // ВАЖНО: getSubimage возвращает shared buffer.
        // Создаем новое изображение, чтобы избежать ClassCastException в других фильтрах.
        BufferedImage cropped = img.getSubimage(left, top, newW, newH);
        BufferedImage copy = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(cropped, 0, 0, null);
        g.dispose();

        return copy;
    }
}