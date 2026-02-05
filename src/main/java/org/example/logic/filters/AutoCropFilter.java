package org.example.logic.filters;

import java.awt.image.BufferedImage;

public class AutoCropFilter implements ImageFilter {

    // Минимальный размер полезного контента (в пикселях)
    // Если после обрезки картинка меньше этого — считаем, что алгоритм ошибся
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 400;

    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        int top = 0;
        int bottom = height - 1;
        int left = 0;
        int right = width - 1;

        // 1. Ищем границы (как и раньше)
        for (int y = 0; y < height; y++) {
            if (!isRowWhite(img, y, width)) {
                top = y;
                break;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            if (!isRowWhite(img, y, width)) {
                bottom = y;
                break;
            }
        }
        for (int x = 0; x < width; x++) {
            if (!isColWhite(img, x, top, bottom)) {
                left = x;
                break;
            }
        }
        for (int x = width - 1; x >= 0; x--) {
            if (!isColWhite(img, x, top, bottom)) {
                right = x;
                break;
            }
        }

        // --- ПРОВЕРКИ БЕЗОПАСНОСТИ ---

        // 1. Если координаты перепутались (пустая страница)
        if (right <= left || bottom <= top) {
            return img;
        }

        // 2. Добавляем отступы (padding)
        int padding = 10;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width - 1, right + padding);
        bottom = Math.min(height - 1, bottom + padding);

        int newW = right - left + 1;
        int newH = bottom - top + 1;

        // 3. ЗАЩИТА: Если результат слишком маленький (мусор или ошибка)
        if (newW < MIN_WIDTH || newH < MIN_HEIGHT) {
            System.out.println("   [AutoCrop] Отмена: Результат слишком мал (" + newW + "x" + newH + ")");
            return img;
        }

        // 4. ЗАЩИТА: Если мы обрезали меньше 2% площади, нет смысла дергаться
        long originalArea = (long) width * height;
        long newArea = (long) newW * newH;
        if (newArea > originalArea * 0.98) {
            return img; // Почти ничего не изменилось
        }

        System.out.println("   [AutoCrop] Обрезка: " + width + "x" + height + " -> " + newW + "x" + newH);
        return img.getSubimage(left, top, newW, newH);
    }

    private boolean isRowWhite(BufferedImage img, int y, int width) {
        for (int x = 0; x < width; x++) {
            if (isDark(img.getRGB(x, y))) return false;
        }
        return true;
    }

    private boolean isColWhite(BufferedImage img, int x, int startY, int endY) {
        for (int y = startY; y <= endY; y++) {
            if (isDark(img.getRGB(x, y))) return false;
        }
        return true;
    }

    private boolean isDark(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        // Порог 230 (если пиксель темнее 230 — это контент)
        return (r < 230 || g < 230 || b < 230);
    }
}