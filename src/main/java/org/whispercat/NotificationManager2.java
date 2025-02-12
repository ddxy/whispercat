package org.whispercat;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationManager2 {
    private static final int MAX_NOTIFICATIONS = 5;
    private static final int NOTIFICATION_SPACING = 10;
    private static NotificationManager2 instance;
    private final List<ToastNotification2> notifications = new ArrayList<>();
    private final Timer animationTimer;
    private static Window parent;
    private NotificationManager2() {
        animationTimer = new Timer(30, e -> updateAnimations());
        animationTimer.start();
    }

    public static synchronized NotificationManager2 getInstance() {
        if (instance == null) {
            instance = new NotificationManager2();
        }
        return instance;
    }

    public static void setWindow(Application application) {
        parent = application;
    }

    public synchronized void showNotification(ToastNotification2.Type type, String message) {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            ToastNotification2 oldestNotification = notifications.remove(0);
            oldestNotification.dispose();
        }

        ToastNotification2 notification = new ToastNotification2(parent, type, message);
        notifications.add(notification);

        positionNotification(notification);
    }

    private void positionNotification(ToastNotification2 notification) {
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

    public synchronized void removeNotification(ToastNotification2 notification) {
        notifications.remove(notification);
        updateNotificationPositions();
    }

    private void updateAnimations() {
        synchronized (this) {
            List<ToastNotification2> notificationsCopy = new ArrayList<>(notifications);
            for (ToastNotification2 notification : notificationsCopy) {
                notification.updateAnimation();
            }
        }
    }

    private void updateNotificationPositions() {
        int yOffset = 40;
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ToastNotification2 notification = notifications.get(i);
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
            ToastNotification2 notification = notifications.get(i);
            Window owner = notification.getOwner();

            int x = owner.getX() + owner.getWidth() - notification.getWidth() - 20;
            int y = owner.getY() + owner.getHeight() - notification.getHeight() - yOffset;

            notification.setLocation(x, y);

            yOffset += notification.getHeight() + NOTIFICATION_SPACING;
        }
    }
}