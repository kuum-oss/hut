package org.example.logic;

import java.awt.*;
import java.awt.image.*;
import java.util.stream.IntStream;

public class MangaUpscaler {

    private static final int TARGET_MIN_WIDTH = 1600; // Целевая ширина

    public BufferedImage improve(BufferedImage original) {
        if (original == null) return null;

        BufferedImage workingImage = convertToCompat(original);

        // 1. UPSCALE: Только если картинка реально маленькая
        if (workingImage.getWidth() < TARGET_MIN_WIDTH) {
            // Вычисляем масштаб, чтобы дотянуть минимум до 1600px
            double scale = (double) TARGET_MIN_WIDTH / workingImage.getWidth();
            // Но не более 2.5 раз, чтобы не размыть совсем в кашу
            scale = Math.min(scale, 2.5);

            if (scale > 1.1) { // Если разница существенная, увеличиваем
                workingImage = bicubicUpscale(workingImage, scale);
            }
        }

        // 2. ФИЛЬТРЫ: Применяем ВСЕГДА, если вызван этот метод
        // (Раньше фильтры пропускались, если размер был ок)

        applySmartLevelsParallel(workingImage);
        return applySharpen(workingImage);
    }

    private BufferedImage convertToCompat(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage newImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImg.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImg;
    }

    private BufferedImage bicubicUpscale(BufferedImage src, double factor) {
        int w = (int) (src.getWidth() * factor);
        int h = (int) (src.getHeight() * factor);

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = dst.createGraphics();

        // Максимальное качество рендера
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dst;
    }

    private void applySmartLevelsParallel(BufferedImage image) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        IntStream.range(0, pixels.length).parallel().forEach(i -> {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Используем зеленый канал как яркость (он самый информативный)
            int luma = g;

            int out;
            // Усиленная формула контраста
            if (luma < 50) {
                out = 0; // Black crush
            } else if (luma > 220) {
                out = 255; // White crush
            } else {
                // Линейное растяжение остатка
                out = (luma - 50) * 255 / (170);
                if (out < 0) out = 0;
                if (out > 255) out = 255;
            }

            pixels[i] = (out << 16) | (out << 8) | out;
        });
    }

    private BufferedImage applySharpen(BufferedImage image) {
        // Стандартная матрица резкости
        float[] sharpenMatrix = {
                0.0f, -1.0f, 0.0f,
                -1.0f, 5.0f, -1.0f,
                0.0f, -1.0f, 0.0f
        };
        Kernel kernel = new Kernel(3, 3, sharpenMatrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        return op.filter(image, result);
    }
}