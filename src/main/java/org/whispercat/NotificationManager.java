package org.whispercat;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationManager {
    private static final int MAX_NOTIFICATIONS = 5;
    private static final int NOTIFICATION_SPACING = 10;
    private static NotificationManager instance;
    private final List<ToastNotification> notifications = new ArrayList<>();
    private final Timer animationTimer;

    private NotificationManager() {
        animationTimer = new Timer(30, e -> updateAnimations());
        animationTimer.start();
    }

    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    public synchronized void showNotification(Window parent, ToastNotification.Type type, String message) {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            ToastNotification oldestNotification = notifications.remove(0);
            oldestNotification.dispose();
        }

        ToastNotification notification = new ToastNotification(parent, type, message);
        notifications.add(notification);

        positionNotification(notification);
    }

    private void positionNotification(ToastNotification notification) {
        Window owner = notification.getOwner();
        int x = owner.getX() + owner.getWidth() - notification.getWidth() - 20;
        int yOffset = 40;

        for (int i = notifications.size() - 2; i >= 0; i--) {
            yOffset += notifications.get(i).getHeight() + NOTIFICATION_SPACING;
        }

        int y = owner.getY() + owner.getHeight() - notification.getHeight() - yOffset;
        notification.setLocation(x, y);
        notification.showToast();
    }

    public synchronized void removeNotification(ToastNotification notification) {
        notifications.remove(notification);
        updateNotificationPositions();
    }

    private void updateAnimations() {
        synchronized (this) {
            List<ToastNotification> notificationsCopy = new ArrayList<>(notifications);
            for (ToastNotification notification : notificationsCopy) {
                notification.updateAnimation();
            }
        }
    }

    private void updateNotificationPositions() {
        int yOffset = 40;
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ToastNotification notification = notifications.get(i);
            Window owner = notification.getOwner();
            int x = owner.getX() + owner.getWidth() - notification.getWidth() - 20;
            int targetY = owner.getY() + owner.getHeight() - notification.getHeight() - yOffset;
            notification.setLocation(x, targetY);
            yOffset += notification.getHeight() + NOTIFICATION_SPACING;
        }
    }


    public synchronized void updateAllNotifications() {
        int yOffset = 40;
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ToastNotification notification = notifications.get(i);
            Window owner = notification.getOwner();

            int x = owner.getX() + owner.getWidth() - notification.getWidth() - 20;
            int y = owner.getY() + owner.getHeight() - notification.getHeight() - yOffset;

            notification.setLocation(x, y);

            yOffset += notification.getHeight() + NOTIFICATION_SPACING;
        }
    }
}