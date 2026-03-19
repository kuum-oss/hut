package org.example.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;
import org.example.logic.EpubWatermarkCleaner;
import org.example.logic.MangaResizer;
import org.example.model.CropMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.geom.Arc2D;
import java.io.File;
import java.util.List;

public class MangaCleanerApp extends JFrame {

    private final MangaResizer mangaResizer = new MangaResizer();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();
    private EpubEditorDialog epubEditorDialog;

    // Компоненты UI
    private JPanel dropPanel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private CircleLoader circleLoader;
    private JCheckBox hdModeCheckBox;
    private JCheckBox colorModeCheckBox;
    private JComboBox<String> cropModeComboBox;

    // Современная цветовая палитра
    private static final Color ACCENT_COLOR = new Color(33, 150, 243); // Material Blue
    private static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    private static final Color TEXT_MAIN = new Color(44, 62, 80);
    private static final Color TEXT_MUTED = new Color(127, 140, 141);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);

    // Шрифты
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 26);
    private static final Font FONT_TEXT = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 13);

    public MangaCleanerApp() {
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {}

        setTitle("Manga Cleaner v19 - Color Cover Support");
        setSize(1000, 750);
        setMinimumSize(new Dimension(850, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        epubEditorDialog = new EpubEditorDialog(this);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(new EmptyBorder(30, 35, 25, 35));

        JPanel topContainer = new JPanel(new BorderLayout(0, 20));
        topContainer.setOpaque(false);
        topContainer.add(createHeader(), BorderLayout.NORTH);
        topContainer.add(createSettingsPanel(), BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);
        dropPanel = createDropZone();
        mainPanel.add(dropPanel, BorderLayout.CENTER);
        mainPanel.add(createFooter(), BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("✨ Оптимизация Манги и Книг");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);

        JLabel subtitle = new JLabel("PDF (Чистка + Обложка в цвете) | EPUB (Удаление рекламы)");
        subtitle.setFont(FONT_TEXT);
        subtitle.setForeground(TEXT_MUTED);

        header.add(title);
        header.add(subtitle);

        JButton epubEditBtn = new JButton("📚 Редактор EPUB");
        epubEditBtn.setFont(FONT_BOLD);
        epubEditBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #2ecc71; foreground: #ffffff;");
        epubEditBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        epubEditBtn.addActionListener(e -> epubEditorDialog.setVisible(true));

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(epubEditBtn);

        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.setOpaque(false);
        headerContainer.add(header, BorderLayout.WEST);
        headerContainer.add(rightHeader, BorderLayout.EAST);

        return headerContainer;
    }

    private JPanel createSettingsPanel() {
        // Обертка в виде "карточки" для настроек
        JPanel cardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 15));
        cardPanel.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #ffffff;");
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(5, 10, 5, 10)
        ));

        hdModeCheckBox = new JCheckBox("💎 HD Upscale");
        hdModeCheckBox.setFont(FONT_TEXT);
        hdModeCheckBox.setForeground(TEXT_MAIN);
        hdModeCheckBox.setOpaque(false);
        hdModeCheckBox.setFocusPainted(false);
        hdModeCheckBox.setToolTipText("Улучшить качество изображения (медленнее)");

        colorModeCheckBox = new JCheckBox("🎨 Оставить цвет (Комиксы)");
        colorModeCheckBox.setFont(FONT_TEXT);
        colorModeCheckBox.setForeground(ACCENT_COLOR);
        colorModeCheckBox.setOpaque(false);
        colorModeCheckBox.setFocusPainted(false);
        colorModeCheckBox.setToolTipText("Не делать ч/б для цветных комиксов");

        JLabel cropLabel = new JLabel("✂️ Режим обрезки:");
        cropLabel.setFont(FONT_TEXT);
        cropLabel.setForeground(TEXT_MAIN);

        String[] modes = {"❌ Без обрезки", "🤖 Умный авто-кроп", "📏 Обычная (4 критерия)"};
        cropModeComboBox = new JComboBox<>(modes);
        cropModeComboBox.setFont(FONT_SMALL);
        cropModeComboBox.setSelectedIndex(1);
        cropModeComboBox.setFocusable(false);

        cardPanel.add(hdModeCheckBox);
        cardPanel.add(colorModeCheckBox);
        cardPanel.add(Box.createRigidArea(new Dimension(20, 0))); // Разделитель
        cardPanel.add(cropLabel);
        cardPanel.add(cropModeComboBox);

        return cardPanel;
    }

    private JPanel createDropZone() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.putClientProperty(FlatClientProperties.STYLE, "arc: 25; background: #ffffff;");
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(Color.GRAY, 3, 10, 3, true),
                new EmptyBorder(40, 40, 40, 40)
        ));

        JPanel loaderContainer = new JPanel(new BorderLayout());
        loaderContainer.setOpaque(false);
        circleLoader = new CircleLoader();
        circleLoader.setVisible(false);
        loaderContainer.add(circleLoader, BorderLayout.CENTER);

        JLabel infoLabel = new JLabel("🚀 Перетащите файлы сюда 📁");
        infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        infoLabel.setForeground(ACCENT_COLOR);

        JLabel hintLabel = new JLabel("Поддерживаются форматы PDF и EPUB");
        hintLabel.setFont(FONT_TEXT);
        hintLabel.setForeground(TEXT_MUTED);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(infoLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(10, 0, 0, 0);
        panel.add(hintLabel, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(30, 0, 0, 0);
        panel.add(loaderContainer, gbc);

        new DropTarget(panel, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (!circleLoader.isVisible()) panel.setBackground(new Color(240, 248, 255)); // Alice Blue
            }
            @Override
            public void dragExit(DropTargetEvent dte) {
                panel.putClientProperty(FlatClientProperties.STYLE, "arc: 25; background: #ffffff;");
            }
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                panel.putClientProperty(FlatClientProperties.STYLE, "arc: 25; background: #ffffff;");
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) startProcessing(files);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout(15, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 0, 0, 0));

        statusLabel = new JLabel("✅ Готов к работе");
        statusLabel.setFont(FONT_TEXT);
        statusLabel.setForeground(TEXT_MAIN);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(350, 24)); // Сделали чуть толще
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        progressBar.setFont(FONT_BOLD);
        progressBar.setBackground(BORDER_COLOR);
        progressBar.setForeground(ACCENT_COLOR);
        progressBar.setBorderPainted(false); // Убираем некрасивую стандартную рамку

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(progressBar, BorderLayout.EAST);
        return footer;
    }

    private void startProcessing(List<File> files) {
        boolean useUpscale = hdModeCheckBox.isSelected();
        boolean keepColor = colorModeCheckBox.isSelected();
        int cropIdx = cropModeComboBox.getSelectedIndex();
        CropMode selectedCropMode = CropMode.SMART;
        if (cropIdx == 0) selectedCropMode = CropMode.SKIP;
        else if (cropIdx == 2) selectedCropMode = CropMode.MANUAL_4_CRIT;

        final CropMode finalCropMode = selectedCropMode;
        toggleUI(true);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (files.isEmpty()) return null;

                File parentDir = files.get(0).getParentFile();
                File outputDir = new File(parentDir, "Cleaned");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                for (int i = 0; i < files.size(); i++) {
                    File file = files.get(i);
                    String fileName = file.getName().toLowerCase();
                    updateStatus("Обработка: " + file.getName());

                    try {
                        if (fileName.endsWith(".pdf")) {
                            System.out.println(">>> Режим PDF: " + fileName + " | HD: " + useUpscale);
                            String outName = file.getName().replace(".pdf", "_cleaned.pdf");
                            File outFile = new File(outputDir, outName);

                            mangaResizer.applyResize(
                                    file,
                                    outFile,
                                    finalCropMode,
                                    useUpscale,
                                    !keepColor,
                                    true,
                                    finalCropMode != CropMode.SKIP
                            );
                        }
                        else if (fileName.endsWith(".epub")) {
                            System.out.println(">>> Режим EPUB: " + fileName);
                            String outName = file.getName().replace(".epub", "_cleaned.epub");
                            File outFile = new File(outputDir, outName);
                            epubCleaner.clean(file, outFile, epubEditorDialog);
                        }
                        else {
                            System.out.println(">>> Пропуск: " + fileName);
                        }

                    } catch (Throwable e) {
                        System.err.println("ОШИБКА с файлом " + fileName);
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(MangaCleanerApp.this,
                                "Ошибка: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    }

                    int progress = (int) (((double) (i + 1) / files.size()) * 100);
                    publish(progress);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                toggleUI(false);
                updateStatus("Готово!");
                JOptionPane.showMessageDialog(MangaCleanerApp.this, "Все задачи успешно завершены!", "Успех", JOptionPane.INFORMATION_MESSAGE);
            }
        };
        worker.execute();
    }

    private void toggleUI(boolean processing) {
        progressBar.setVisible(processing);
        circleLoader.setVisible(processing);
        dropPanel.setEnabled(!processing);
        hdModeCheckBox.setEnabled(!processing);
        colorModeCheckBox.setEnabled(!processing);
        cropModeComboBox.setEnabled(!processing);
        if (processing) progressBar.setValue(0);
    }

    private void updateStatus(String text) {
        String sticker = "⏳";
        if (text.startsWith("Готово")) sticker = "🎉";
        else if (text.startsWith("Ошибка")) sticker = "❌";
        else if (text.startsWith("Обработка")) sticker = "⚙️";

        statusLabel.setText(sticker + " " + text);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }

    private static class CircleLoader extends JPanel {
        private int angle = 0;
        private final Timer timer;

        public CircleLoader() {
            setPreferredSize(new Dimension(65, 65));
            setOpaque(false);
            timer = new Timer(16, e -> { angle = (angle + 6) % 360; repaint(); }); // Чуть ускорил анимацию
        }

        @Override
        public void setVisible(boolean b) {
            super.setVisible(b);
            if (b) timer.start(); else timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight()) - 10;
            g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); // Сделал линию чуть толще
            g2.setColor(BORDER_COLOR);
            g2.drawOval((getWidth()-s)/2, (getHeight()-s)/2, s, s);
            g2.setColor(ACCENT_COLOR);
            g2.draw(new Arc2D.Float((getWidth()-s)/2, (getHeight()-s)/2, s, s, 90 - angle, -120, Arc2D.OPEN)); // Увеличил хвост загрузки
            g2.dispose();
        }
    }
}