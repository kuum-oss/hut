package org.example.ui;

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

    private static final Color ACCENT_COLOR = new Color(0, 120, 215);
    private static final Color BG_COLOR = new Color(245, 245, 245);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TEXT = new Font("Segoe UI", Font.PLAIN, 14);

    public EpubEditorDialog(Frame owner) {
        super(owner, "Редактор EPUB - Добавление Иллюстраций", true);
        setSize(750, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(BG_COLOR);

        // Header
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(25, 25, 10, 25));
        
        JLabel title = new JLabel("Редактирование EPUB контента");
        title.setFont(FONT_TITLE);
        title.setForeground(Color.DARK_GRAY);
        header.add(title, BorderLayout.NORTH);
        
        replaceStarsCheckBox = new JCheckBox("Заменять '***' на изображения во всей книге");
        replaceStarsCheckBox.setFont(FONT_TEXT);
        replaceStarsCheckBox.setOpaque(false);
        header.add(replaceStarsCheckBox, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Task List (Scrollable)
        taskListPanel = new JPanel();
        taskListPanel.setBackground(Color.WHITE);
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        
        JScrollPane scrollPane = new JScrollPane(taskListPanel);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 25, 10, 25),
                BorderFactory.createLineBorder(new Color(230, 230, 230))
        ));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Controls
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setOpaque(false);
        
        JButton addBtn = new JButton("+ Добавить");
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JButton okBtn = new JButton("OK – Сохранить настройки");
        okBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        okBtn.setBackground(ACCENT_COLOR);
        okBtn.setForeground(Color.WHITE);
        okBtn.setFocusPainted(false);
        
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
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    public void removeTask(ImageTask task) {
        tasks.remove(task);
        taskListPanel.remove(task.panel);
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
            panel = new JPanel(new BorderLayout(15, 5));
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
                    new EmptyBorder(15, 10, 15, 10)
            ));

            imgLabel = new JLabel("JPEG сюда");
            imgLabel.setPreferredSize(new Dimension(160, 80));
            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imgLabel.setBorder(BorderFactory.createDashedBorder(Color.LIGHT_GRAY, 1, 4, 2, true));
            imgLabel.setForeground(Color.GRAY);
            imgLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));

            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 5, 5, 5);

            gbc.gridx = 0; gbc.gridy = 0;
            right.add(new JLabel("Стр:"), gbc);
            
            gbc.gridx = 1;
            pageField = new JTextField("0", 4);
            pageField.setFont(new Font("Segoe UI", Font.BOLD, 14));
            right.add(pageField, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            gbc.gridwidth = 2;
            useForStars = new JCheckBox("Использовать для '***'");
            useForStars.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            useForStars.setOpaque(false);
            right.add(useForStars, gbc);

            JButton delBtn = new JButton("× Удалить");
            delBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            delBtn.setForeground(new Color(200, 0, 0));
            delBtn.setBorderPainted(false);
            delBtn.setContentAreaFilled(false);
            delBtn.setFocusPainted(false);
            delBtn.addActionListener(e -> dialog.removeTask(this));

            JPanel actionPanel = new JPanel(new BorderLayout());
            actionPanel.setOpaque(false);
            actionPanel.add(delBtn, BorderLayout.NORTH);

            panel.add(imgLabel, BorderLayout.WEST);
            panel.add(right, BorderLayout.CENTER);
            panel.add(actionPanel, BorderLayout.EAST);

            setupDragAndDrop(dialog);
        }

        private void setupDragAndDrop(EpubEditorDialog dialog) {
            new DropTarget(imgLabel, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File f = files.get(0);
                            String ext = f.getName().toLowerCase();
                            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                                imageFile = f;
                                imgLabel.setText(f.getName());
                                imgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                                imgLabel.setForeground(new Color(0, 150, 0));
                                imgLabel.setBorder(BorderFactory.createLineBorder(new Color(0, 200, 0), 2));
                            } else {
                                JOptionPane.showMessageDialog(dialog, "Только JPEG изображения!");
                            }
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
            });
        }
    }
}
