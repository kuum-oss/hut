package org.example.logic.filters;

import java.awt.image.BufferedImage;

public class BinarizationFilter implements ImageFilter {

    @Override
    public BufferedImage apply(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        float[][] luma = new float[w][h];

        // Предварительный расчет яркости (Float для точности ошибки)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                luma[x][y] = (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF));
            }
        }

        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float oldP = luma[x][y];
                int newP = oldP < 128 ? 0 : 255;
                res.setRGB(x, y, newP == 0 ? 0 : 0xFFFFFF);

                float err = oldP - newP;
                // Распределение ошибки с проверкой границ
                if (x + 1 < w) luma[x+1][y] += err * 7/16f;
                if (y + 1 < h) {
                    if (x > 0) luma[x-1][y+1] += err * 3/16f;
                    luma[x][y+1] += err * 5/16f;
                    if (x + 1 < w) luma[x+1][y+1] += err * 1/16f;
                }
            }
        }
        return res;
    }
}