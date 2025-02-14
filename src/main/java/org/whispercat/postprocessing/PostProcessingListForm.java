package org.whispercat.postprocessing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.whispercat.ConfigManager;
import org.whispercat.MainForm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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

        // Create and add the header panel with a title.
        JLabel headerLabel = new JLabel("All Post Processings");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.PLAIN, 18f));
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
//        add(headerPanel, BorderLayout.NORTH);

        // Create a container that holds the individual post processing items.
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        refreshList();
    }

    /**
     * Refreshes the list by reading the JSON array from the ConfigManager
     * and rebuilding the UI.
     */
    public void refreshList() {
        listContainer.removeAll();

        // Get the list of post processing JSON strings.
        List<PostProcessingData> postProcessingList = configManager.getPostProcessingDataList();
        logger.info("Post Processing List: {}", postProcessingList);

        // For each item, create a panel showing its title, description, and buttons.
        for (PostProcessingData data : postProcessingList) {
            // Deserialize to get the title and description.
            String title = (data.title != null && !data.title.trim().isEmpty()) ? data.title : "No Title";
            String description = (data.description != null && !data.description.trim().isEmpty()) ? data.description : "No Description";

            // Create an item panel with a titled border using the title.
            JPanel itemPanel = new JPanel(new BorderLayout());
            itemPanel.setBorder(BorderFactory.createTitledBorder(title));

            // Create an info panel for the description.
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            JLabel descriptionLabel = new JLabel("Description: " + description);
            descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(descriptionLabel);
            // Add info panel to the center.
            itemPanel.add(infoPanel, BorderLayout.CENTER);

            // Create a button panel on the right.
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

            // Edit button.
            JButton editButton = new JButton();
            editButton.setIcon(new FlatSVGIcon("icon/svg/edit.svg", 16, 16));
            editButton.setToolTipText("Edit this Post Processing");
            editButton.addActionListener((ActionEvent e) -> {
                mainForm.setSelectedMenu(2, 2);
                mainForm.showForm(new PostProcessingForm(configManager, data));
            });

            // Delete button.
            JButton deleteButton = new JButton();
            deleteButton.setIcon(new FlatSVGIcon("icon/svg/trash.svg", 16, 16));
            deleteButton.setToolTipText("Delete this Post Processing");
            deleteButton.addActionListener((ActionEvent e) -> {
                configManager.deletePostProcessingData(data.uuid);
                configManager.saveConfig();
                refreshList();
            });
            // Add buttons to the button panel with vertical spacing.
            buttonPanel.add(editButton);
            buttonPanel.add(Box.createVerticalStrut(5));
            buttonPanel.add(deleteButton);
            // Add the button panel to the right side.
            itemPanel.add(buttonPanel, BorderLayout.EAST);

            // Adjust the height: set maximum size to its preferred height.
            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemPanel.getPreferredSize().height));

            listContainer.add(itemPanel);
        }
        listContainer.revalidate();
        listContainer.repaint();
    }


}