# Monica Локальное хранилище паролей

<div align="center">

[中文](README.md) | [English](README_EN.md) | [日本語](README_JA.md) | [Tiếng Việt](README_VI.md) | **Русский** | [黑羽川](readme_Nya.md)

<img src="image/themepng.png" alt="Monica App Icon" width="500" />

<p><strong>Локально‑ориентированное хранилище паролей, объединяющее Bitwarden и KeePass</strong></p>
<p>Android / Browser · Local Vault · TOTP · WebDAV Backup</p>

<p>
	Дружественная ссылка:
	<a href="https://linux.do" title="Linux.do">
		<img src="https://www.google.com/s2/favicons?domain=linux.do&sz=64" alt="Linux.do" width="22" />
		Linux.do
	</a>
</p>

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Monica/total?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/commits)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
<br>
<a href="https://trendshift.io/repositories/27059" target="_blank"><img src="https://trendshift.io/api/badge/repositories/27059" alt="JoyinJoester%2FMonica | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

</div>

Monica — это локальное хранилище паролей, которое объединяет **Bitwarden** и **KeePass**.
Проект построен вокруг принципа local-first и помогает управлять паролями, 2FA, защищенными заметками и конфиденциальными вложениями на Android и в браузере.

Проект создан с использованием ИИ. Сделайте регулярные резервные копии привычкой, чтобы избежать потери данных. Приватность данных бесценна, поэтому не храните все данные только в одном приложении.

Сайт: https://joyinjoester.github.io/Monica/

> Monica for Windows архивирован. Исторический код: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)
>
> Monica for Browser архивирован. Новый Monica Extension сейчас переписывается и активно разрабатывается — следите за обновлениями.
>
> Сейчас проект в основном поддерживается мной одним, поэтому времени и ресурсов ограниченно. Из-за этого Monica for Wear временно не может обновляться постоянно. Сейчас мой основной фокус — улучшение функций, опыта и стабильности Monica for Android. Спасибо за понимание и поддержку.

---

## Пользователю прежде всего

### Для кого Monica
- Для тех, кто хочет локальное управление паролями вместо полностью облачного хранения.
- Для пользователей, которые работают и с Bitwarden, и с KeePass (`.kdbx`).
- Для тех, кому нужны и Android‑клиент, и автозаполнение в браузере.

### Что вы получаете
- Локальный зашифрованный vault для логинов, карт, персональных данных, заметок и вложений.
- Двойную экосистему: на Android доступна интеграция Bitwarden API/синхронизации и чтение/запись KeePass (`.kdbx`).
- Опциональную синхронизацию и резервное копирование через собственную инфраструктуру WebDAV.
- Встроенное управление TOTP в одном приложении.

### Локальный формат базы данных MDBX
MDBX — это разрабатываемый Monica локально-ориентированный зашифрованный формат vault. Это не просто таблица паролей: формат проектируется вокруг вложенных папок, вложений, истории commit, обнаружения конфликтов, удаления через tombstone, восстановления из snapshot и режимов безопасности Tiga.

Если вы хотите подключить MDBX в другом клиенте, начните с [MDBX workspace README](mdbx/README.md) и [MDBX client integration guide](mdbx/CLIENT_INTEGRATION_GUIDE.md). Полная спецификация формата находится в [mdbx-doc](mdbx-doc/README.md).

### Быстрая установка

Android:
1. Скачайте последний APK из [Releases](https://github.com/JoyinJoester/Monica/releases).
2. Установите на Android 8.0+ и задайте мастер‑пароль.

Расширение браузера (Chrome / Edge):
1. Соберите в каталоге `Monica for Browser`.
2. Откройте `chrome://extensions/` и включите режим разработчика.
3. Нажмите «Загрузить распакованное расширение» и выберите папку `dist`.

### Известное ограничение
- Из-за ограничений совместимости Monica for Android сейчас не может создавать passkeys на некоторых устройствах Xiaomi HyperOS.

---

## Акцент на Android

### Ключевые возможности
- Локальный vault для хранения учетных данных.
- Сводный импорт/интеграция с KeePass и Bitwarden.
- Быстрый поиск по заголовку, домену и тегам.
- Разблокировка с помощью системной биометрии Android.
- Единое хранение и генерация TOTP.

### Детали реализации
- UI: Jetpack Compose + Material 3 + Navigation Compose.
- Data: Room (`PasswordDatabase`) + DAO + Repository.
- Concurrency: Kotlin Coroutines + Flow.
- DI: Koin (инициализация в `MonicaApplication`).
- Security: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Фоновые задачи: WorkManager (`AutoBackupWorker`) для автоматического WebDAV‑бэкапа.
- Протоколы и интеграции: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV).

### Модель безопасности
- Шифрование: AES-256-GCM (аутентифицированное шифрование).
- KDF: PBKDF2-HMAC-SHA256 (высокие параметры итераций).
- Локальная защита: хэш мастер‑пароля и настройки безопасности управляются локально.
- Сетевой периметр: приложение запрашивает сетевые разрешения в основном для интеграции Bitwarden и WebDAV синхронизации/бэкапа.

---

## Поддержка

Если Monica помогает в работе, рассмотрите поддержку разработки и улучшений безопасности.

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica" width="320"/>
<br/>
<sub>Сканируйте WeChat или Alipay</sub>
</div>

Поддержка в первую очередь идет на:
- Усиление безопасности и аудиты.
- Улучшение UX и стабильности Android.
- Кроссплатформенную согласованность и поддержку документации.

---

## Заметки для разработчиков

### Слои проекта (текущая структура кода)
- `takagi/ru/monica/ui`: экраны и компоненты Compose.
- `takagi/ru/monica/data`: сущности Room, DAO, миграции БД.
- `takagi/ru/monica/repository`: обертки доступа к данным.
- `takagi/ru/monica/security`: шифрование, ключи и логика аутентификации.
- `takagi/ru/monica/bitwarden`: API, криптография, маппинг, синхронизация и логика ViewModel.
- `takagi/ru/monica/autofill`: сервисы и потоки автозаполнения.
- `takagi/ru/monica/passkey`: реализация Credential Provider для Android 14+.
- `takagi/ru/monica/workers`: фоновые задачи, например WebDAV‑бэкап.
- `mdbx`: Rust workspace и документы интеграции клиентов для локального формата базы данных Monica MDBX.

### Используемые зрелые компоненты (проверяемо в репозитории)
- Android UI: Jetpack Compose, Material 3, Navigation Compose.
- Data and state: Room, DataStore Preferences, ViewModel.
- Security: Android Keystore, EncryptedSharedPreferences, BiometricPrompt.
- Network and protocol: Retrofit, OkHttp, Kotlinx Serialization.
- Sync and ecosystem: sardine-android (WebDAV), kotpass (KeePass), Bitwarden API integration.
- Async and jobs: Coroutines, Flow, WorkManager.
- Additional capabilities: CameraX + ML Kit (QR scan), Credentials API (Passkey).

### Сборка и вклад
- Android Studio: последняя стабильная версия.
- JDK: 17+.
- Android config: `compileSdk 35`, `targetSdk 34`, `minSdk 26` (см. `Monica for Android/app/build.gradle`).
- Базовые версии сборки Android: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00` (Material3 синхронизирован по BOM).
- Источник правды версий: `Monica for Android/gradle/libs.versions.toml` и `Monica for Android/app/build.gradle`.
- Техстек браузера: React + TypeScript + Vite (см. `Monica for Browser/package.json`).
- Вклад через Issues и PR приветствуется.

---

## Благодарности

Дизайн Monica, адаптация совместимости и ряд функциональных направлений вдохновлены и поддержаны следующими открытыми проектами и программами:

- [Keyguard](https://github.com/AChep/keyguard-app) — референс по взаимодействию и UX Android‑менеджера паролей.
- [Bitwarden](https://bitwarden.com/) — важный ориентир по open-source экосистеме управления паролями, модели vault и синхронизации.
- [KeePass](https://keepass.info/) — основа философии локального хранилища и совместимости с `.kdbx`.
- [Stratum Auth](https://github.com/stratumauth/app) — референс по опыту аутентификатора, иконкам и связанным аспектам совместимости.

---

## История звезд

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica&type=Date)](https://star-history.com/#JoyinJoester/Monica&Date)

---

## Лицензия

Copyright (c) 2025 JoyinJoester

Monica распространяется по лицензии [GNU General Public License v3.0](LICENSE).

## Указание сторонних иконок

- Проект локально включает ресурсы иконок из [Stratum Auth app](https://github.com/stratumauth/app) (версия [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), каталоги [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0).
- Названия брендов и логотипы принадлежат соответствующим правообладателям.
