# Connection Service

Сервис хранит подключения внешних аккаунтов Portable Agent и безопасно выдаёт короткоживущие access
token разрешённым сервисам. Он не вызывает Google Calendar и не хранит команды пользователя.

Сервис хранит модель подключения и зашифрованные refresh token в PostgreSQL. Публичный API умеет
начинать и завершать OAuth flow, показывать подключения пользователя и безопасно отключать их.

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

OpenAPI берётся только из подписанного GitHub Release `portable-agent/contracts`. Обновить локальный
снимок контракта можно командой:

```powershell
./scripts/update-contract.ps1 -Version 2.7.0
```

## Границы

Короткая актуальная карта находится в [SERVICE.md](SERVICE.md), архитектура — в
[docs/architecture.md](docs/architecture.md), запуск и сбои — в [docs/runbook.md](docs/runbook.md).
