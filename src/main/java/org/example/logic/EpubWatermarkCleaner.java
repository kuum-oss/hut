package org.example.logic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EpubWatermarkCleaner {

    // Текст, который нужно удалить
    private static final String WATERMARK = "oceanofpdf.com";

    public void clean(File input, File output) throws IOException {
        // EPUB - это просто ZIP архив. Мы будем перекладывать файлы из старого в новый.
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {

            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                // Создаем запись в новом архиве с тем же именем
                ZipEntry newEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);

                // Если это файл с текстом книги (html/xhtml) - чистим его
                if (isHtml(entry.getName())) {
                    processTextFile(zis, zos);
                } else {
                    // Если это картинка, обложка, шрифт или метаданные - просто копируем
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }

                zos.closeEntry();
            }
        }
    }

    private void processTextFile(ZipInputStream zis, ZipOutputStream zos) throws IOException {
        // Читаем весь текстовый файл в память
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }

        // Преобразуем в строку
        String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        // --- УДАЛЕНИЕ РЕКЛАМЫ ---
        if (content.toLowerCase().contains(WATERMARK)) {
            // Удаляем "oceanofpdf.com" и пробелы вокруг
            content = content.replaceAll("(?i)\\s*oceanofpdf\\.com\\s*", "");
        }

        // Записываем обратно в архив
        byte[] cleanedBytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(cleanedBytes);
    }

    private boolean isHtml(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm");
    }
}