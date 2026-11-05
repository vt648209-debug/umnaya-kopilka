package ru.varim.kopilka;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KopilkaBridgePlugin.class);
        super.onCreate(savedInstanceState);
        takeShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        takeShare(intent);
    }

    /** «Поделиться → Умная копилка» из СМС, уведомления или любого приложения. */
    private void takeShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) BankStore.offerShared(this, text);
        intent.setAction(Intent.ACTION_MAIN);
    }
}
