package ru.varim.kopilka;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Читает уведомления ТОЛЬКО приложений выбранных банков; остальные даже не разбираются. */
public class BankNotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName())) return;
            BankFilter f = BankStore.filter(this);
            boolean known = f.byPackage(pkg, BankStore.enabled(this)) != null;
            boolean custom = false;
            for (String c : BankStore.list(this, "custom")) if (c.equalsIgnoreCase(pkg)) custom = true;
            if (!known && !custom) return; // не банк — не читаем
            Notification n = sbn.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence text = big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT);
            String body = (title != null ? title + ". " : "") + (text != null ? text : "");
            BankStore.offer(this, body, null, pkg, "push");
        } catch (Exception ignored) { /* фильтр не должен ронять сервис */ }
    }
}
