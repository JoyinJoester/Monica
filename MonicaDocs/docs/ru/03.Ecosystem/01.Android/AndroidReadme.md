---
title: Описание версии Android
date: 2025-06-01 23:00:00
permalink: /ecosystem/AndroidReadme
---

# Monica for Android

> Android-клиент локального хранилища паролей Monica  
> Android 8.0+ · Jetpack Compose · MDBX / Bitwarden / KeePass · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Кратко
Monica for Android — нативный Android-клиент в экосистеме хранилища паролей Monica. Он следует принципу <mark>local-first</mark> и фокусируется на безопасном, контролируемом и восстанавливаемом управлении паролями на Android.

Ключевые возможности: `Local Vault`, `интеграция Bitwarden`, `совместимость KeePass`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for Android — локальное хранилище паролей, объединяющее **Bitwarden** и **KeePass**. Основной акцент сделан на локальном хранении: приложение помогает управлять учетными записями, паролями, 2FA, приватными заметками и чувствительными вложениями на Android, а также поддерживает резервное копирование, восстановление и межплатформенную синхронизацию через WebDAV.

Текущий курс проекта: **local-first, совместимость с Bitwarden, интеграция KeePass, полноценный переход на MDBX и Android 8.0+**. Разработка сосредоточена на локальном зашифрованном vault, нативном AutoFill, TOTP, биометрии, Passkey, WebDAV backup/restore и управлении разными типами записей.

---

## Для кого это ✅

Monica for Android подходит пользователям, которые:

- Хотят управлять паролями по модели **local-first** и не передавать ключевые учетные данные сторонним облачным сервисам.
- Используют Bitwarden, но также ведут данные KeePass (`.kdbx`) и хотят единый вход для управления.
- Нуждаются в AutoFill, биометрической разблокировке и управлении TOTP в повседневном использовании Android.
- Хотят применять собственную инфраструктуру WebDAV для резервного копирования и восстановления между устройствами, включая офлайн-сценарии или низкую задержку.

## Текущие функции 🎯

### Local Vault

- Создание, открытие и блокировка локального зашифрованного vault
- Local-first стратегия для снижения зависимости от внешних сервисов
- Поддержка MDBX и традиционных форматов зашифрованных баз данных

### Интеграция нескольких экосистем

- **Совместимость Bitwarden**: синхронизация через Bitwarden API, импорт/экспорт и привязка аккаунта
- **Совместимость KeePass**: нативное чтение/запись файлов `.kdbx`, импорт и миграция
- **Локальный формат MDBX**: собственный вложенный зашифрованный формат базы данных Monica с историей версий и обнаружением конфликтов

### Управление разными типами записей

- Базовый CRUD для логинов, защищенных заметок, банковских карт, документов, TOTP и других типов
- Хранение вложений для каждой записи
- Избранное, теги и классификация по папкам

### Избранное и поиск

- Представления с приоритетом избранного
- Быстрый поиск по заголовку, домену и тегам
- Полнотекстовый поиск

### TOTP и двухэтапная проверка

- Импорт через URI `otpauth://`
- Импорт QR-кодов с камеры или из галереи
- Генерация кодов и обновление в реальном времени

### Нативная интеграция Android

- **AutoFill**: системное заполнение учетных данных, совместимое с большинством приложений и браузеров
- **Биометрия**: Face ID / отпечаток через `BiometricPrompt`
- **Passkey / Credential Provider**: поддержка Passkey на Android 14+
- **Фоновое резервное копирование**: автоматический WebDAV backup по расписанию через `WorkManager`

### Резервное копирование и восстановление WebDAV

- Загрузка, скачивание и проверка целостности SHA-256
- Предпросмотр восстановления и выбор версии
- Проверка перед восстановлением

## Текущий статус ⚡

- Проект активно разрабатывается и поддерживается
- Широкая поддержка устройств Android 8.0-15; некоторые новые функции, например Passkey, требуют Android 14+

## Сведения для разработки 👨‍💻

### Техническая архитектура

```text
UI Layer (Compose)
    →
ViewModel / ViewModel State
    →
Repository Pattern
    →
Room Database + Keystore + Biometric
    →
Bitwarden API / KeePass Library / MDBX FFI
    →
WebDAV / Encryption / Security
```

### Основной стек ⚙️

- **UI**: <mark>Jetpack Compose</mark> + Material 3 + Navigation Compose
- **Данные**: Room (SQLite ORM) + DAO + Repository
- **Состояние**: ViewModel + Kotlin Flow + DataStore Preferences
- **DI**: Koin (запуск в `MonicaApplication`)
- **Безопасность**: Android Keystore, EncryptedSharedPreferences, BiometricPrompt
- **Фоновые задачи**: WorkManager (`AutoBackupWorker` для автоматического WebDAV backup)
- **Сеть и протоколы**: Retrofit + OkHttp (Bitwarden API), kotpass (KeePass), sardine-android (WebDAV)
- **Асинхронность**: Coroutines + Flow
- **Сканирование**: CameraX + ML Kit (QR-коды), Credentials API (Passkey)

### Слои проекта 📁

- `takagi/ru/monica/ui`: Compose-страницы и UI-компоненты
- `takagi/ru/monica/data`: сущности Room, DAO, миграции базы данных
- `takagi/ru/monica/repository`: инкапсуляция и агрегирование доступа к данным
- `takagi/ru/monica/security`: шифрование, ключи и аутентификация
- `takagi/ru/monica/bitwarden`: Bitwarden API, шифрование, синхронизация и логика ViewModel
- `takagi/ru/monica/autofill`: сервисы и сценарии AutoFill
- `takagi/ru/monica/passkey`: интеграция Credential Provider для Android 14+
- `takagi/ru/monica/workers`: фоновые задачи, например автоматический WebDAV backup

### Модель безопасности 🔐

- **Шифрование**: AES-256-GCM (аутентифицированное шифрование)
- **Вывод ключа**: PBKDF2-HMAC-SHA256 с большим числом итераций
- **Локальная защита**: хэш мастер-пароля и настройки безопасности управляются Android Keystore
- **Сетевая граница**: используется только для Bitwarden API sync, WebDAV backup/restore и обновления QR-кодов

### Сборка и зависимости 📦

- **JDK**: 17+
- **Android Studio**: последняя стабильная версия
- **Конфигурация**: `compileSdk 35`, `targetSdk 34`, `minSdk 26`
- **Инструменты**: AGP `8.6.0`, Kotlin `2.0.21`, Compose BOM `2026.03.00`
- **Версии**: см. `gradle/libs.versions.toml` и `app/build.gradle`

### Быстрые команды

Сборка debug APK:

```bash
./gradlew :app:assembleDebug
```

Запуск unit-тестов:

```bash
./gradlew test
```

Android instrumentation tests (нужно устройство или эмулятор):

```bash
./gradlew connectedAndroidTest
```

---

## Дорожная карта 🚦

### Приоритеты стабильной версии

- Базовые возможности MDBX Local Vault
- Импорт/синхронизация с Bitwarden
- Чтение/запись KeePass `.kdbx`
- Управление разными типами записей
- Android AutoFill
- TOTP и QR-коды
- WebDAV backup/restore

### В процессе

- Улучшение биометрического опыта (Face / отпечаток)
- Проверка Passkey на реальных устройствах (Android 14+)
- Оптимизация офлайн-поиска
- Полная миграция и совместимость MDBX

### Будущие возможности

- Больше облачных сервисов синхронизации (OneDrive, Google Drive и др.)
- Интеграция браузерного расширения
- Синхронизация данных с iOS-версией
- Продвинутое шифрование и аудит безопасности
- Виджеты и быстрый доступ

---

## Благодарности 🙏

Дизайн, совместимость и часть функционального направления Monica вдохновлены и поддержаны следующими проектами:

- [Keyguard](https://github.com/AChep/keyguard-app) - ориентир для UX и взаимодействия Android-менеджера паролей
- [Bitwarden](https://bitwarden.com/) - важный ориентир по экосистеме open-source менеджеров паролей, модели vault и синхронизации
- [KeePass](https://keepass.info/) - основа локального vault-подхода и совместимости с `.kdbx`
- [Stratum Auth](https://github.com/stratumauth/app) - ориентир по UX аутентификатора, иконкам и совместимости

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## Лицензия

Copyright (c) 2025 JoyinJoester

Monica for Android распространяется как open source по [GNU General Public License v3.0](LICENSE).

## Атрибуция сторонних иконок

- Проект включает иконки из [Stratum Auth app](https://github.com/stratumauth/app) (версия [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), каталоги [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Права на названия брендов и логотипы принадлежат их владельцам
