package org.example.logic;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.model.CropMode;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class MangaResizer {

    private final MangaImageProcessor imageProcessor = new MangaImageProcessor();

    public BufferedImage getPreviewImage(File file) {
        try (PDDocument doc = PDDocument.load(file)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            return renderer.renderImageWithDPI(0, 150, ImageType.RGB);
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    /**
     * Основной метод ресайза.
     */
    public void applyResize(File file, CropMode cropMode, boolean upscale, boolean binarization, boolean skipFirstPage, boolean smartCrop) throws Exception {

        try (PDDocument sourceDoc = PDDocument.load(file)) {
            try (PDDocument newDoc = new PDDocument()) {

                PDFRenderer renderer = new PDFRenderer(sourceDoc);
                int totalPages = sourceDoc.getNumberOfPages();

                // Переменная для хранения "Эталонного кропа" (для режима SmartCrop = OFF)
                Rectangle fixedCropBox = null;

                for (int i = 0; i < totalPages; i++) {

                    // 1. ЗАЩИТА ОБЛОЖКИ (если включено и это 1-я стр)
                    if (i == 0 && skipFirstPage) {
                        newDoc.importPage(sourceDoc.getPage(i));
                        continue;
                    }

                    // 2. Рендеринг страницы
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, 300, ImageType.RGB);

                    // 3. ВЫЧИСЛЕНИЕ ОБРЕЗКИ (CROP)
                    Rectangle cropRect;

                    if (smartCrop) {
                        // РЕЖИМ 1: Агрессивный умный кроп (для каждой страницы свой)
                        cropRect = calculateContentBounds(pageImage);
                    } else {
                        // РЕЖИМ 2: Статичный кроп (по 2-й странице)

                        // Если режим "Без изменений" (SKIP), то не обрезаем вообще
                        if (cropMode == CropMode.SKIP) {
                            cropRect = new Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight());
                        } else {
                            // Иначе пытаемся вычислить эталон по 2-й странице
                            if (fixedCropBox == null) {
                                // Вычисляем ТОЛЬКО если это не обложка (начиная со 2-й стр, i >= 1)
                                // Если файл всего из 1 стр, придется вычислять по ней (i==0)
                                if (i >= 1 || totalPages == 1) {
                                    fixedCropBox = calculateContentBounds(pageImage);
                                    if (fixedCropBox != null) {
                                        System.out.println("Эталон обрезки зафиксирован по стр " + (i+1));
                                    }
                                }
                            }

                            // Применяем эталон
                            if (fixedCropBox != null) {
                                cropRect = fixedCropBox;
                            } else {
                                // Если эталон еще не найден (например, мы на 1-й стр, а эталон ищем со 2-й)
                                // пока берем полную картинку
                                cropRect = new Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight());
                            }
                        }
                    }

                    // Страховка: если алгоритм вернул null или мусор — берем оригинал
                    if (cropRect == null || cropRect.width < 50 || cropRect.height < 50) {
                        cropRect = new Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight());
                    }

                    // Дополнительная страховка выхода за границы (на случай FixedCrop > текущей картинки)
                    cropRect = clampRect(cropRect, pageImage.getWidth(), pageImage.getHeight());

                    // 4. ФИЗИЧЕСКАЯ ОБРЕЗКА
                    BufferedImage croppedImage = pageImage.getSubimage(cropRect.x, cropRect.y, cropRect.width, cropRect.height);

                    // 5. ОБРАБОТКА (Ч/Б, Апскейл)
                    imageProcessor.setBinarization(binarization);
                    BufferedImage finalImage = imageProcessor.process(croppedImage);

                    // 6. СОХРАНЕНИЕ В PDF
                    PDPage newPage = new PDPage(new PDRectangle(finalImage.getWidth(), finalImage.getHeight()));
                    newDoc.addPage(newPage);

                    PDImageXObject pdImage = LosslessFactory.createFromImage(newDoc, finalImage);
                    try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                        contentStream.drawImage(pdImage, 0, 0);
                    }
                }
                newDoc.save(file);
            }
        }
    }

    // Вспомогательный метод: ищем границы контента
    private Rectangle calculateContentBounds(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean found = false;
        int threshold = 235; // Чувствительность к белому (255 - белый)

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;

                // Если пиксель не белый
                if (r < threshold || g < threshold || b < threshold) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }

        if (!found) return null; // Пустой белый лист

        // Добавляем padding (отступы), чтобы не резать вплотную к тексту
        int padding = 15;
        minX = Math.max(0, minX - padding);
        minY = Math.max(0, minY - padding);
        maxX = Math.min(width, maxX + padding);
        maxY = Math.min(height, maxY + padding);

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    // Защита от выхода за пределы картинки (важно для Fixed Mode)
    private Rectangle clampRect(Rectangle rect, int imgW, int imgH) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int w = Math.min(imgW - x, rect.width);
        int h = Math.min(imgH - y, rect.height);
        return new Rectangle(x, y, w, h);
    }
}