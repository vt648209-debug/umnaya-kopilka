package ru.varim.kopilka;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Фильтр банковских СМС и уведомлений — та же логика, что в src/bankfilter.js.
 * Правила (банки, слова-секреты, признаки операции) читаются из assets/bank-rules.json.
 * Сообщения с кодами и паролями отбрасываются здесь, на телефоне, и никуда не передаются.
 */
public final class BankFilter {
    public static final class Bank {
        public final String id, name;
        public final List<String> packages = new ArrayList<>(), senders = new ArrayList<>();
        Bank(String id, String name) { this.id = id; this.name = name; }
    }
    public static final class Decision {
        public final boolean ok; public final String reason, bank, text;
        Decision(boolean ok, String reason, String bank, String text) { this.ok = ok; this.reason = reason; this.bank = bank; this.text = text; }
    }

    public final List<Bank> banks = new ArrayList<>();
    private final List<Pattern> secret = new ArrayList<>();
    private final List<String> op = new ArrayList<>();
    private final Pattern amount;

    public BankFilter(String json) throws Exception {
        JSONObject r = new JSONObject(json);
        JSONArray b = r.getJSONArray("banks");
        for (int i = 0; i < b.length(); i++) {
            JSONObject o = b.getJSONObject(i);
            Bank bank = new Bank(o.getString("id"), o.getString("name"));
            JSONArray p = o.getJSONArray("packages"); for (int j = 0; j < p.length(); j++) bank.packages.add(p.getString(j));
            JSONArray s = o.getJSONArray("senders"); for (int j = 0; j < s.length(); j++) bank.senders.add(s.getString(j));
            banks.add(bank);
        }
        JSONArray s = r.getJSONArray("secretWords"); for (int i = 0; i < s.length(); i++) secret.add(Pattern.compile(s.getString(i)));
        JSONArray w = r.getJSONArray("opWords"); for (int i = 0; i < w.length(); i++) op.add(low(w.getString(i)));
        amount = Pattern.compile(r.getString("amount"), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static String low(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е'); }
    static String norm(String s) { return low(s).replaceAll("[^a-zа-я0-9]", ""); }

    public boolean hasSecret(String text) {
        String t = low(text);
        for (Pattern p : secret) if (p.matcher(t).find()) return true;
        return false;
    }
    public boolean isOperation(String text) {
        String t = low(text);
        if (!amount.matcher(t).find()) return false;
        for (String w : op) if (t.contains(w)) return true;
        return false;
    }
    public Bank bySender(String sender, List<String> enabled) {
        String s = norm(sender);
        if (s.isEmpty()) return null;
        for (Bank b : banks) {
            if (enabled != null && !enabled.contains(b.id)) continue;
            for (String x : b.senders) if (norm(x).equals(s)) return b;
        }
        return null;
    }
    public Bank byPackage(String pkg, List<String> enabled) {
        for (Bank b : banks) {
            if (enabled != null && !enabled.contains(b.id)) continue;
            if (b.packages.contains(pkg)) return b;
        }
        return null;
    }

    /** sender — для СМС, pkg — для уведомлений; оба null — «Поделиться» из другого приложения. */
    public Decision decide(String text, String sender, String pkg, List<String> enabled, List<String> custom) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) return new Decision(false, "empty", null, null);
        if (hasSecret(body)) return new Decision(false, "secret", null, null);
        if (body.length() > 500) body = body.substring(0, 500);
        if (sender != null || pkg != null) {
            Bank bank = sender != null ? bySender(sender, enabled) : byPackage(pkg, enabled);
            boolean mine = false;
            String who = norm(sender != null ? sender : pkg);
            if (custom != null) for (String c : custom) if (norm(c).equals(who) && !who.isEmpty()) mine = true;
            if (bank == null && !mine) return new Decision(false, "not-bank", null, null);
            if (!isOperation(body)) return new Decision(false, "not-operation", null, null);
            return new Decision(true, null, bank != null ? bank.id : "custom", body);
        }
        if (!isOperation(body)) return new Decision(false, "not-operation", null, null);
        return new Decision(true, null, null, body);
    }
}
