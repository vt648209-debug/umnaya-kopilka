package ru.varim.kopilka;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Входящие СМС: длинные склеиваются по отправителю, дальше — тот же фильтр банков и кодов. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null) return;
        Map<String, StringBuilder> by = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getOriginatingAddress() == null ? "" : m.getOriginatingAddress();
            by.computeIfAbsent(from, k -> new StringBuilder()).append(m.getMessageBody() == null ? "" : m.getMessageBody());
        }
        for (Map.Entry<String, StringBuilder> e : by.entrySet()) BankStore.offer(context, e.getValue().toString(), e.getKey(), null, "sms");
    }
}
