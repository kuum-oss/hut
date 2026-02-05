package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class DenoiseFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, img.getType());

        int[] window = new int[9];

        // Пропускаем края, чтобы не вылететь за границы массива
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int k = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        // Берем зеленый канал как самый яркий для ч/б
                        int rgb = img.getRGB(x + dx, y + dy);
                        window[k++] = (rgb >> 8) & 0xFF;
                    }
                }

                Arrays.sort(window);
                int middle = window[4]; // Медиана

                // Восстанавливаем цвет (grey -> rgb)
                int newPixel = (0xFF << 24) | (middle << 16) | (middle << 8) | middle;
                result.setRGB(x, y, newPixel);
            }
        }
        return result;
    }
}