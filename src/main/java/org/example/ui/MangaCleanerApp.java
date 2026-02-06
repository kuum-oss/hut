package org.example.ui;

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

    // Логика
    private final MangaResizer mangaResizer = new MangaResizer();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();

    // Компоненты UI
    private JPanel dropPanel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private CircleLoader circleLoader;

    // --- НОВЫЙ КОМПОНЕНТ: ГАЛОЧКА ---
    private JCheckBox hdModeCheckBox;

    // Константы дизайна
    private static final Color ACCENT_COLOR = new Color(0, 120, 215);
    private static final Color BG_COLOR = new Color(245, 245, 245);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TEXT = new Font("Segoe UI", Font.PLAIN, 14);

    public MangaCleanerApp() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setTitle("Manga Cleaner v18 - With Settings");
        setSize(950, 750);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Верхняя часть (Заголовок + Настройки)
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setOpaque(false);
        topContainer.add(createHeader(), BorderLayout.NORTH);
        topContainer.add(createSettingsPanel(), BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // Центр (Drop Zone)
        dropPanel = createDropZone();
        mainPanel.add(dropPanel, BorderLayout.CENTER);

        // Низ (Статус)
        mainPanel.add(createFooter(), BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 5));
        header.setOpaque(false);

        JLabel title = new JLabel("Оптимизация Манги и Книг");
        title.setFont(FONT_TITLE);
        title.setForeground(Color.DARK_GRAY);

        JLabel subtitle = new JLabel("PDF (Чистка + Upscale) | EPUB (Удаление рекламы)");
        subtitle.setFont(FONT_TEXT);
        subtitle.setForeground(Color.GRAY);

        header.add(title);
        header.add(subtitle);
        return header;
    }

    // --- НОВАЯ ПАНЕЛЬ С НАСТРОЙКАМИ ---
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 15));
        panel.setOpaque(false);

        hdModeCheckBox = new JCheckBox("Включить HD Upscale (Медленно, но качественно)");
        hdModeCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 14));
        hdModeCheckBox.setForeground(ACCENT_COLOR);
        hdModeCheckBox.setOpaque(false);
        hdModeCheckBox.setFocusPainted(false);

        // По умолчанию выключено (чтобы работало быстро)
        hdModeCheckBox.setSelected(false);

        panel.add(hdModeCheckBox);
        return panel;
    }

    private JPanel createDropZone() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(ACCENT_COLOR, 2, 5, 2, true),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JPanel loaderContainer = new JPanel(new BorderLayout());
        loaderContainer.setOpaque(false);
        circleLoader = new CircleLoader();
        circleLoader.setVisible(false);
        loaderContainer.add(circleLoader, BorderLayout.CENTER);

        JLabel infoLabel = new JLabel("Перетащите файлы сюда");
        infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        infoLabel.setForeground(ACCENT_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(infoLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(20, 0, 0, 0);
        panel.add(loaderContainer, gbc);

        new DropTarget(panel, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (!circleLoader.isVisible()) panel.setBackground(new Color(230, 245, 255));
            }
            @Override
            public void dragExit(DropTargetEvent dte) {
                panel.setBackground(Color.WHITE);
            }
            @Override
            public void drop(DropTargetDropEvent dtde) {
                panel.setBackground(Color.WHITE);
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
        statusLabel = new JLabel("Готов к работе");
        statusLabel.setFont(FONT_TEXT);
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setVisible(false);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(progressBar, BorderLayout.EAST);
        return footer;
    }

    // --- ЛОГИКА ОБРАБОТКИ ---
    private void startProcessing(List<File> files) {
        // Читаем состояние галочки ПЕРЕД запуском потока
        boolean useUpscale = hdModeCheckBox.isSelected();

        toggleUI(true);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < files.size(); i++) {
                    File file = files.get(i);
                    String fileName = file.getName().toLowerCase();
                    updateStatus("Обработка: " + file.getName());

                    try {
                        if (fileName.endsWith(".pdf")) {
                            System.out.println(">>> Режим PDF: " + fileName + " | HD Mode: " + useUpscale);

                            // Передаем значение useUpscale (true/false) в ресайзер
                            mangaResizer.applyResize(
                                    file,
                                    CropMode.SMART, // Теперь этот enum есть
                                    useUpscale,     // <--- БЕРЕМ ИЗ ГАЛОЧКИ
                                    true,           // binarization (Ч/Б фильтр всегда включен)
                                    false,          // skipFirstPage
                                    true            // smartCrop
                            );
                        }
                        else if (fileName.endsWith(".epub")) {
                            System.out.println(">>> Режим EPUB: " + fileName);
                            String newPath = file.getAbsolutePath().replace(".epub", "_cleaned.epub");
                            epubCleaner.clean(file, new File(newPath));
                        }
                        else {
                            System.out.println(">>> Пропуск: " + fileName);
                        }

                    } catch (Throwable e) {
                        System.err.println("ОШИБКА с файлом " + fileName);
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(MangaCleanerApp.this,
                                "Ошибка: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(MangaCleanerApp.this, "Все задачи завершены!");
            }
        };
        worker.execute();
    }

    private void toggleUI(boolean processing) {
        progressBar.setVisible(processing);
        circleLoader.setVisible(processing);
        dropPanel.setEnabled(!processing);
        hdModeCheckBox.setEnabled(!processing); // Блокируем галочку во время работы
        if (processing) progressBar.setValue(0);
    }

    private void updateStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }

    private static class CircleLoader extends JPanel {
        private int angle = 0;
        private final Timer timer;

        public CircleLoader() {
            setPreferredSize(new Dimension(60, 60));
            setOpaque(false);
            timer = new Timer(16, e -> { angle = (angle + 5) % 360; repaint(); });
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
            g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(230, 230, 230));
            g2.drawOval((getWidth()-s)/2, (getHeight()-s)/2, s, s);
            g2.setColor(ACCENT_COLOR);
            g2.draw(new Arc2D.Float((getWidth()-s)/2, (getHeight()-s)/2, s, s, 90 - angle, -100, Arc2D.OPEN));
            g2.dispose();
        }
    }
}