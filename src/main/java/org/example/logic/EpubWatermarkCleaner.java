package org.example.logic;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class EpubWatermarkCleaner {
    private static final String WATERMARK = "oceanofpdf.com";

    public void clean(File input, File output) throws IOException {
        Book book;
        try (InputStream in = new FileInputStream(input)) {
            book = new EpubReader().readEpub(in);
        }
        Collection<Resource> resources = book.getResources().getAll();
        for (Resource res : resources) {
            if (!isHtml(res)) continue;
            String originalHtml = new String(res.getData(), StandardCharsets.UTF_8);
            if (!originalHtml.toLowerCase().contains(WATERMARK)) continue;
            String cleanedHtml = originalHtml.replaceAll("(?i)\\s*oceanofpdf\\.com\\s*", "");
            res.setData(cleanedHtml.getBytes(StandardCharsets.UTF_8));
        }
        if (book.getCoverImage() != null) {
            book.setCoverImage(book.getCoverImage());
        }
        try (OutputStream out = new FileOutputStream(output)) {
            new EpubWriter().write(book, out);
        }
    }

    private boolean isHtml(Resource res) {
        String href = res.getHref();
        if (href == null) return false;
        String lower = href.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm");
    }
}