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
    // Базовое DPI повышено до 300 для HD качества
    private static final float RENDER_DPI = 300f;
    // Коэффициент дополнительного увеличения (если включен upscale)
    private static final double UPSCALE_FACTOR = 1.5;

    public void applyResize(File file, CropMode cropMode, boolean upscale, boolean binarization, boolean skipFirstPage, boolean smartCrop) throws Exception {
        System.out.println("   [LOG] Открытие файла для HD обработки: " + file.getName());

        // Принудительная чистка памяти перед началом тяжелой работы
        System.gc();

        try (PDDocument sourceDoc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Используем memory-optimized режим для нового документа, чтобы экономить RAM
            try (PDDocument newDoc = new PDDocument(org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                PDFRenderer renderer = new PDFRenderer(sourceDoc);
                int totalPages = sourceDoc.getNumberOfPages();

                for (int i = 0; i < totalPages; i++) {
                    // Чистим память на каждой итерации, иначе на 300 DPI все лопнет
                    if (i % 5 == 0) System.gc();

                    // --- 1. ПРОВЕРКА НА РЕКЛАМУ ---
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String pageText = stripper.getText(sourceDoc).toLowerCase();

                    if (pageText.contains(WATERMARK_TEXT)) {
                        System.out.println("   [DEL] Страница " + (i + 1) + " удалена (реклама).");
                        continue;
                    }

                    // --- 2. ПРОПУСК ОБЛОЖКИ ---
                    if (skipFirstPage && i == 0) {
                        System.out.println("   [SKIP] Обложка скопирована без изменений.");
                        newDoc.addPage(sourceDoc.getPage(i));
                        continue;
                    }

                    System.out.println("   [PROC] Обработка страницы " + (i + 1) + " (HD Quality)...");

                    // --- 3. РЕНДЕРИНГ В HD (300 DPI) ---
                    // Это самая тяжелая часть для памяти
                    BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);

                    // --- 4. ДОПОЛНИТЕЛЬНОЕ HD УЛУЧШЕНИЕ (Upscale) ---
                    if (upscale) {
                        System.out.println("      [HD+] Дополнительное улучшение четкости (x" + UPSCALE_FACTOR + ")...");
                        image = upscaleImageHighQuality(image);
                    }

                    // --- 5. АВТО-ОБРЕЗКА ---
                    if (smartCrop) {
                        image = autoCrop(image);
                    }

                    // --- 6. ФИЛЬТРЫ (Ч/Б) ---
                    imageProcessor.setBinarization(binarization);
                    BufferedImage processed = imageProcessor.process(image);

                    // Освобождаем исходную тяжелую картинку
                    image.flush();

                    // --- 7. СОЗДАНИЕ СТРАНИЦЫ ---
                    PDPage newPage = new PDPage(new PDRectangle(processed.getWidth(), processed.getHeight()));
                    newDoc.addPage(newPage);

                    // Используем LosslessFactory (PNG сжатие) для сохранения максимального качества
                    PDImageXObject pdImage = LosslessFactory.createFromImage(newDoc, processed);
                    try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                        contentStream.drawImage(pdImage, 0, 0);
                    }
                    // Освобождаем обработанную картинку
                    processed.flush();
                }

                String newPath = file.getAbsolutePath().replace(".pdf", "_HD.pdf");
                newDoc.save(new File(newPath));
                System.out.println("   [DONE] HD Файл сохранен: " + newPath);
            }
        } catch (OutOfMemoryError e) {
            System.err.println("   [!!!] КРИТИЧЕСКАЯ ОШИБКА ПАМЯТИ [!!!]");
            System.err.println("   Для HD обработки 300 DPI нужно больше памяти.");
            System.err.println("   Добавьте в параметры запуска: -Xmx6G");
            throw new Exception("Нехватка памяти для HD обработки. Увеличьте Heap Size.");
        }
    }

    /**
     * Качественное увеличение изображения (Lanczos interpolation).
     * Делает линии гладкими без "мыла".
     */
    /**
     * Качественное увеличение изображения.
     * Исправлено: Используем BICUBIC, так как LANCZOS нет в стандартной Java.
     */
    private BufferedImage upscaleImageHighQuality(BufferedImage source) {
        int newW = (int) (source.getWidth() * UPSCALE_FACTOR);
        int newH = (int) (source.getHeight() * UPSCALE_FACTOR);

        BufferedImage resized = new BufferedImage(newW, newH, source.getType());
        Graphics2D g = resized.createGraphics();

        // Включаем максимальное качество, доступное в Java
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); // <-- ИСПРАВЛЕНО ТУТ
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();

        return resized;
    }

    // (Методы autoCrop, isRowWhite, isColWhite, isPixelWhite остаются без изменений из прошлого ответа)
    private BufferedImage autoCrop(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int top = 0, bottom = height - 1, left = 0, right = width - 1;
        int whiteThreshold = 230;

        for (int y = 0; y < height; y++) { if (!isRowWhite(source, y, width, whiteThreshold)) { top = y; break; } }
        for (int y = height - 1; y >= 0; y--) { if (!isRowWhite(source, y, width, whiteThreshold)) { bottom = y; break; } }
        for (int x = 0; x < width; x++) { if (!isColWhite(source, x, top, bottom, whiteThreshold)) { left = x; break; } }
        for (int x = width - 1; x >= 0; x--) { if (!isColWhite(source, x, top, bottom, whiteThreshold)) { right = x; break; } }

        if (left >= right || top >= bottom) return source;

        int padding = 20;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width, right + padding);
        bottom = Math.min(height, bottom + padding);

        return source.getSubimage(left, top, right - left, bottom - top);
    }

    private boolean isRowWhite(BufferedImage img, int y, int width, int threshold) {
        for (int x = 0; x < width; x += 5) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isColWhite(BufferedImage img, int x, int startY, int endY, int threshold) {
        for (int y = startY; y <= endY; y += 5) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isPixelWhite(int rgb, int threshold) {
        return ((rgb >> 16) & 0xFF) > threshold && ((rgb >> 8) & 0xFF) > threshold && (rgb & 0xFF) > threshold;
    }
}