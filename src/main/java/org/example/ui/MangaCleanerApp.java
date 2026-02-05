package org.example.ui;

import org.example.logic.*;
import org.example.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MangaCleanerApp extends JFrame {

    private final PdfWatermarkCleaner pdfCleaner = new PdfWatermarkCleaner();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();
    private final MangaResizer mangaResizer = new MangaResizer();
    private final MangaImageProcessor imageProcessor = new MangaImageProcessor();

    private final JPanel dropPanel;
    private static final Color ACCENT_COLOR = new Color(0, 122, 204);

    public MangaCleanerApp() {
        setTitle("Manga Cleaner v16 (Smart Crop Edition)");
        setSize(950, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(248, 249, 250));

        // Хедер
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 15));
        header.setBackground(Color.WHITE);
        header.setBorder(new MatteBorder(0, 0, 1, 0, new Color(230, 230, 230)));
        JLabel title = new JLabel("Manga Cleaner");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(ACCENT_COLOR);
        header.add(title);
        mainPanel.add(header, BorderLayout.NORTH);

        // Зона Drag & Drop
        dropPanel = new JPanel(new GridBagLayout());
        dropPanel.setBackground(Color.WHITE);
        float[] dash = {10.0f};
        dropPanel.setBorder(BorderFactory.createStrokeBorder(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f), new Color(200, 200, 200)));

        JLabel dropLabel = new JLabel("<html><center>Перетащите PDF/EPUB файлы сюда<br><small style='color:#999'>Первая страница (обложка) будет защищена</small></center></html>");
        dropLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        dropPanel.add(dropLabel);

        new DropTarget(dropPanel, new FileDropHandler());
        mainPanel.add(dropPanel, BorderLayout.CENTER);
        add(mainPanel);
    }

    private class FileDropHandler extends DropTargetAdapter {
        public void drop(DropTargetDropEvent event) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<File> allFiles = new ArrayList<>();
                for (File f : droppedFiles) collectFiles(f, allFiles);

                if (!allFiles.isEmpty()) processBatchAsync(allFiles, allFiles.get(0).getParentFile());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void collectFiles(File root, List<File> res) {
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) for (File c : children) collectFiles(c, res);
        } else if (root.getName().toLowerCase().endsWith(".pdf") || root.getName().toLowerCase().endsWith(".epub")) {
            res.add(root);
        }
    }

    // --- ЛОГИКА ОБРАБОТКИ ---
    private void processBatchAsync(List<File> inputs, File outputDir) {
        LoadingDialog loadingDialog = new LoadingDialog(this, inputs.size());

        SwingWorker<Void, ProgressUpdate> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                AtomicReference<ProcessingSettings> settingsRef = new AtomicReference<>(null);

                for (int i = 0; i < inputs.size(); i++) {
                    File input = inputs.get(i);
                    File output = new File(outputDir, input.getName().replace(".", "_clean."));

                    publish(new ProgressUpdate(i, "Обработка: " + input.getName()));

                    if (input.getName().toLowerCase().endsWith(".pdf")) {
                        if (settingsRef.get() == null) {
                            SwingUtilities.invokeAndWait(() -> loadingDialog.setVisible(false));
                            BufferedImage preview = mangaResizer.getPreviewImage(input);
                            SwingUtilities.invokeAndWait(() -> settingsRef.set(showSettingsDialog(preview, inputs.size())));
                            if (settingsRef.get() == null) { cancel(true); return null; }
                            SwingUtilities.invokeLater(() -> loadingDialog.setVisible(true));
                        }

                        ProcessingSettings cfg = settingsRef.get();
                        Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        if (cfg.removeWatermarks) pdfCleaner.clean(output, output);

                        // ПЕРЕДАЕМ НОВЫЙ ПАРАМЕТР: smartCrop
                        mangaResizer.applyResize(output, cfg.cropMode, cfg.upscale, cfg.binarization, cfg.skipCover, cfg.smartCrop);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<ProgressUpdate> chunks) {
                ProgressUpdate last = chunks.get(chunks.size() - 1);
                loadingDialog.updateProgress(last.index, last.message);
            }

            @Override
            protected void done() {
                loadingDialog.dispose();
                if (!isCancelled()) JOptionPane.showMessageDialog(MangaCleanerApp.this, "Готово!");
            }
        };

        worker.execute();
        loadingDialog.setVisible(true);
    }

    private static record ProgressUpdate(int index, String message) {}

    // --- НАСТРОЙКИ ---
    private static class ProcessingSettings {
        CropMode cropMode;
        boolean removeWatermarks, upscale, binarization, skipCover, smartCrop;
        ProcessingSettings(CropMode cm, boolean rw, boolean up, boolean bin, boolean sc, boolean smart) {
            this.cropMode = cm; this.removeWatermarks = rw; this.upscale = up; this.binarization = bin; this.skipCover = sc; this.smartCrop = smart;
        }
    }

    private ProcessingSettings showSettingsDialog(BufferedImage rawImage, int total) {
        JDialog dialog = new JDialog(this, "Настройки обработки", true);
        dialog.setSize(1000, 650);
        dialog.setLayout(new BorderLayout());

        JLabel resLabel = new JLabel("Загрузка...", SwingConstants.CENTER);
        dialog.add(new JScrollPane(resLabel), BorderLayout.CENTER);

        // ПАНЕЛЬ НАСТРОЕК
        JPanel controls = new JPanel(new GridLayout(2, 1));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        JPanel botRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));

        // Чекбоксы
        JCheckBox chkCover = new JCheckBox("Защитить обложку", true);
        JCheckBox chkBin = new JCheckBox("E-Ink (Ч/Б)", false);
        JCheckBox chkUp = new JCheckBox("HD Upscale", false);

        // НОВЫЙ ЧЕКБОКС
        JCheckBox chkSmartCrop = new JCheckBox("Smart Auto-Crop", true);
        chkSmartCrop.setToolTipText("<html><b>ВКЛ:</b> Обрезает каждую страницу индивидуально (максимум полезного места).<br><b>ВЫКЛ:</b> Вычисляет обрезку по 2-й странице и применяет ко всем (стабильность).</html>");

        // Выбор режима если SmartCrop выключен
        String[] modes = {"По ширине (Стандарт)", "Вписать целиком", "Растянуть"};
        JComboBox<String> comboMode = new JComboBox<>(modes);
        comboMode.setEnabled(false); // По умолчанию выключен, так как SmartCrop включен

        // Логика переключения
        chkSmartCrop.addActionListener(e -> comboMode.setEnabled(!chkSmartCrop.isSelected()));

        JButton btnGo = new JButton("НАЧАТЬ ОБРАБОТКУ");
        btnGo.setBackground(new Color(34, 139, 34));
        btnGo.setForeground(Color.WHITE);

        topRow.add(chkCover); topRow.add(chkBin); topRow.add(chkUp);
        botRow.add(chkSmartCrop); botRow.add(new JLabel("Или вручную:")); botRow.add(comboMode); botRow.add(btnGo);

        controls.add(topRow);
        controls.add(botRow);
        dialog.add(controls, BorderLayout.SOUTH);

        // Превью логика
        Runnable updatePreview = () -> {
            new SwingWorker<ImageIcon, Void>() {
                protected ImageIcon doInBackground() {
                    imageProcessor.setBinarization(chkBin.isSelected());
                    BufferedImage bi = imageProcessor.process(rawImage);
                    return new ImageIcon(bi.getScaledInstance(-1, 500, Image.SCALE_SMOOTH));
                }
                protected void done() { try { resLabel.setIcon(get()); resLabel.setText(""); } catch (Exception e) {} }
            }.execute();
        };
        chkBin.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        final ProcessingSettings[] res = {null};
        btnGo.addActionListener(e -> {
            CropMode m = CropMode.FIT_WIDTH;
            if (comboMode.getSelectedIndex() == 1) m = CropMode.FIT_HEIGHT;
            if (comboMode.getSelectedIndex() == 2) m = CropMode.STRETCH;

            res[0] = new ProcessingSettings(m, true, chkUp.isSelected(), chkBin.isSelected(), chkCover.isSelected(), chkSmartCrop.isSelected());
            dialog.dispose();
        });

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return res[0];
    }

    // --- ДИАЛОГ ЗАГРУЗКИ (из прошлого шага) ---
    private static class LoadingDialog extends JDialog {
        private final JLabel statusLabel, etaLabel;
        private final Timer timer;
        private long startTime;
        private int secondsElapsed = 0;
        private int totalFiles;

        public LoadingDialog(Frame owner, int totalFiles) {
            super(owner, "Обработка", true);
            this.totalFiles = totalFiles;
            setSize(400, 320);
            setLocationRelativeTo(owner);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));

            JPanel panel = new JPanel(new GridBagLayout()) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                    g2.setColor(new Color(220,220,220));
                    g2.drawRoundRect(0,0,getWidth()-1, getHeight()-1, 30,30);
                    g2.dispose();
                }
            };
            panel.setOpaque(false);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 10, 10);

            panel.add(new CircleLoader(), gbc);

            statusLabel = new JLabel("Подготовка...");
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            gbc.gridy++; panel.add(statusLabel, gbc);

            etaLabel = new JLabel("Ожидание...");
            etaLabel.setForeground(ACCENT_COLOR);
            gbc.gridy++; panel.add(etaLabel, gbc);

            add(panel);

            timer = new Timer(1000, e -> secondsElapsed++);
        }

        public void updateProgress(int idx, String msg) {
            if (idx == 0) startTime = System.currentTimeMillis();
            statusLabel.setText(msg);
            if (idx > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                long avg = elapsed / idx;
                long left = avg * (totalFiles - idx);
                etaLabel.setText(String.format("Осталось: ~%02d:%02d", (left/1000)/60, (left/1000)%60));
            }
        }
        public void setVisible(boolean b) { if(b) timer.start(); else timer.stop(); super.setVisible(b); }
    }

    private static class CircleLoader extends JPanel {
        private int angle = 0;
        public CircleLoader() {
            setPreferredSize(new Dimension(80, 80));
            setOpaque(false);
            new Timer(16, e -> { angle = (angle + 6) % 360; repaint(); }).start();
        }
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(240,240,240));
            g2.drawOval(10, 10, 60, 60);
            g2.setColor(ACCENT_COLOR);
            g2.draw(new Arc2D.Float(10, 10, 60, 60, 90 - angle, -240, Arc2D.OPEN));
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }
}