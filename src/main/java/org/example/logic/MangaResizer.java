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

    private static final float DPI_STANDARD = 144f;
    private static final float DPI_HIGH = 300f;
    private static final double UPSCALE_FACTOR = 1.5;

    public void applyResize(File file, File outFile, CropMode cropMode, boolean upscale, boolean binarization, boolean skipFirstPage, boolean smartCrop) throws Exception {
        float currentDpi = upscale ? DPI_HIGH : DPI_STANDARD;
        String modeName = upscale ? "HD Quality" : "Turbo Mode";

        System.out.println("   [LOG] Открытие: " + file.getName());
        System.out.println("   [MODE] " + modeName + " + Pattern Eraser | Crop: " + cropMode);

        int oddPagesDeletedStreak = 0;
        boolean patternLocked = false;
        int processedPagesCount = 0; // Считаем только реально обработанные страницы
        
        boolean autoEmptyCheckEnabled = false;
        int emptyCheckCount = 0;

        // Принудительная чистка перед стартом
        System.gc();

        try (PDDocument sourceDoc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Создаем новый чистый документ
            try (PDDocument newDoc = new PDDocument(org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                PDFRenderer renderer = new PDFRenderer(sourceDoc);
                int totalPages = sourceDoc.getNumberOfPages();

                for (int i = 0; i < totalPages; i++) {
                    int pageNum = i + 1;
                    boolean isOdd = (pageNum % 2 != 0);

                    // --- 0. ОБЛОЖКА (Всегда сохраняем первую страницу в цвете) ---
                    if (skipFirstPage && i == 0) {
                        System.out.println("   [KEEP] Обложка сохранена (Original Color).");
                        newDoc.addPage(sourceDoc.getPage(i));
                        continue;
                    }

                    // --- 1. АГРЕССИВНОЕ УДАЛЕНИЕ ПО ПАТТЕРНУ ---
                    // Если паттерн найден и страница нечетная - мы её даже не загружаем в память.
                    // Пропуск занимает 0.000001 сек.
                    if (patternLocked && isOdd) {
                        continue;
                    }

                    // --- 2. ПОИСК ЗАКОНОМЕРНОСТИ (Только в начале книги) ---
                    boolean isAd = false;

                    if (!patternLocked) {
                        stripper.setStartPage(pageNum);
                        stripper.setEndPage(pageNum);
                        String pageText = stripper.getText(sourceDoc).toLowerCase();

                        if (pageText.contains(WATERMARK_TEXT)) {
                            isAd = true;
                        }
                    }

                    if (isAd) {
                        System.out.println("   [DEL] Стр. " + pageNum + " удалена (реклама).");

                        if (isOdd) {
                            oddPagesDeletedStreak++;
                            // Если 5 раз подряд удалили нечетную -> ВКЛЮЧАЕМ РЕЖИМ "Pattern Eraser"
                            if (oddPagesDeletedStreak >= 5) {
                                patternLocked = true;
                                System.out.println("   [!!!] ПАТТЕРН ПОДТВЕРЖДЕН. Все нечетные страницы будут уничтожены мгновенно.");
                            }
                        }
                        continue; // Идем к следующей странице
                    } else {
                        // Если цепочка прервалась (встретили полезную нечетную страницу)
                        if (isOdd && !patternLocked) {
                            oddPagesDeletedStreak = 0;
                        }
                    }

                    // --- 3. ОБРАБОТКА ПОЛЕЗНОЙ СТРАНИЦЫ ---
                    // Этот код выполняется ТОЛЬКО для полезных страниц

                    if (processedPagesCount % 10 == 0) {
                        System.out.println("   [PROC] Обработка стр. " + pageNum + " / " + totalPages);
                    }

                    BufferedImage image = renderer.renderImageWithDPI(i, currentDpi, ImageType.RGB);

                    // --- 4. ПРОВЕРКА НА ПУСТУЮ СТРАНИЦУ (Watermark check) ---
                    if (emptyCheckCount < 10 || autoEmptyCheckEnabled) {
                        if (isImageEmpty(image)) {
                            System.out.println("   [DEL] Стр. " + pageNum + " удалена (пустая/только вотермарка).");
                            image.flush();
                            if (emptyCheckCount < 10) emptyCheckCount++;
                            if (emptyCheckCount == 10) autoEmptyCheckEnabled = true;
                            continue;
                        }
                    }

                    if (upscale) {
                        image = resizeImage(image, UPSCALE_FACTOR, true);
                    }

                    if (cropMode == CropMode.SMART) {
                        image = autoCropFast(image);
                    } else if (cropMode == CropMode.MANUAL_4_CRIT) {
                        image = manualCrop4(image);
                    }

                    imageProcessor.setBinarization(binarization);
                    BufferedImage processed = imageProcessor.process(image);
                    image.flush();

                    PDPage newPage = new PDPage(new PDRectangle(processed.getWidth(), processed.getHeight()));
                    newDoc.addPage(newPage);

                    PDImageXObject pdImage = LosslessFactory.createFromImage(newDoc, processed);
                    try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                        contentStream.drawImage(pdImage, 0, 0);
                    }
                    processed.flush();

                    // ОПТИМИЗАЦИЯ ПАМЯТИ:
                    // Чистим память только если мы реально обработали 20 картинок.
                    // Раньше мы чистили память даже при пропуске рекламы, это вызывало тормоза.
                    processedPagesCount++;
                    if (processedPagesCount % 20 == 0) {
                        System.gc();
                    }
                }

                newDoc.save(outFile);
                System.out.println("   [DONE] Готово: " + outFile.getAbsolutePath());
            }
        } catch (OutOfMemoryError e) {
            System.err.println("   [MEM] Ошибка памяти! Попробуйте флаги -Xmx4G");
            throw new Exception("Out Of Memory");
        }
    }

    private BufferedImage resizeImage(BufferedImage source, double factor, boolean highQuality) {
        int newW = (int) (source.getWidth() * factor);
        int newH = (int) (source.getHeight() * factor);
        BufferedImage resized = new BufferedImage(newW, newH, source.getType());
        Graphics2D g = resized.createGraphics();

        if (highQuality) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    private BufferedImage autoCropFast(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int top = 0, bottom = height - 1, left = 0, right = width - 1;
        int whiteThreshold = 230;
        int scanStep = 30;

        for (int y = 0; y < height; y += scanStep) {
            if (!isRowWhite(source, y, width, whiteThreshold)) { top = Math.max(0, y - scanStep); break; }
        }
        for (int y = height - 1; y >= 0; y -= scanStep) {
            if (!isRowWhite(source, y, width, whiteThreshold)) { bottom = Math.min(height - 1, y + scanStep); break; }
        }
        for (int x = 0; x < width; x += scanStep) {
            if (!isColWhite(source, x, top, bottom, whiteThreshold)) { left = Math.max(0, x - scanStep); break; }
        }
        for (int x = width - 1; x >= 0; x -= scanStep) {
            if (!isColWhite(source, x, top, bottom, whiteThreshold)) { right = Math.min(width - 1, x + scanStep); break; }
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
        for (int x = 0; x < width; x += 20) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isColWhite(BufferedImage img, int x, int startY, int endY, int threshold) {
        for (int y = startY; y <= endY; y += 20) if (!isPixelWhite(img.getRGB(x, y), threshold)) return false;
        return true;
    }
    private boolean isPixelWhite(int rgb, int threshold) {
        return ((rgb >> 16) & 0xFF) > threshold && ((rgb >> 8) & 0xFF) > threshold && (rgb & 0xFF) > threshold;
    }

    private boolean isImageEmpty(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int whiteThreshold = 240;
        int step = 15;
        
        // Проверяем наличие темных пикселей (контента)
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                if (!isPixelWhite(img.getRGB(x, y), whiteThreshold)) {
                    return false; // Нашли контент
                }
            }
        }
        return true; // Контента нет
    }

    private BufferedImage manualCrop4(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        
        // Обычная обрезка по 4 критериям (статичные поля 5%)
        int cropW = (int) (width * 0.05);
        int cropH = (int) (height * 0.05);
        
        int x = cropW;
        int y = cropH;
        int w = width - 2 * cropW;
        int h = height - 2 * cropH;
        
        if (w <= 0 || h <= 0) return source;
        
        return source.getSubimage(x, y, w, h);
    }
}