package org.example.logic;

import org.example.logic.filters.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class MangaImageProcessor {

    private final List<ImageFilter> pipeline = new ArrayList<>();
    private boolean useBinarization = false;

    public MangaImageProcessor() {
        // УДАЛЕНО: pipeline.add(new AutoCropFilter());
        // Причина: Обрезка теперь управляется умным алгоритмом в MangaResizer.

        // Оставляем только фильтры качества
        // УДАЛЕНО: pipeline.add(new UpscaleFilter());
        // Причина: Апскейл теперь управляется в MangaResizer для предотвращения повторной обработки.
        
        pipeline.add(new DenoiseFilter());
        pipeline.add(new LevelsFilter());
        pipeline.add(new SharpenFilter());
    }

    public void setBinarization(boolean enable) {
        this.useBinarization = enable;
    }

    public BufferedImage process(BufferedImage original) {
        if (original == null) return null;

        BufferedImage current = original;
        if (current.getType() != BufferedImage.TYPE_INT_RGB) {
            current = convertToCompat(original);
        }

        // Применяем фильтры (Резкость и т.д.)
        // ВНИМАНИЕ: Если выключена бинаризация (режим комиксов), 
        // пропускаем деструктивные фильтры улучшения ч/б текста.
        for (ImageFilter filter : pipeline) {
            if (!useBinarization) {
                if (filter instanceof DenoiseFilter || filter instanceof LevelsFilter || filter instanceof SharpenFilter) {
                    continue;
                }
            }
            
            BufferedImage next = filter.apply(current);
            // Если фильтр вернул новый объект, флушим старый (если он был создан нами)
            if (next != current && current != original) {
                current.flush();
            }
            current = next;
        }

        // E-Ink режим (всегда в конце)
        if (useBinarization) {
            BufferedImage next = new BinarizationFilter().apply(current);
            if (next != current && current != original) {
                current.flush();
            }
            current = next;
        }

        return current;
    }

    private BufferedImage convertToCompat(BufferedImage image) {
        // Если изображение уже нужного типа, проверяем, не является ли оно subimage (через смещение растра)
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            if (image.getRaster().getSampleModelTranslateX() == 0 &&
                image.getRaster().getSampleModelTranslateY() == 0) {
                return image;
            }
        }
        
        // Создаем новое "чистое" изображение без смещений и с нужным типом
        BufferedImage newImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImg.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImg;
    }
}