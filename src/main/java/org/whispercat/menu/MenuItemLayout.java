package org.whispercat.menu;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JLabel;

public class MenuItemLayout implements LayoutManager {

    private final Menu menu;

    public MenuItemLayout(Menu menu) {
        this.menu = menu;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int height = insets.top + insets.bottom;
            int size = parent.getComponentCount();
            for (int i = 0; i < size; i++) {
                Component com = parent.getComponent(i);
                if (com.isVisible()) {
                    if (com instanceof JLabel) {
                        if (menu.isMenuFull() || menu.isHideMenuTitleOnMinimum() == false) {
                            height += com.getPreferredSize().height + (UIScale.scale(menu.getMenuTitleVgap()) * 2);
                        }
                    } else {
                        height += com.getPreferredSize().height;
                    }
                }
            }
            return new Dimension(5, height);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return new Dimension(0, 0);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int x = insets.left;
            int y = insets.top;
            int width = parent.getWidth() - (insets.left + insets.right);
            int size = parent.getComponentCount();
            for (int i = 0; i < size; i++) {
                Component com = parent.getComponent(i);
                if (com.isVisible()) {
                    int comHeight = com.getPreferredSize().height;
                    if (com instanceof JLabel) {
                        if (menu.isMenuFull() || menu.isHideMenuTitleOnMinimum() == false) {
                            int menuTitleInset = UIScale.scale(menu.getMenuTitleLeftInset());
                            int menuTitleVgap = UIScale.scale(menu.getMenuTitleVgap());
                            int titleWidth = width - menuTitleInset;
                            y += menuTitleVgap;
                            com.setBounds(x + menuTitleInset, y, titleWidth, comHeight);
                            y += comHeight + menuTitleVgap;
                        } else {
                            com.setBounds(0, 0, 0, 0);
                        }
                    } else {
                        com.setBounds(x, y, width, comHeight);
                        y += comHeight;
                    }
                }
            }
        }
    }
}
