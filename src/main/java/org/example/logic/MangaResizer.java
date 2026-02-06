package org.example.logic;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.model.CropMode;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class MangaResizer {

    private final MangaImageProcessor imageProcessor = new MangaImageProcessor();
    private static final String WATERMARK_TEXT = "oceanofpdf";

    // ОПТИМИЗАЦИЯ: Чуть снизили DPI для скорости (144 - это стандарт 2x экранов)
    private static final float DPI_STANDARD = 144f;
    private static final float DPI_HIGH = 300f;
    private static final double UPSCALE_FACTOR = 1.5;

    public void applyResize(File file, CropMode cropMode, boolean upscale, boolean binarization, boolean skipFirstPage, boolean smartCrop) throws Exception {
        float currentDpi = upscale ? DPI_HIGH : DPI_STANDARD;
        String modeName = upscale ? "HD Quality (Slow)" : "Turbo Mode (Fast)";

        System.out.println("   [LOG] Открытие: " + file.getName());
        System.out.println("   [MODE] " + modeName);

        // Ручная чистка только один раз перед стартом
        System.gc();

        try (PDDocument sourceDoc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // MemoryUsageSetting.setupTempFileOnly() важен для больших файлов
            try (PDDocument newDoc = new PDDocument(org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                PDFRenderer renderer = new PDFRenderer(sourceDoc);
                int totalPages = sourceDoc.getNumberOfPages();

                for (int i = 0; i < totalPages; i++) {
                    // ОПТИМИЗАЦИЯ: Убрали System.gc() из цикла.
                    // Это убирает микро-фризы на каждой странице.

                    // 1. Поиск рекламы
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String pageText = stripper.getText(sourceDoc).toLowerCase();

                    if (pageText.contains(WATERMARK_TEXT)) {
                        System.out.println("   [DEL] Стр. " + (i + 1) + " удалена (реклама).");
                        continue;
                    }

                    if (skipFirstPage && i == 0) {
                        newDoc.addPage(sourceDoc.getPage(i));
                        continue;
                    }

                    System.out.println("   [PROC] Стр. " + (i + 1) + " / " + totalPages);

                    // 2. Рендеринг
                    BufferedImage image = renderer.renderImageWithDPI(i, currentDpi, ImageType.RGB);

                    // 3. Upscale (Если включен)
                    if (upscale) {
                        image = resizeImage(image, UPSCALE_FACTOR, true);
                    }

                    // 4. Smart Crop (Быстрый алгоритм)
                    if (smartCrop) {
                        image = autoCropFast(image);
                    }

                    // 5. Фильтры
                    imageProcessor.setBinarization(binarization);
                    BufferedImage processed = imageProcessor.process(image);
                    image.flush();

                    // 6. Сохранение
                    PDPage newPage = new PDPage(new PDRectangle(processed.getWidth(), processed.getHeight()));
                    newDoc.addPage(newPage);

                    PDImageXObject pdImage = LosslessFactory.createFromImage(newDoc, processed);
                    try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                        contentStream.drawImage(pdImage, 0, 0);
                    }
                    processed.flush();
                }

                String suffix = upscale ? "_HD.pdf" : "_fixed.pdf";
                String newPath = file.getAbsolutePath().replace(".pdf", suffix);
                newDoc.save(new File(newPath));
                System.out.println("   [DONE] Сохранено: " + newPath);
            }
        } catch (OutOfMemoryError e) {
            System.err.println("   [MEM] Не хватает памяти! Используйте настройки запуска -Xmx4G");
            throw new Exception("Out Of Memory");
        }
    }

    /**
     * ОПТИМИЗИРОВАННЫЙ РЕСАЙЗ
     * В HD режиме использует Bicubic (качественно).
     * В обычном режиме использует Bilinear (быстро).
     */
    private BufferedImage resizeImage(BufferedImage source, double factor, boolean highQuality) {
        int newW = (int) (source.getWidth() * factor);
        int newH = (int) (source.getHeight() * factor);

        BufferedImage resized = new BufferedImage(newW, newH, source.getType());
        Graphics2D g = resized.createGraphics();

        if (highQuality) {
            // Медленно, но красиво (для HD галочки)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            // ОПТИМИЗАЦИЯ: Быстро (для обычного режима)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }

        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();

        return resized;
    }

    /**
     * ТУРБО ОБРЕЗКА (Auto Crop Fast)
     * Сканирует пиксели с большим шагом, что ускоряет процесс в 10 раз.
     */
    private BufferedImage autoCropFast(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int top = 0, bottom = height - 1, left = 0, right = width - 1;
        int whiteThreshold = 230;

        // ОПТИМИЗАЦИЯ: Шаг проверки 30 пикселей вместо 5.
        // Для поиска белых полей этого достаточно, а скорость возрастает в разы.
        int scanStep = 30;

        for (int y = 0; y < height; y += scanStep) {
            if (!isRowWhite(source, y, width, whiteThreshold)) {
                top = Math.max(0, y - scanStep); // Откат назад для точности
                break;
            }
        }
        for (int y = height - 1; y >= 0; y -= scanStep) {
            if (!isRowWhite(source, y, width, whiteThreshold)) {
                bottom = Math.min(height - 1, y + scanStep);
                break;
            }
        }
        for (int x = 0; x < width; x += scanStep) {
            if (!isColWhite(source, x, top, bottom, whiteThreshold)) {
                left = Math.max(0, x - scanStep);
                break;
            }
        }
        for (int x = width - 1; x >= 0; x -= scanStep) {
            if (!isColWhite(source, x, top, bottom, whiteThreshold)) {
                right = Math.min(width - 1, x + scanStep);
                break;
            }
        }

        if (left >= right || top >= bottom) return source;

        int padding = 20;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width, right + padding);
        bottom = Math.min(height, bottom + padding);

        return source.getSubimage(left, top, right - left, bottom - top);
    }

    private boolean isRowWhite(BufferedImage img, int y, int width, int threshold) {
        // ОПТИМИЗАЦИЯ: Проверяем каждый 20-й пиксель в строке
        for (int x = 0; x < width; x += 20) {
            if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        }
        return true;
    }

    private boolean isColWhite(BufferedImage img, int x, int startY, int endY, int threshold) {
        // ОПТИМИЗАЦИЯ: Проверяем каждый 20-й пиксель в столбце
        for (int y = startY; y <= endY; y += 20) {
            if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        }
        return true;
    }

    private boolean isPixelWhite(int rgb, int threshold) {
        return ((rgb >> 16) & 0xFF) > threshold && ((rgb >> 8) & 0xFF) > threshold && (rgb & 0xFF) > threshold;
    }
}