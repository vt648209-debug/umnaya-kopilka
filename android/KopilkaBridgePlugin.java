package ru.varim.kopilka;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.os.Build;
import android.provider.Telephony;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Мост между пересыльщиком и веб-интерфейсом копилки. */
@CapacitorPlugin(name = "KopilkaBridge", permissions = { @Permission(alias = "sms", strings = { Manifest.permission.RECEIVE_SMS }), @Permission(alias = "smsread", strings = { Manifest.permission.READ_SMS }) })
public class KopilkaBridgePlugin extends Plugin {
    private static WeakReference<KopilkaBridgePlugin> live = new WeakReference<>(null);

    @Override public void load() { live = new WeakReference<>(this); }

    /** Новая операция, пока приложение открыто, — сразу в интерфейс. */
    static void deliver(JSONObject item) {
        KopilkaBridgePlugin p = live.get();
        if (p == null) return;
        try { p.notifyListeners("bankMessage", JSObject.fromJSONObject(item)); } catch (Exception ignored) { }
    }

    private boolean notifAccess() {
        return NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName());
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("notifAccess", notifAccess());
        r.put("sms", getPermissionState("sms") == PermissionState.GRANTED);
        r.put("chosen", BankStore.prefs(getContext()).contains("banks"));
        List<String> en = BankStore.enabled(getContext());
        JSArray banks = new JSArray();
        for (BankFilter.Bank b : BankStore.filter(getContext()).banks) {
            JSObject o = new JSObject(); o.put("id", b.id); o.put("name", b.name); o.put("on", en == null || en.contains(b.id)); banks.put(o);
        }
        r.put("banks", banks);
        r.put("custom", new JSArray(BankStore.list(getContext(), "custom")));
        r.put("queued", BankStore.queued(getContext()));
        r.put("received", BankStore.prefs(getContext()).getInt("received", 0));
        r.put("lastReceived", BankStore.prefs(getContext()).getLong("lastReceived", 0));
        r.put("droppedSecrets", BankStore.prefs(getContext()).getInt("droppedSecrets", 0));
        r.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    @PluginMethod
    public void setBanks(PluginCall call) {
        try {
            List<String> ids = new ArrayList<>(); JSArray a = call.getArray("ids", new JSArray());
            for (int i = 0; i < a.length(); i++) ids.add(a.getString(i));
            BankStore.setList(getContext(), "banks", ids);
            List<String> custom = new ArrayList<>(); JSArray c = call.getArray("custom", new JSArray());
            for (int i = 0; i < c.length(); i++) { String s = c.getString(i).trim(); if (!s.isEmpty() && s.length() <= 60) custom.add(s); }
            BankStore.setList(getContext(), "custom", custom);
            call.resolve();
        } catch (Exception e) { call.reject("Не удалось сохранить банки"); }
    }

    @PluginMethod
    public void openNotificationAccess(PluginCall call) {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            // сразу экран переключателя для нашего приложения
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(getContext(), BankNotificationService.class).flattenToString());
        } else i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { getContext().startActivity(i); }
        catch (Exception e) { Intent f = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(f); }
        call.resolve();
    }

    @PluginMethod
    public void openAppInfo(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void requestSms(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) { JSObject r = new JSObject(); r.put("sms", true); call.resolve(r); return; }
        requestPermissionForAlias("sms", call, "smsResult");
    }
    @PermissionCallback
    private void smsResult(PluginCall call) {
        JSObject r = new JSObject(); r.put("sms", getPermissionState("sms") == PermissionState.GRANTED); call.resolve(r);
    }

    /** Кнопка «Загрузить СМС банка»: прочитать входящие СМС за N дней, только банки из списка, без кодов. */
    @PluginMethod
    public void readInbox(PluginCall call) {
        if (getPermissionState("smsread") != PermissionState.GRANTED) { requestPermissionForAlias("smsread", call, "smsReadResult"); return; }
        doReadInbox(call);
    }
    @PermissionCallback
    private void smsReadResult(PluginCall call) {
        if (getPermissionState("smsread") != PermissionState.GRANTED) { call.reject("Нет разрешения читать СМС"); return; }
        doReadInbox(call);
    }
    private void doReadInbox(PluginCall call) {
        int days = call.getInt("days", 92);
        long since = System.currentTimeMillis() - days * 86_400_000L;
        BankFilter f = BankStore.filter(getContext());
        List<String> en = BankStore.enabled(getContext());
        List<String> custom = BankStore.list(getContext(), "custom");
        JSArray items = new JSArray();
        int secrets = 0, total = 0;
        try (Cursor c = getContext().getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[] { Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE },
                Telephony.Sms.DATE + " > ?", new String[] { String.valueOf(since) }, Telephony.Sms.DATE + " ASC")) {
            while (c != null && c.moveToNext() && items.length() < 5000) {
                total++;
                BankFilter.Decision d = f.decide(c.getString(1), c.getString(0), null, en, custom);
                if (d.ok) { JSObject o = new JSObject(); o.put("text", d.text); o.put("at", c.getLong(2)); if (d.bank != null) o.put("bank", d.bank); items.put(o); }
                else if ("secret".equals(d.reason)) secrets++;
            }
        } catch (Exception e) { call.reject("Не удалось прочитать СМС"); return; }
        JSObject r = new JSObject(); r.put("items", items); r.put("scanned", total); r.put("secrets", secrets);
        call.resolve(r);
    }

    @PluginMethod
    public void drain(PluginCall call) {
        JSONArray q = BankStore.drain(getContext());
        JSObject r = new JSObject();
        try { r.put("items", new JSArray(q.toString())); } catch (Exception e) { r.put("items", new JSArray()); }
        call.resolve(r);
    }
}
