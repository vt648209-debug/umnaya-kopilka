package ru.varim.kopilka;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;

/** Те же случаи, что в test/lev-0511.test.js: правила Android совпадают с веб-версией. */
public class BankFilterTest {
    static BankFilter f;

    @BeforeClass
    public static void load() throws Exception {
        f = new BankFilter(new String(Files.readAllBytes(Paths.get("src/main/assets/bank-rules.json")), StandardCharsets.UTF_8));
    }

    @Test public void codesAreDropped() {
        String[] secrets = {
            "Код для входа в СберБанк Онлайн: 52341. Никому не сообщайте код",
            "Пароль для подтверждения покупки 1 500р в OZON: 7781",
            "Никому не говорите этот код: 1234",
            "Ваш одноразовый пароль 9981",
            "Для подтверждения перевода 5000 р введите 4471",
            "T-Bank: OTP 991203 для оплаты 1 200 ₽",
            "Не сообщайте PIN никому. Покупка 300 р",
        };
        for (String s : secrets) {
            BankFilter.Decision d = f.decide(s, "900", null, null, null);
            assertFalse(s, d.ok);
            assertEquals(s, "secret", d.reason);
            assertNull(d.text);
        }
    }

    @Test public void onlyWhitelistedBankOperations() {
        BankFilter.Decision d = f.decide("СЧЁТ2341 15:02 Покупка 250р Пятёрочка Баланс: 12 300.50р", "900", null, null, null);
        assertTrue(d.ok); assertEquals("sber", d.bank);
        assertEquals("tbank", f.decide("Оплата 1500 ₽, Магнит. Доступно 3000 ₽", null, "com.idamob.tinkoff.android", null, null).bank);
        assertEquals("not-bank", f.decide("Покупка 250р", "MegaFon", null, null, null).reason);
        assertEquals("not-bank", f.decide("Покупка 250р Баланс 100р", "900", null, Arrays.asList("tbank"), null).reason);
        assertTrue(f.decide("Покупка 250р Баланс 100р", "MyBank", null, Arrays.asList(), Arrays.asList("MyBank")).ok);
        assertEquals("not-operation", f.decide("Акция! Кешбэк 10% в кафе", "900", null, null, null).reason);
        assertTrue(f.decide("Покупка 990р SHOPPING MALL Баланс 1000р", "900", null, null, null).ok);
        assertTrue(f.decide("Зачисление зарплаты 60 000,00 руб.", "900", null, null, null).ok);
    }
}
