package ru.varim.kopilka;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Очередь операций для копилки на телефоне. Пересыльщик (уведомления/СМС) кладёт сюда только
 * прошедшие фильтр операции; приложение забирает их при открытии (или сразу, если открыто).
 * Ничего не уходит в интернет: данные остаются в приложении.
 */
public final class BankStore {
    private static final String PREFS = "kopilka_bridge";
    private static final int MAX = 300;
    private static BankFilter filter;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static synchronized BankFilter filter(Context c) {
        if (filter == null) {
            try (InputStream in = c.getAssets().open("bank-rules.json")) {
                byte[] buf = new byte[in.available()];
                int n = 0; while (n < buf.length) { int r = in.read(buf, n, buf.length - n); if (r < 0) break; n += r; }
                filter = new BankFilter(new String(buf, 0, n, StandardCharsets.UTF_8));
            } catch (Exception e) { throw new RuntimeException("bank-rules.json", e); }
        }
        return filter;
    }

    static List<String> list(Context c, String key) {
        String v = prefs(c).getString(key, "");
        List<String> out = new ArrayList<>();
        for (String s : v.split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
    static void setList(Context c, String key, List<String> v) { prefs(c).edit().putString(key, String.join("\n", v)).apply(); }
    /** Выбранные банки; пока пользователь не выбрал — null (все из списка). */
    static List<String> enabled(Context c) { return prefs(c).contains("banks") ? list(c, "banks") : null; }

    /** Решение фильтра + постановка в очередь. Возвращает элемент очереди или null, если отброшено. */
    static synchronized JSONObject offer(Context c, String text, String sender, String pkg, String src) {
        BankFilter.Decision d = filter(c).decide(text, sender, pkg, enabled(c), list(c, "custom"));
        SharedPreferences p = prefs(c);
        if (!d.ok) {
            if ("secret".equals(d.reason)) p.edit().putInt("droppedSecrets", p.getInt("droppedSecrets", 0) + 1).apply();
            return null;
        }
        long now = System.currentTimeMillis();
        // банковские пуши часто обновляются — одинаковый текст в течение 2 минут не дублируем
        String last = p.getString("lastText", ""); long lastAt = p.getLong("lastAt", 0);
        if (last.equals(d.text) && now - lastAt < 120_000) return null;
        try {
            JSONObject item = new JSONObject();
            item.put("text", d.text); item.put("at", now); item.put("src", src);
            if (d.bank != null) item.put("bank", d.bank);
            JSONArray q = new JSONArray(p.getString("queue", "[]"));
            q.put(item);
            while (q.length() > MAX) q.remove(0);
            p.edit().putString("queue", q.toString()).putString("lastText", d.text).putLong("lastAt", now)
                .putInt("received", p.getInt("received", 0) + 1).putLong("lastReceived", now).apply();
            KopilkaBridgePlugin.deliver(item);
            return item;
        } catch (Exception e) { return null; }
    }

    /** «Поделиться» из другого приложения: только фильтр кодов, остальное разберёт копилка. */
    static synchronized JSONObject offerShared(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return null;
        if (filter(c).hasSecret(text)) return null;
        try {
            JSONObject item = new JSONObject();
            item.put("text", text.trim().length() > 500 ? text.trim().substring(0, 500) : text.trim());
            item.put("at", System.currentTimeMillis()); item.put("src", "share");
            SharedPreferences p = prefs(c);
            JSONArray q = new JSONArray(p.getString("queue", "[]")); q.put(item);
            p.edit().putString("queue", q.toString()).apply();
            KopilkaBridgePlugin.deliver(item);
            return item;
        } catch (Exception e) { return null; }
    }

    static synchronized JSONArray drain(Context c) {
        SharedPreferences p = prefs(c);
        try { JSONArray q = new JSONArray(p.getString("queue", "[]")); p.edit().putString("queue", "[]").apply(); return q; }
        catch (Exception e) { p.edit().putString("queue", "[]").apply(); return new JSONArray(); }
    }
    static int queued(Context c) { try { return new JSONArray(prefs(c).getString("queue", "[]")).length(); } catch (Exception e) { return 0; } }
    static List<String> split(String csv) { return csv == null || csv.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(csv.split(","))); }
}
