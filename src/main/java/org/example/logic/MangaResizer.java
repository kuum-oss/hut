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

    // Настройки качества
    private static final float DPI_STANDARD = 150f; // Быстро, для чтения
    private static final float DPI_HIGH = 300f;     // HD, если включено улучшение
    private static final double UPSCALE_FACTOR = 1.5;

    public void applyResize(File file, CropMode cropMode, boolean upscale, boolean binarization, boolean skipFirstPage, boolean smartCrop) throws Exception {
        // Выбираем DPI в зависимости от настройки
        float currentDpi = upscale ? DPI_HIGH : DPI_STANDARD;
        String modeName = upscale ? "HD Quality (300 DPI + Upscale)" : "Standard Quality (150 DPI)";

        System.out.println("   [LOG] Открытие файла: " + file.getName());
        System.out.println("   [MODE] Режим: " + modeName);

        // Чистим память перед стартом
        System.gc();

        try (PDDocument sourceDoc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Используем temp file для экономии RAM
            try (PDDocument newDoc = new PDDocument(org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                PDFRenderer renderer = new PDFRenderer(sourceDoc);
                int totalPages = sourceDoc.getNumberOfPages();

                for (int i = 0; i < totalPages; i++) {
                    // Периодическая очистка мусора
                    if (i % 10 == 0) System.gc();

                    // --- 1. ПРОВЕРКА НА РЕКЛАМУ (Работает всегда) ---
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String pageText = stripper.getText(sourceDoc).toLowerCase();

                    if (pageText.contains(WATERMARK_TEXT)) {
                        System.out.println("   [DEL] Страница " + (i + 1) + " удалена (реклама).");
                        continue;
                    }

                    // --- 2. ПРОПУСК ОБЛОЖКИ ---
                    if (skipFirstPage && i == 0) {
                        newDoc.addPage(sourceDoc.getPage(i));
                        continue;
                    }

                    System.out.println("   [PROC] Стр. " + (i + 1) + " -> Рендеринг...");

                    // --- 3. РЕНДЕРИНГ (DPI зависит от настроек) ---
                    BufferedImage image = renderer.renderImageWithDPI(i, currentDpi, ImageType.RGB);

                    // --- 4. УЛУЧШЕНИЕ КАЧЕСТВА (Только если выбрано в настройках) ---
                    if (upscale) {
                        System.out.println("      [HD+] Upscale x1.5...");
                        image = upscaleImageHighQuality(image);
                    }

                    // --- 5. АВТО-ОБРЕЗКА (Работает всегда, если выбрано smartCrop) ---
                    if (smartCrop) {
                        image = autoCrop(image);
                    }

                    // --- 6. ФИЛЬТРЫ (Ч/Б) ---
                    imageProcessor.setBinarization(binarization);
                    BufferedImage processed = imageProcessor.process(image);
                    image.flush(); // Освобождаем память

                    // --- 7. СОХРАНЕНИЕ СТРАНИЦЫ ---
                    PDPage newPage = new PDPage(new PDRectangle(processed.getWidth(), processed.getHeight()));
                    newDoc.addPage(newPage);

                    PDImageXObject pdImage = LosslessFactory.createFromImage(newDoc, processed);
                    try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                        contentStream.drawImage(pdImage, 0, 0);
                    }
                    processed.flush();
                }

                // Добавляем пометку _HD к имени файла только если был upscale
                String suffix = upscale ? "_HD.pdf" : "_fixed.pdf";
                String newPath = file.getAbsolutePath().replace(".pdf", suffix);

                newDoc.save(new File(newPath));
                System.out.println("   [DONE] Готово: " + newPath);
            }
        } catch (OutOfMemoryError e) {
            System.err.println("   [MEM] Ошибка памяти! Попробуйте добавить -Xmx4G");
            throw new Exception("Нехватка памяти (Out Of Memory)");
        }
    }

    /**
     * Качественное увеличение (Bicubic).
     * Вызывается ТОЛЬКО если upscale = true.
     */
    private BufferedImage upscaleImageHighQuality(BufferedImage source) {
        int newW = (int) (source.getWidth() * UPSCALE_FACTOR);
        int newH = (int) (source.getHeight() * UPSCALE_FACTOR);

        BufferedImage resized = new BufferedImage(newW, newH, source.getType());
        Graphics2D g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();

        return resized;
    }

    // --- ЛОГИКА ОБРЕЗКИ (Остается без изменений) ---
    private BufferedImage autoCrop(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int top = 0, bottom = height - 1, left = 0, right = width - 1;
        int whiteThreshold = 230;

        // Поиск границ
        for (int y = 0; y < height; y++) { if (!isRowWhite(source, y, width, whiteThreshold)) { top = y; break; } }
        for (int y = height - 1; y >= 0; y--) { if (!isRowWhite(source, y, width, whiteThreshold)) { bottom = y; break; } }
        for (int x = 0; x < width; x++) { if (!isColWhite(source, x, top, bottom, whiteThreshold)) { left = x; break; } }
        for (int x = width - 1; x >= 0; x--) { if (!isColWhite(source, x, top, bottom, whiteThreshold)) { right = x; break; } }

        if (left >= right || top >= bottom) return source; // Пустая страница

        // Отступы
        int padding = 20;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width, right + padding);
        bottom = Math.min(height, bottom + padding);

        return source.getSubimage(left, top, right - left, bottom - top);
    }

    private boolean isRowWhite(BufferedImage img, int y, int width, int threshold) {
        for (int x = 0; x < width; x += 10) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isColWhite(BufferedImage img, int x, int startY, int endY, int threshold) {
        for (int y = startY; y <= endY; y += 10) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isPixelWhite(int rgb, int threshold) {
        return ((rgb >> 16) & 0xFF) > threshold && ((rgb >> 8) & 0xFF) > threshold && (rgb & 0xFF) > threshold;
    }
}