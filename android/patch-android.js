'use strict';
// Встраивает пересыльщик в проект Capacitor (android/ создаётся командой `npx cap add android`):
// Java-классы, правила фильтра, разрешения и службы в манифест, иконки, подпись релиза.
// Повторный запуск безопасен.
const fs = require('fs');
const path = require('path');
const H = __dirname;
const A = path.join(H, 'android', 'app');
const PKG = 'ru/varim/kopilka';
const ver = require('./package.json').version;
const put = (from, to) => { fs.mkdirSync(path.dirname(to), { recursive: true }); fs.copyFileSync(from, to); };

// 1. Java
for (const f of fs.readdirSync(H).filter((x) => x.endsWith('.java'))) {
  const dir = f.endsWith('Test.java') ? 'test' : 'main';
  put(path.join(H, f), path.join(A, 'src', dir, 'java', PKG, f));
}
fs.rmSync(path.join(A, 'src', 'test', 'java', 'com'), { recursive: true, force: true });
fs.rmSync(path.join(A, 'src', 'androidTest'), { recursive: true, force: true });

// 2. правила фильтра (те же, что в веб-версии)
put(path.join(H, 'bank-rules.json'), path.join(A, 'src', 'main', 'assets', 'bank-rules.json'));

// 3. манифест
const mf = path.join(A, 'src', 'main', 'AndroidManifest.xml');
let m = fs.readFileSync(mf, 'utf8');
if (!m.includes('KOPILKA-BRIDGE')) {
  m = m.replace('<uses-permission android:name="android.permission.INTERNET" />', `<uses-permission android:name="android.permission.INTERNET" />
    <!-- KOPILKA-BRIDGE: СМС банков (только чтение входящих, отправки нет) -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" />`);
  m = m.replace(/(<activity[\s\S]*?<\/intent-filter>)/, `$1

            <!-- «Поделиться → Умная копилка» -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>`);
  m = m.replace('</application>', `
        <!-- Уведомления банковских приложений: читаются только банки из списка -->
        <service
            android:name=".BankNotificationService"
            android:label="Умная копилка: траты из уведомлений банков"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <!-- Входящие СМС банков -->
        <receiver
            android:name=".SmsReceiver"
            android:permission="android.permission.BROADCAST_SMS"
            android:exported="true">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
    </application>`);
  fs.writeFileSync(mf, m);
}

// 4. версия, тестовая библиотека JSON, подпись релиза (ключ — из секретов GitHub, вводит владелец)
const gf = path.join(A, 'build.gradle');
let g = fs.readFileSync(gf, 'utf8');
// versionCode: 0.5.0 → 500; сборки GitHub Actions — 1000 + номер запуска (всегда новее)
const code = process.env.BUILD_NUMBER ? 1000 + Number(process.env.BUILD_NUMBER) : ver.split('.').reduce((s, x) => s * 100 + Number(x), 0);
g = g.replace(/versionCode \d+/, `versionCode ${code}`).replace(/versionName "[^"]*"/, `versionName "${ver}"`);
if (!g.includes('org.json:json')) g = g.replace('testImplementation "junit:junit:$junitVersion"', 'testImplementation "junit:junit:$junitVersion"\n    testImplementation "org.json:json:20240303"');
if (!g.includes('KOPILKA_KEYSTORE')) {
  g = g.replace('    buildTypes {', `    signingConfigs {
        release {
            // KOPILKA_KEYSTORE и пароли задаются в секретах репозитория (Settings → Secrets → Actions)
            def ks = System.getenv("KOPILKA_KEYSTORE")
            if (ks) {
                storeFile file(ks)
                storePassword System.getenv("KOPILKA_STORE_PASSWORD")
                keyAlias System.getenv("KOPILKA_KEY_ALIAS")
                keyPassword System.getenv("KOPILKA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {`);
  g = g.replace("            minifyEnabled false", '            minifyEnabled false\n            if (System.getenv("KOPILKA_KEYSTORE")) signingConfig signingConfigs.release');
}
fs.writeFileSync(gf, g);

// 5. иконки (icon-<плотность>.png рядом с этим файлом)
const res = path.join(A, 'src', 'main', 'res');
fs.rmSync(path.join(res, 'mipmap-anydpi-v26'), { recursive: true, force: true });
for (const d of ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']) {
  const f = path.join(H, `icon-${d}.png`);
  if (!fs.existsSync(f)) continue;
  for (const n of ['ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png']) put(f, path.join(res, `mipmap-${d}`, n));
}
// 6. тестовая подпись: общий debug.keystore (стандартный пароль android) — чтобы обновления
//    ставились поверх друг друга. Для магазинов — релизный ключ из секретов (п. 4).
if (fs.existsSync(path.join(H, 'debug.keystore'))) {
  let g2 = fs.readFileSync(gf, 'utf8');
  if (!g2.includes('kopilkaDebug')) {
    g2 = g2.replace('    signingConfigs {', `    signingConfigs {
        kopilkaDebug {
            storeFile file("../../debug.keystore")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }`);
    g2 = g2.replace('    buildTypes {', '    buildTypes {\n        debug {\n            signingConfig signingConfigs.kopilkaDebug\n        }');
    fs.writeFileSync(gf, g2);
  }
}
console.log('android пропатчен, версия', ver, 'code', code);
