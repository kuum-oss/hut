package org.example.ui;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EpubEditorDialog extends JDialog {

    private final List<ImageTask> tasks = new ArrayList<>();
    private final JPanel taskListPanel;
    private final JCheckBox replaceStarsCheckBox;

    // Современная цветовая палитра
    private static final Color ACCENT_COLOR = new Color(33, 150, 243); // Material Blue
    private static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    private static final Color DANGER_COLOR = new Color(231, 76, 60);
    private static final Color TEXT_MAIN = new Color(44, 62, 80);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);

    // Шрифты
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TEXT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);

    public EpubEditorDialog(Frame owner) {
        super(owner, "Редактор EPUB - Добавление Иллюстраций", true);
        setSize(800, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(25, 30, 15, 30));

        JLabel title = new JLabel("📖 Редактирование EPUB контента");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);
        header.add(title, BorderLayout.NORTH);

        replaceStarsCheckBox = new JCheckBox("✨ Заменять '***' на изображения во всей книге");
        replaceStarsCheckBox.setFont(FONT_TEXT);
        replaceStarsCheckBox.setForeground(TEXT_MAIN);
        replaceStarsCheckBox.setOpaque(false);
        replaceStarsCheckBox.setFocusPainted(false);
        header.add(replaceStarsCheckBox, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Task List (Scrollable)
        taskListPanel = new JPanel();
        taskListPanel.setOpaque(false);
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setBorder(new EmptyBorder(0, 30, 0, 30)); // Отступы по бокам

        JScrollPane scrollPane = new JScrollPane(taskListPanel);
        scrollPane.setBorder(null); // Убираем некрасивую стандартную рамку
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        // Ускоряем скроллинг мыши (важный QoL фикс для Swing)
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Controls
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 30, 15, 30));

        JButton addBtn = new JButton("➕ Добавить страницу");
        addBtn.setFont(FONT_BOLD);
        addBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 10; background: #ffffff; foreground: #2c3e50;");
        addBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton okBtn = new JButton("✅ Сохранить настройки");
        okBtn.setFont(FONT_BOLD);
        okBtn.putClientProperty(FlatClientProperties.STYLE, "arc: 10; background: #2196f3; foreground: #ffffff;");
        okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        addBtn.addActionListener(e -> addNewTask());
        okBtn.addActionListener(e -> dispose());

        footer.add(addBtn);
        footer.add(okBtn);
        add(footer, BorderLayout.SOUTH);

        // Initial task
        addNewTask();
    }

    private void addNewTask() {
        ImageTask task = new ImageTask(this);
        tasks.add(task);
        taskListPanel.add(task.panel);
        taskListPanel.add(Box.createRigidArea(new Dimension(0, 15))); // Промежуток между задачами
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    public void removeTask(ImageTask task) {
        tasks.remove(task);
        // Удаляем саму панель и следующий за ней отступ (RigidArea)
        int index = taskListPanel.getComponentZOrder(task.panel);
        if (index != -1) {
            taskListPanel.remove(index);
            if (index < taskListPanel.getComponentCount()) {
                taskListPanel.remove(index); // Удаляем Box.createRigidArea
            }
        }
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    public List<ImageTask> getTasks() {
        return tasks;
    }

    public boolean isReplaceStars() {
        return replaceStarsCheckBox.isSelected();
    }

    public static class ImageTask {
        public JPanel panel;
        public JLabel imgLabel;
        public JTextField pageField;
        public File imageFile;
        public JCheckBox useForStars;

        ImageTask(EpubEditorDialog dialog) {
            panel = new JPanel(new BorderLayout(20, 10));
            panel.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #ffffff;");
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true), // Скругленные углы
                    new EmptyBorder(15, 15, 15, 15)
            ));

            // Зона Drop (обертка для скругления)
            JPanel imgWrapper = new JPanel(new BorderLayout());
            imgWrapper.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #f5f7fa;");
            
            imgLabel = new JLabel("📥 Перетащите JPEG сюда");
            imgLabel.setPreferredSize(new Dimension(200, 90));
            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imgLabel.setOpaque(false); // Делаем прозрачным, фон рисует обертка
            imgLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 5, 2, false));
            imgLabel.setForeground(Color.DARK_GRAY);
            imgLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            
            imgWrapper.add(imgLabel);

            // Правая часть с настройками
            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(5, 10, 5, 10);

            gbc.gridx = 0; gbc.gridy = 0;
            JLabel pageLabel = new JLabel("📄 Номер страницы:");
            pageLabel.setFont(FONT_TEXT);
            pageLabel.setForeground(TEXT_MAIN);
            right.add(pageLabel, gbc);

            gbc.gridx = 1;
            pageField = new JTextField("0", 5);
            pageField.setFont(FONT_BOLD);
            pageField.setHorizontalAlignment(JTextField.CENTER);
            pageField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "№");
            right.add(pageField, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            gbc.gridwidth = 2;
            useForStars = new JCheckBox("⭐ Использовать для замены '***'");
            useForStars.setFont(FONT_TEXT);
            useForStars.setForeground(TEXT_MAIN);
            useForStars.setOpaque(false);
            useForStars.setFocusPainted(false);
            right.add(useForStars, gbc);

            // Кнопка удаления
            JButton delBtn = new JButton("🗑️");
            delBtn.setToolTipText("Удалить задачу");
            delBtn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            delBtn.setForeground(DANGER_COLOR);
            delBtn.setBorderPainted(false);
            delBtn.setContentAreaFilled(false);
            delBtn.setFocusPainted(false);
            delBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            delBtn.addActionListener(e -> dialog.removeTask(this));

            JPanel actionPanel = new JPanel(new BorderLayout());
            actionPanel.setOpaque(false);
            actionPanel.add(delBtn, BorderLayout.CENTER);

            panel.add(imgWrapper, BorderLayout.WEST);
            panel.add(right, BorderLayout.CENTER);
            panel.add(actionPanel, BorderLayout.EAST);

            setupDragAndDrop(dialog);
        }

        private void setupDragAndDrop(EpubEditorDialog dialog) {
            new DropTarget(imgLabel, new DropTargetAdapter() {
                @Override
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File f = files.get(0);
                            String ext = f.getName().toLowerCase();
                            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                                imageFile = f;
                                imgLabel.setText("<html><center>✅ " + f.getName() + "</center></html>");
                                JPanel wrapper = (JPanel) imgLabel.getParent();
                                wrapper.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #ebfaf0;"); // Зеленоватый фон успеха
                                imgLabel.setForeground(SUCCESS_COLOR);
                                imgLabel.setBorder(BorderFactory.createLineBorder(SUCCESS_COLOR, 2, true));
                            } else {
                                JOptionPane.showMessageDialog(dialog, "❌ Пожалуйста, используйте только JPEG изображения!", "Ошибка формата", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }
}
