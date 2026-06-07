---
title: Описание версии iOS
date: 2025-06-01 23:00:00
permalink: /ecosystem/iOSReadme
---

# Monica for iOS

> iOS-клиент local-first хранилища паролей Monica  
> iOS 17+ · SwiftUI · MDBX Vault · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-iOS/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-iOS?style=flat-square)](https://github.com/Monica-Pass/Monica-for-iOS/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note Кратко
Monica for iOS — нативный iOS-клиент в экосистеме хранилища паролей Monica. Он следует принципу <mark>local-first</mark> и фокусируется на безопасном, контролируемом и восстанавливаемом управлении паролями на iPhone.

Ключевые возможности: `MDBX Vault`, `AutoFill`, `TOTP`, `WebDAV`.
:::

Monica for iOS — нативный iOS-клиент в экосистеме хранилища паролей Monica. Он следует **local-first** подходу и фокусируется на безопасном, контролируемом и восстанавливаемом управлении паролями на iPhone.

Текущее направление проекта строится вокруг **MDBX first, iOS 17+, iPhone first и моста Rust/Swift UniFFI**. Основной фокус — локальный зашифрованный vault, нативный AutoFill, TOTP, WebDAV backup/restore и управление разными типами записей, совместимое с моделями данных Android / MDBX.

Первый публичный релиз в первую очередь закроет:

- Базовые возможности Local Vault
- Управление основными записями
- WebDAV backup/restore
- Более полную поддержку AutoFill

Дальше планируется:

- Passkey
- Совместимость Bitwarden
- Поддержка вложений
- Больше возможностей облачной синхронизации

---

## Для кого это

Monica for iOS подходит пользователям, которые:

- Хотят **local-first** менеджер паролей и не хотят доверять ключевые учетные данные сторонним сервисам.
- Уже управляют паролями, TOTP, приватными заметками и связанными данными в Monica Android или экосистеме MDBX.
- Нуждаются в нативных возможностях iOS: **AutoFill, Face ID / Touch ID и локальное резервное копирование/восстановление**.
- Хотят управлять на iPhone разными типами записей: логинами, защищенными заметками, TOTP, банковскими картами и метаданными документов.

---

## Текущие функции

### Local MDBX Vault

- Создание, открытие и блокировка локального зашифрованного vault
- Local-first стратегия для снижения зависимости от внешних сервисов

### Управление разными типами записей

- Базовый CRUD для логинов, защищенных заметок, TOTP, банковских карт, метаданных документов и других типов
- Совместимость с моделью данных Monica / MDBX

### Избранное и поиск

- Приоритет избранного
- Режим просмотра только избранного
- Поиск внутри сессии
- Восстановление после мягкого удаления

### TOTP

- Импорт через URI `otpauth://`
- Импорт QR-кода
- Генерация кодов и обновление оставшихся секунд

### iOS AutoFill

- Чтение зашифрованного индекса через Credential Provider Extension
- Возврат системе учетных данных для заполнения
- Нативная поддержка сценариев password AutoFill

### Безопасная разблокировка

- Сочетание Keychain, LocalAuthentication и MDBX `security_key`
- Мастер-пароль не хранится в открытом виде
- Чувствительная информация по возможности остается в локальной границе безопасности

### WebDAV

- Загрузка, скачивание и проверка целостности SHA-256
- Предпросмотр восстановления
- Проверка открытия перед восстановлением

### OneDrive

- Уже подключены MSAL и Microsoft Graph app-folder provider
- Проверка с реальными аккаунтами и сетью продолжается

### KeePass / KDBX

- Основные современные сценарии чтения/записи KDBX3 / KDBX4 уже совместимы
- Дальше будет расширяться проверка в реальных сценариях

---

## Текущий статус

- Проект все еще находится на этапе разработки iOS-клиента и проверки на реальных устройствах.
- При неподписанном тестировании в симуляторе с `CODE_SIGNING_ALLOWED=NO` недоступность App Group container ожидаема.
- AutoFill QuickType, Credential Provider, App Group, Keychain access group, сканирование TOTP камерой и похожие возможности требуют дальнейшей проверки в подписанной среде на реальном устройстве.

---

## Сведения для разработки

### Структура каталогов 🗂️

::: tip Обзор структуры проекта
```text
.
├── Monica.xcodeproj/
├── App/
│   └── MonicaApp/
├── Extensions/
│   ├── MonicaAutoFillExtension/
│   ├── MonicaShareExtension/
│   └── MonicaWidgetExtension/
├── Tests/
│   └── MonicaTests/
├── Scripts/
│   ├── build-mdbx-xcframework.sh
│   └── generate-mdbx-swift-bindings.sh
├── Artifacts/
│   ├── MDBX/
│   └── MSAL/
├── Generated/
│   └── MDBXUniFFI/
└── SwiftPackages/
    ├── MSAL/
    ├── MonicaCore/
    ├── MonicaMDBX/
    ├── MonicaSecurity/
    ├── MonicaStorage/
    ├── MonicaSync/
    └── MonicaUI/
```
:::

### Технологии ⚙️

- **App layer**: <mark>SwiftUI</mark>, Observation, AuthenticationServices, LocalAuthentication, Keychain, WidgetKit
- **Local Vault**: MDBX (Rust) + UniFFI → `MonicaMDBX` (Swift)
- **Core logic**: `MonicaCore` (TOTP, разбор `otpauth://`, генерация безопасных паролей)
- **Storage layer**: `MonicaStorage` (vault repository, совместимость KDBX, индекс AutoFill)
- **Security layer**: `MonicaSecurity` (граница Keychain / LocalAuthentication)
- **Sync layer**: `MonicaSync` (WebDAV, OneDrive, адаптеры Bitwarden)

### MDBX UniFFI 🔧

Bridge crate со стороны Rust:

```text
mdbx/crates/mdbx-ios-ffi
```

Генерация Swift bindings:

```bash
Scripts/generate-mdbx-swift-bindings.sh
```

Сборка iOS XCFramework:

```bash
Scripts/build-mdbx-xcframework.sh
```

Если отсутствует UniFFI CLI:

```bash
cargo install uniffi --version 0.31.1 --locked --features cli
```

Важно: не запускайте `Scripts/build-mdbx-xcframework.sh` параллельно с Xcode build. Скрипт заменяет `MonicaMDBXGenerated.xcframework` и может привести к ошибке чтения в Xcode.

---

## Команды проверки

::: tip Быстрый запуск тестов и сборки

### Swift Package tests

Выполните команды по очереди в терминале macOS:

```bash
cd SwiftPackages/MonicaCore && swift test
cd ../MonicaMDBX && swift test
cd ../MonicaStorage && swift test
cd ../MonicaSecurity && swift test
cd ../MonicaSync && swift test
cd ../MonicaUI && swift test
```

### iOS Simulator build без подписи

Сначала выведите доступные симуляторы:

```bash
xcrun simctl list devices available
```

Затем замените `<iPhone simulator UUID>` и выполните:

```bash
xcodebuild \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

### iOS Simulator XCTest

```bash
xcodebuild test \
  -project Monica.xcodeproj \
  -scheme Monica \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<iPhone simulator UUID>" \
  CODE_SIGNING_ALLOWED=NO
```

:::

---

## Дорожная карта

### Приоритеты первого релиза

- MDBX Local Vault
- Управление основными записями
- TOTP
- WebDAV
- iOS AutoFill

### В процессе

- Проверка на подписанном реальном устройстве
- Keychain / LocalAuthentication
- App Group
- QuickType
- Сканирование камерой

### Будущие возможности

- Passkey
- Двусторонняя синхронизация Bitwarden
- Работа с вложениями
- Больше облачных сервисов
- Нативные точки входа iOS, например Widget / Live Activity

---

## Благодарности

Дизайн, совместимость и часть функционального направления Monica вдохновлены и поддержаны следующими проектами:

- [Bitwarden](https://bitwarden.com/) - важный ориентир по экосистеме open-source менеджеров паролей, модели vault и синхронизации
- [KeePass](https://keepass.info/) - основа локального vault-подхода и совместимости с `.kdbx`
- [Keyguard](https://github.com/AChep/keyguard-app) - ориентир для UX Android-менеджера паролей
- [Stratum Auth](https://github.com/stratumauth/app) - ориентир по UX аутентификатора, иконкам и совместимости

### Краткая дорожная карта 🚦

- **Обязательно для первого релиза**: MDBX Local Vault, управление основными записями, TOTP, WebDAV, iOS AutoFill
- **Ближайшая проверка**: подписанное реальное устройство, Keychain / LocalAuthentication, App Group, QuickType, камера
- **Будущие планы**: Passkey, двусторонняя синхронизация Bitwarden, вложения, больше облачных сервисов, Widget / Live Activity

> Эти пункты можно напрямую превратить в issue или milestone для отслеживания и распределения.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-iOS&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-iOS&Date)

---

## Лицензия

Copyright (c) 2025 JoyinJoester

Monica for iOS распространяется как open source по [GNU General Public License v3.0](LICENSE).

## Атрибуция сторонних иконок

- Проект использует сторонние иконки из [Stratum Auth app](https://github.com/stratumauth/app) (версия [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0), каталоги [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons), GPL-3.0)
- Права на названия брендов и логотипы принадлежат их владельцам
