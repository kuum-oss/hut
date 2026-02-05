package org.example.logic;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PdfWatermarkCleaner {

    private static final String WATERMARK_TEXT = "oceanofpdf";

    public void clean(File input, File output) throws Exception {
        try (PDDocument doc = PDDocument.load(input)) {

            int totalPages = doc.getNumberOfPages();
            List<Integer> pagesToRemove = new ArrayList<>();

            // Проходим по всем страницам
            for (int i = 0; i < totalPages; i++) {
                PDPage page = doc.getPage(i);

                // 1. Получаем ВЕСЬ текст со страницы
                PDFTextStripper textStripper = new PDFTextStripper();
                textStripper.setStartPage(i + 1);
                textStripper.setEndPage(i + 1);
                String pageText = textStripper.getText(doc).toLowerCase();

                // 2. Логика УДАЛЕНИЯ страницы (только начало и конец книги)
                boolean isJunkPage = false;
                if (pageText.contains(WATERMARK_TEXT)) {
                    // Если это первые 2 страницы или последние 2 страницы
                    if (i <= 1 || i >= totalPages - 2) {
                        isJunkPage = true;
                    }
                    // Если текста очень мало (меньше 200 символов) и там есть реклама - это мусор
                    if (pageText.length() < 200) {
                        isJunkPage = true;
                    }
                }

                if (isJunkPage) {
                    pagesToRemove.add(i);
                    continue; // Если удаляем страницу, замазывать на ней ничего не надо
                }

                // 3. Если страницу не удалили, ищем ГДЕ находится текст и замазываем его
                if (pageText.contains(WATERMARK_TEXT)) {
                    WatermarkLocator locator = new WatermarkLocator();
                    locator.setSortByPosition(true);
                    locator.setStartPage(i + 1);
                    locator.setEndPage(i + 1);
                    locator.writeText(doc, new OutputStreamWriter(new ByteArrayOutputStream()));

                    if (!locator.getFoundAreas().isEmpty()) {
                        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                            cs.setNonStrokingColor(Color.WHITE);
                            for (PDRectangle rect : locator.getFoundAreas()) {
                                // Рисуем белый прямоугольник поверх текста
                                cs.addRect(0, page.getMediaBox().getHeight() - rect.getLowerLeftY() - 2, page.getMediaBox().getWidth(), rect.getHeight() + 5);
                                cs.fill();
                            }
                        }
                    }
                }

                // 4. Удаляем кликабельные ссылки
                List<PDAnnotation> annotations = page.getAnnotations();
                List<PDAnnotation> toRemove = new ArrayList<>();
                for (PDAnnotation ann : annotations) {
                    if (ann instanceof PDAnnotationLink) {
                        PDAnnotationLink link = (PDAnnotationLink) ann;
                        if (link.getAction() instanceof PDActionURI) {
                            PDActionURI uri = (PDActionURI) link.getAction();
                            if (uri.getURI() != null && uri.getURI().toLowerCase().contains(WATERMARK_TEXT)) {
                                toRemove.add(ann);
                            }
                        }
                    }
                }
                annotations.removeAll(toRemove);
            }

            // Удаляем страницы с конца списка, чтобы не сбить нумерацию
            Collections.sort(pagesToRemove, Collections.reverseOrder());
            for (int p : pagesToRemove) {
                if (doc.getNumberOfPages() > p) { // Проверка на всякий случай
                    doc.removePage(p);
                }
            }

            doc.save(output);
        }
    }

    // Вспомогательный класс для поиска координат текста
    private static class WatermarkLocator extends PDFTextStripper {
        private final List<PDRectangle> foundAreas = new ArrayList<>();

        public WatermarkLocator() throws java.io.IOException { super(); }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (text != null && text.toLowerCase().contains(WATERMARK_TEXT)) {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

                for (TextPosition pos : textPositions) {
                    if (pos.getXDirAdj() < minX) minX = pos.getXDirAdj();
                    if (pos.getYDirAdj() < minY) minY = pos.getYDirAdj();
                    if (pos.getXDirAdj() + pos.getWidthDirAdj() > maxX) maxX = pos.getXDirAdj() + pos.getWidthDirAdj();
                    if (pos.getYDirAdj() + pos.getHeightDir() > maxY) maxY = pos.getYDirAdj() + pos.getHeightDir();
                }

                if (minX != Float.MAX_VALUE) {
                    foundAreas.add(new PDRectangle(minX, maxY, maxX - minX, maxY - minY));
                }
            }
        }
        public List<PDRectangle> getFoundAreas() { return foundAreas; }
    }
}