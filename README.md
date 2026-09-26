# Connection Service

Сервис хранит подключения внешних аккаунтов Portable Agent и позднее будет безопасно выдавать
короткоживущие access token разрешённым сервисам. Он не вызывает Google Calendar и не хранит команды
пользователя.

Сейчас репозиторий содержит проверяемый Spring Boot-каркас. Схема подключений, шифрование и OAuth API
будут добавляться отдельными TDD-инкрементами после согласования контрактов.

## Стек

- Java 25 и Spring Boot 4;
- Spring MVC;
- jOOQ и PostgreSQL;
- Flyway;
- Testcontainers;
- Gradle, Spotless, JaCoCo;
- MkDocs и Backstage TechDocs.

## Локальная разработка

```powershell
docker compose up -d postgres
./gradlew bootRun
```

Все проверки:

```powershell
./gradlew spotlessCheck test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/check-docs.ps1
```

## Границы

Короткая актуальная карта находится в [SERVICE.md](SERVICE.md), архитектура — в
[docs/architecture.md](docs/architecture.md), запуск и сбои — в [docs/runbook.md](docs/runbook.md).
