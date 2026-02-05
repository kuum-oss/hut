package org.example.logic;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.util.TreeSet;
import java.util.Collections;

public class PdfWatermarkCleaner {
    private static final String WATERMARK_TEXT = "oceanofpdf";

    public void clean(File input, File output) throws Exception {
        try (PDDocument doc = PDDocument.load(input)) {
            PDFTextStripper textStripper = new PDFTextStripper(); // Инициализация вне цикла
            int totalPages = doc.getNumberOfPages();
            // Используем TreeSet с обратным порядком, чтобы индексы не смещались при удалении
            TreeSet<Integer> pagesToRemove = new TreeSet<>(Collections.reverseOrder());

            for (int i = 0; i < totalPages; i++) {
                textStripper.setStartPage(i + 1);
                textStripper.setEndPage(i + 1);

                String pageText = textStripper.getText(doc).toLowerCase();
                if (pageText.contains(WATERMARK_TEXT)) {
                    pagesToRemove.add(i);
                }
            }

            for (int pageIdx : pagesToRemove) {
                doc.removePage(pageIdx); // Безопасное удаление
            }
            doc.save(output);
        }
    }
}