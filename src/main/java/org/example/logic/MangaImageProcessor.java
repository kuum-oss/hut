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
        pipeline.add(new UpscaleFilter());
        pipeline.add(new DenoiseFilter());
        pipeline.add(new LevelsFilter());
        pipeline.add(new SharpenFilter());
    }

    public void setBinarization(boolean enable) {
        this.useBinarization = enable;
    }

    public BufferedImage process(BufferedImage original) {
        if (original == null) return null;

        BufferedImage current = convertToCompat(original);

        // Применяем фильтры (Апскейл, Резкость и т.д.)
        for (ImageFilter filter : pipeline) {
            current = filter.apply(current);
        }

        // E-Ink режим (всегда в конце)
        if (useBinarization) {
            current = new BinarizationFilter().apply(current);
        }

        return current;
    }

    private BufferedImage convertToCompat(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage newImg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImg.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImg;
    }
}