package org.example.logic.filters;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

public class SharpenFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage image) {
        // Мягкая матрица для резкости (уменьшена с 1.8 до 1.7 для предотвращения перешарпа)
        float[] sharpenMatrix = {
                -0.1f, -0.1f, -0.1f,
                -0.1f,  1.7f, -0.1f,
                -0.1f, -0.1f, -0.1f
        };
        Kernel kernel = new Kernel(3, 3, sharpenMatrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        return op.filter(image, result);
    }
}