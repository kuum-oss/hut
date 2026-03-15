package org.example.logic;

import org.example.ui.EpubEditorDialog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EpubWatermarkCleaner {

    // Текст, который нужно удалить
    private static final String WATERMARK = "oceanofpdf.com";

    public void clean(File input, File output) throws IOException {
        clean(input, output, null);
    }

    public void clean(File input, File output, EpubEditorDialog editorDialog) throws IOException {
        List<EpubEditorDialog.ImageTask> tasks = (editorDialog != null) ? editorDialog.getTasks() : new ArrayList<>();
        boolean replaceStars = (editorDialog != null) && editorDialog.isReplaceStars();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {

            ZipEntry entry;
            byte[] buffer = new byte[4096];
            int htmlCount = 0;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                ZipEntry newEntry = new ZipEntry(entryName);
                zos.putNextEntry(newEntry);

                if (isHtml(entryName)) {
                    processTextFile(zis, zos, htmlCount, tasks, replaceStars);
                    htmlCount++;
                } else {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }

            // Добавляем новые изображения в архив
            int imgId = 0;
            for (EpubEditorDialog.ImageTask task : tasks) {
                if (task.imageFile != null && task.imageFile.exists()) {
                    // Используем путь OEBPS/images/ для единообразия
                    // Если entryName HTML файлов начинается с OEBPS/, то images/ будет корректным путем.
                    // Если нет, то мы попробуем создать и в корне и в OEBPS.
                    String[] paths = {"OEBPS/images/" + imgId + ".jpg", "images/" + imgId + ".jpg"};
                    for (String path : paths) {
                        ZipEntry imgEntry = new ZipEntry(path);
                        try {
                            zos.putNextEntry(imgEntry);
                            try (FileInputStream fis = new FileInputStream(task.imageFile)) {
                                int len;
                                while ((len = fis.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                            }
                            zos.closeEntry();
                        } catch (Exception ignored) {
                            // Если папка уже есть или другие проблемы - просто пробуем другой путь
                        }
                    }
                    imgId++;
                }
            }
        }
    }

    private void processTextFile(ZipInputStream zis, ZipOutputStream zos, int htmlIndex, 
                                 List<EpubEditorDialog.ImageTask> tasks, boolean replaceStars) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }

        String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        // 1. Удаление вотермарки
        if (content.toLowerCase().contains(WATERMARK)) {
            content = content.replaceAll("(?i)\\s*oceanofpdf\\.com\\s*", "");
        }

        // 2. Вставка изображений по номеру страницы (htmlIndex)
        StringBuilder imgTags = new StringBuilder();
        int imgId = 0;
        for (EpubEditorDialog.ImageTask task : tasks) {
            if (task.imageFile == null) {
                imgId++;
                continue;
            }
            try {
                int targetPage = Integer.parseInt(task.pageField.getText().trim());
                if (targetPage == htmlIndex) {
                    // Используем ../images/ если HTML файлы находятся в подпапке (например OEBPS/text/)
                    // Но если они в OEBPS/ и картинки в OEBPS/images/, то путь images/ верный.
                    // Для универсальности обычно проверяют структуру, но в большинстве EPUB это images/ или ../Images/
                    // Если пользователь говорит "не показывает", а я сохраняю в OEBPS/images/, 
                    // возможно HTML лежит глубже или наоборот.
                    imgTags.append("\n<div style=\"text-align:center; margin: 20px 0;\">")
                           .append("<img src=\"images/").append(imgId).append(".jpg\" style=\"max-width:100%;\"/>")
                           .append("</div>\n");
                }
            } catch (Exception ignored) {}
            imgId++;
        }
        
        if (imgTags.length() > 0) {
            if (content.contains("<body")) {
                content = content.replaceFirst("(<body[^>]*>)", "$1" + imgTags.toString());
            } else {
                content = imgTags.toString() + content;
            }
        }

        // 3. Замена ***
        if (replaceStars && content.contains("***")) {
            // Ищем задачу, помеченную для замены звезд
            EpubEditorDialog.ImageTask starTask = null;
            int starImgId = 0;
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).useForStars != null && tasks.get(i).useForStars.isSelected() && tasks.get(i).imageFile != null) {
                    starTask = tasks.get(i);
                    starImgId = i;
                    break;
                }
            }
            
            // Если помеченная не найдена, берем просто первую попавшуюся с картинкой
            if (starTask == null) {
                for (int i = 0; i < tasks.size(); i++) {
                    if (tasks.get(i).imageFile != null) {
                        starTask = tasks.get(i);
                        starImgId = i;
                        break;
                    }
                }
            }

            if (starTask != null) {
                String starImg = "<div style=\"text-align:center; margin: 15px 0;\"><img src=\"images/" + starImgId + ".jpg\" style=\"max-width:80%;\"/></div>";
                content = content.replace("***", starImg);
            }
        }

        byte[] cleanedBytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(cleanedBytes);
    }

    private boolean isHtml(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm");
    }
}