# Runbook

## Проверка состояния

После запуска открой `GET /actuator/health`. Ответ `UP` означает, что приложение и обязательные
проверки готовы.

## Сервис не запускается

1. Проверь `docker compose ps`.
2. Проверь `DATABASE_URL`, `DATABASE_USER` и `DATABASE_PASSWORD`.
3. Запусти `./gradlew test`.
4. Не записывай token, authorization code и client secret в issue или лог.

## Восстановление данных

Backup/restore будет описан после появления первой таблицы. До этого сервис не хранит бизнес-данные.
