package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

public class LevelsFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage image) {
        // Работаем напрямую с памятью для скорости
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        IntStream.range(0, pixels.length).parallel().forEach(i -> {
            int rgb = pixels[i];
            int g = (rgb >> 8) & 0xFF; // Зеленый канал как яркость

            int out;
            // Агрессивная кривая для манги
            if (g < 60) {
                out = 0; // Black point
            } else if (g > 210) {
                out = 255; // White point
            } else {
                // Растягиваем середину
                out = (g - 60) * 255 / (150);
                if (out < 0) out = 0;
                if (out > 255) out = 255;
            }

            pixels[i] = (out << 16) | (out << 8) | out;
        });

        return image; // Возвращаем тот же объект (измененный in-place)
    }
}