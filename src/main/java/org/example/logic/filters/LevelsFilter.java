package org.example.logic.filters;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

public class LevelsFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage image) {
        // Защита от ClassCastException
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = temp.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = temp;
        }

        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        IntStream.range(0, pixels.length).parallel().forEach(i -> {
            int rgb = pixels[i];
            int g = (rgb >> 8) & 0xFF;

            int out;
            if (g < 60) out = 0;
            else if (g > 210) out = 255;
            else out = (g - 60) * 255 / 150;

            pixels[i] = (0xFF << 24) | (out << 16) | (out << 8) | out;
        });

        return image;
    }
}