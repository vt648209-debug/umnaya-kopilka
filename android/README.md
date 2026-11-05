# Умная копилка — Android

Обёртка Capacitor над веб-версией (тот же движок и интерфейс) + встроенный пересыльщик:

- `BankNotificationService` — уведомления **только** приложений выбранных банков;
- `SmsReceiver` — входящие СМС **только** от отправителей выбранных банков (отправки СМС нет);
- `BankFilter` + `bank-rules.json` — те же правила, что в веб-версии (`src/bankfilter.js`): сообщения с кодами и паролями отбрасываются на телефоне и никуда не передаются;
- `BankStore` — очередь операций на телефоне; приложение забирает её при открытии или сразу, если открыто;
- `KopilkaBridgePlugin` — мост для интерфейса (выбор банков, доступ к уведомлениям, разрешение СМС);
- `MainActivity` — «Поделиться → Умная копилка».

Сборка — GitHub Actions (`.github/workflows/android.yml`) при каждом изменении сайта или этой папки.
APK: `https://github.com/vt648209-debug/umnaya-kopilka/releases/latest/download/umnaya-kopilka.apk`

Локально: `npm install && npm run sync && cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug` (нужен Android SDK).

Подпись: тестовые сборки подписаны общим `debug.keystore` (стандартный пароль `android`) — обновления ставятся поверх.
Для RuStore/Google Play — свой релизный ключ в секретах репозитория: `KOPILKA_KEYSTORE_B64`, `KOPILKA_STORE_PASSWORD`, `KOPILKA_KEY_ALIAS`, `KOPILKA_KEY_PASSWORD`.
