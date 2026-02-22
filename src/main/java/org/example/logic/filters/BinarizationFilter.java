package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class BinarizationFilter implements ImageFilter {

    @Override
    public BufferedImage apply(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        
        // Получаем пиксели исходного изображения
        int[] srcPixels;
        if ((img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_INT_ARGB) &&
            img.getRaster().getSampleModelTranslateX() == 0 &&
            img.getRaster().getSampleModelTranslateY() == 0) {
            srcPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        } else {
            // Если тип другой или есть смещения, получаем через getRGB (медленнее, но надежнее)
            srcPixels = img.getRGB(0, 0, w, h, null, 0, w);
        }

        float[] luma = new float[w * h];

        // Предварительный расчет яркости
        for (int i = 0; i < srcPixels.length; i++) {
            int rgb = srcPixels[i];
            luma[i] = (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF));
        }

        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        // Floyd-Steinberg дизеринг
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float oldP = luma[idx];
                int newP = oldP < 128 ? 0 : 255;
                res.setRGB(x, y, newP == 0 ? 0 : 0xFFFFFF);

                float err = oldP - newP;
                // Распределение ошибки
                if (x + 1 < w) luma[idx + 1] += err * 7/16f;
                if (y + 1 < h) {
                    int nextLine = (y + 1) * w;
                    if (x > 0) luma[nextLine + x - 1] += err * 3/16f;
                    luma[nextLine + x] += err * 5/16f;
                    if (x + 1 < w) luma[nextLine + x + 1] += err * 1/16f;
                }
            }
        }
        return res;
    }
}