package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.ConfigManager;
import org.whispercat.MainForm;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.DnDConstants;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * A panel that displays all saved Post Processing configurations.
 * Each item is shown inside a bordered panel with its title and description,
 * plus two buttons (edit and delete) on the right. The list is built from the
 * ConfigManager's JSON array.
 */
public class PostProcessingListForm extends JPanel {
    private final ConfigManager configManager;
    private final MainForm mainForm;
    private final JPanel listContainer;
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(PostProcessingListForm.class);

    public PostProcessingListForm(ConfigManager configManager, MainForm mainForm) {
        this.configManager = configManager;
        this.mainForm = mainForm;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(60, 20, 10, 10));

        JLabel headerLabel = new JLabel("All Post Processings");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        // add(headerPanel, BorderLayout.NORTH);

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setTransferHandler(new PanelReorderTransferHandler());
        listContainer.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component comp = listContainer.getComponentAt(e.getPoint());
                if (comp instanceof JPanel) {
                    listContainer.getTransferHandler().exportAsDrag(listContainer, e, TransferHandler.MOVE);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
        refreshList();
    }

    public void refreshList() {
        listContainer.removeAll();
        List<PostProcessingData> postProcessingList = configManager.getPostProcessingDataList();
        logger.info("Post Processing List: {}", postProcessingList);

        for (PostProcessingData data : postProcessingList) {
            String title = (data.title != null && !data.title.trim().isEmpty()) ? data.title : "No Title";
            String description = (data.description != null && !data.description.trim().isEmpty()) ? data.description : "No Description";
            JPanel itemPanel = new JPanel(new BorderLayout());
            itemPanel.setBorder(BorderFactory.createTitledBorder(title));

            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            JLabel descriptionLabel = new JLabel("Description: " + description);
            descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(descriptionLabel);
            itemPanel.add(infoPanel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            JButton editButton = new JButton();
            editButton.setIcon(new FlatSVGIcon("icon/svg/edit.svg", 16, 16));
            editButton.setToolTipText("Edit this Post Processing");
            editButton.addActionListener((ActionEvent e) -> {
                mainForm.setSelectedMenu(2, 2);
                mainForm.showForm(new PostProcessingForm(configManager, data));
            });
            JButton deleteButton = new JButton();
            deleteButton.setIcon(new FlatSVGIcon("icon/svg/trash.svg", 16, 16));
            deleteButton.setToolTipText("Delete this Post Processing");
            deleteButton.addActionListener((ActionEvent e) -> {
                configManager.deletePostProcessingData(data.uuid);
                configManager.saveConfig();
                refreshList();
            });
            buttonPanel.add(editButton);
            buttonPanel.add(Box.createVerticalStrut(5));
            buttonPanel.add(deleteButton);
            itemPanel.add(buttonPanel, BorderLayout.EAST);

            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemPanel.getPreferredSize().height));
            listContainer.add(itemPanel);
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    private static class PanelTransferable implements Transferable {
        public static final DataFlavor PANEL_FLAVOR = new DataFlavor(
                DataFlavor.javaJVMLocalObjectMimeType + ";class=javax.swing.JPanel", "JPanel");
        private final JPanel panel;

        public PanelTransferable(JPanel panel) {
            this.panel = panel;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { PANEL_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(PANEL_FLAVOR);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (flavor.equals(PANEL_FLAVOR)) {
                return panel;
            }
            return null;
        }
    }

    private class PanelReorderTransferHandler extends TransferHandler {
        private JPanel draggedPanel;

        @Override
        protected Transferable createTransferable(JComponent c) {
            for (Component comp : listContainer.getComponents()) {
                if (comp.getBounds().contains(listContainer.getMousePosition())) {
                    draggedPanel = (JPanel) comp;
                    break;
                }
            }
            return new PanelTransferable(draggedPanel);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(PanelTransferable.PANEL_FLAVOR);
        }

        @Override
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                JPanel droppedPanel = (JPanel) support.getTransferable().getTransferData(PanelTransferable.PANEL_FLAVOR);
                Point dropPoint = support.getDropLocation().getDropPoint();
                int index = -1;
                for (int i = 0; i < listContainer.getComponentCount(); i++) {
                    Component comp = listContainer.getComponent(i);
                    if (dropPoint.getY() < comp.getBounds().getCenterY()) {
                        index = i;
                        break;
                    }
                }
                if (index == -1) {
                    index = listContainer.getComponentCount();
                }
                listContainer.remove(droppedPanel);
                listContainer.add(droppedPanel, index);
                listContainer.revalidate();
                listContainer.repaint();
                return true;
            } catch (Exception e) {
                logger.error("Error during panel reorder: ", e);
            }
            return false;
        }
    }
}