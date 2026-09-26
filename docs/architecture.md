# Архитектура

Сервис следует обычной слоистой архитектуре Spring MVC.

```text
HTTP controller
    -> service
        -> jOOQ repository
            -> PostgreSQL
        -> provider client
            -> OAuth provider
```

Controller отвечает только за HTTP и DTO. Service проверяет правила и открывает транзакцию.
Repository содержит только jOOQ-запросы. Provider client общается с Google либо тестовым OAuth stub.

Refresh token никогда не покидает сервис. Calendar MCP позднее получит только короткоживущий access
token через внутренний endpoint с service JWT.
