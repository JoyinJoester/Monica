---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Monica Pass"
  text: "Безопасно · Локально · Открытый код"
  tagline: "Monica — это локальный менеджер паролей с открытым исходным кодом. Управляйте своими паролями, кодами 2FA и зашифрованными заметками. Все данные хранятся исключительно на вашем локальном устройстве, минуя любые облака."
  image:
    src: /themepng.png
    alt: Иконка приложения Monica
  actions:
    - theme: brand
      text: Скачать для Android
      link: https://github.com/Monica-Pass/Monica/releases
    - theme: brand
      text: Версия для Windows
      link: https://github.com/Monica-Pass/Monica-by-Avalonia
    - theme: brand
      text: Версия для iOS
      link: https://github.com/Monica-Pass/Monica-for-iOS
    - theme: alt
      text: Введение в проект
      link: /ru/guide/intro
    - theme: alt
      text: Документация проекта
      link: /ru/reference/catalogue

features:
  - title: Менеджер паролей
    details: Поддержка автозаполнения и хранения множества полей. Защищено высокопрочным шифрованием AES-256, гарантируя, что данные остаются только на вашем устройстве.
    icon: 🔑
  - title: Аутентификатор 2FA
    details: Встроенный аутентификатор TOTP с поддержкой сканирования QR-кодов. Автоматический отсчет времени и копирование в один клик до истечения срока действия кода.
    icon: 🕒
  - title: Карты и заметки
    details: Безопасное хранение банковских карт, документов и личных заметок. Полный контроль над всей вашей конфиденциальной информацией.
    icon: 💳
  - title: Material Design 3
    details: Строгое соответствие спецификациям Google Material Design 3. Поддержка динамических цветов и темной темы. Чистый интерфейс без рекламы.
    icon: 🎨
  - title: Аппаратный Keystore
    details: Ключи шифрования защищены системным хранилищем Android Keystore (TEE), обеспечивая аппаратную границу изоляции для безопасности ваших данных.
    icon: 🛡️
  - title: Архитектура Zero-Knowledge
    details: По умолчанию работает полностью офлайн и не требует разрешений на доступ к сети. Это гарантирует, что даже разработчики не смогут получить доступ к вашим данным.
    icon: 🚫
---