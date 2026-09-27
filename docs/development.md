# Разработка

Нужны Java 25 и Docker Desktop.

```powershell
docker compose up -d postgres
./gradlew spotlessApply test
./gradlew bootRun
```

Новый сценарий начинается с падающего теста. Быстрые unit-тесты проверяют правила, MVC slice — HTTP,
Testcontainers — jOOQ и Flyway на настоящем PostgreSQL.
