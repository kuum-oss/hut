package org.example.logic.filters;

import java.awt.image.BufferedImage;

public class BinarizationFilter implements ImageFilter {

    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        // 1. Карта яркости
        float[][] lum = new float[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Формула восприятия яркости глазом
                lum[x][y] = (0.299f * r + 0.587f * g + 0.114f * b);
            }
        }

        // 2. Создаем 1-битное изображение (TYPE_BYTE_BINARY)
        // Это важно, чтобы PDFBox использовал сжатие CCITT Fax (маленький размер)
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        // 3. Дизеринг (Floyd-Steinberg)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float oldPixel = lum[x][y];
                int newPixel = (oldPixel < 128) ? 0 : 255;

                int rgbVal = (newPixel == 0) ? 0x000000 : 0xFFFFFF;
                result.setRGB(x, y, rgbVal);

                float quantError = oldPixel - newPixel;

                // Распределяем ошибку на соседей
                if (x + 1 < width)
                    lum[x + 1][y] += quantError * 7 / 16;
                if (x - 1 >= 0 && y + 1 < height)
                    lum[x - 1][y + 1] += quantError * 3 / 16;
                if (y + 1 < height)
                    lum[x][y + 1] += quantError * 5 / 16;
                if (x + 1 < width && y + 1 < height)
                    lum[x + 1][y + 1] += quantError * 1 / 16;
            }
        }
        return result;
    }
}