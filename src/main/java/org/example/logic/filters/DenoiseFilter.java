package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class DenoiseFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        // Проверка типа и смещений (для безопасного доступа к DataBufferInt)
        if (img.getType() != BufferedImage.TYPE_INT_RGB ||
            img.getRaster().getSampleModelTranslateX() != 0 ||
            img.getRaster().getSampleModelTranslateY() != 0) {
            
            BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = temp.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = temp;
        }
        
        // Создаем результирующее изображение того же типа
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Получаем прямой доступ к пикселям через DataBufferInt
        int[] srcPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int[] dstPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        // Копируем края (верхнюю и нижнюю строки)
        System.arraycopy(srcPixels, 0, dstPixels, 0, width);
        System.arraycopy(srcPixels, (height - 1) * width, dstPixels, (height - 1) * width, width);

        // Медианный фильтр 3x3
        // Используем ThreadLocal или просто создаем один массив на поток, если бы использовали параллелизм.
        // Но для простоты и учитывая, что это вызывается в пайплайне, создадим локальный массив.
        int[] window = new int[9];
        
        for (int y = 1; y < height - 1; y++) {
            // Копируем крайние пиксели слева и справа в строке
            dstPixels[y * width] = srcPixels[y * width];
            dstPixels[y * width + width - 1] = srcPixels[y * width + width - 1];
            
            for (int x = 1; x < width - 1; x++) {
                int k = 0;
                // Извлекаем яркость (зеленый канал как аппроксимацию для ч/б)
                for (int dy = -1; dy <= 1; dy++) {
                    int offset = (y + dy) * width + (x - 1);
                    window[k++] = (srcPixels[offset] >> 8) & 0xFF;
                    window[k++] = (srcPixels[offset + 1] >> 8) & 0xFF;
                    window[k++] = (srcPixels[offset + 2] >> 8) & 0xFF;
                }
                
                // Быстрая сортировка для 9 элементов (можно оптимизировать до фиксированной сети сортировки)
                Arrays.sort(window);
                int median = window[4];
                
                dstPixels[y * width + x] = (0xFF << 24) | (median << 16) | (median << 8) | median;
            }
        }
        return result;
    }
}