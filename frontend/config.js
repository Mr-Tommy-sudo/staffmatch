window.FP_CONFIG = Object.freeze({
  // Базовый URL Java-бэкенда. Для локального запуска: docker compose up + CORS_ALLOWED_ORIGINS.
  apiBase: 'http://localhost:8080',
  // Если true — при первом открытии показываем экран подключения (ввод initData).
  // Внутри MAX-мессенджера window.WebApp.initData определяется автоматически.
  requireInitDataScreen: true
});