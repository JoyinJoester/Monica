import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "ru-RU",
  description: "Monica — это локальный менеджер паролей с открытым исходным кодом.",
  markdown: {
    container: {
      tipLabel: "Подсказка",
      warningLabel: "Предупреждение",
      dangerLabel: "Опасность",
      infoLabel: "Информация",
      detailsLabel: "Подробнее",
    },
  },
  themeConfig: {
    darkModeSwitchLabel: "Тема",
    sidebarMenuLabel: "Меню",
    returnToTopLabel: "Вернуться наверх",
    lastUpdatedText: "Последнее обновление",
    outline: { level: [2, 4], label: "Навигация по странице" },
    docFooter: { prev: "Предыдущая страница", next: "Следующая страница" },
    nav: [
      { text: "Главная", link: "/ru/" },
      { text: "Руководство", link: "/ru/guide/intro" },
      { text: "Документация", link: "/ru/reference/catalogue" },
      {
        text: "Страницы",
        items: [
          { text: "Архив", link: "/ru/archives" },
          { text: "Список статей", link: "/ru/articleOverview" },
        ],
      },
      { text: "✨ Донат", link: "/ru/personal/" },
    ],
    editLink: {
      text: "Редактировать эту страницу на GitHub",
      pattern: "https://github.com/JoyinJoester/Monica/edit/master/docs/:path",
    },
  },
});