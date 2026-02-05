package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class DenoiseFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Копируем исходное изображение, чтобы сохранить края
        result.getGraphics().drawImage(img, 0, 0, null);

        int[] window = new int[9];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int k = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        window[k++] = (img.getRGB(x + dx, y + dy) >> 8) & 0xFF;
                    }
                }
                Arrays.sort(window);
                int median = window[4];
                result.setRGB(x, y, (0xFF << 24) | (median << 16) | (median << 8) | median);
            }
        }
        return result;
    }
}