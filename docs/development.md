# Разработка

Нужны Java 25 и Docker Desktop.

```powershell
docker compose up -d postgres
./gradlew spotlessApply test
./gradlew bootRun
```

Новый сценарий начинается с падающего теста. Быстрые unit-тесты проверяют правила, MVC slice — HTTP,
Testcontainers — jOOQ и Flyway на настоящем PostgreSQL.

Контракт обновляется только из GitHub Release с проверкой checksum и artifact attestation:

```powershell
./scripts/update-contract.ps1 -Version 2.7.0
```

Для локальной проверки шифра включи его внешними properties. Значения — Base64 от случайных 32 байт;
не сохраняй их в `.env` под Git:

```powershell
$env:CONNECTION_TOKEN_KEYS_ENABLED = "true"
$env:CONNECTION_TOKEN_KEYS_CURRENT_VERSION = "1"
$env:CONNECTION_TOKEN_KEYS_ITEMS_0_VERSION = "1"
$env:CONNECTION_TOKEN_KEYS_ITEMS_0_VALUE = "<base64-32-bytes>"
```

При ротации добавь следующий элемент `ITEMS_1`, переключи `CURRENT_VERSION`, а предыдущий ключ оставь
до перешифрования или удаления всех старых записей.

TTL незавершённой OAuth-сессии меняется без кода:

```powershell
$env:CONNECTION_OAUTH_SESSION_TTL = "10m"
```

Нулевое или отрицательное значение останавливает запуск, чтобы state не жил неограниченно.

Google adapter выключен по умолчанию. Для sandbox нужны значения из secret manager и точный callback,
зарегистрированный в Google Cloud Console:

```powershell
$env:GOOGLE_OAUTH_ENABLED = "true"
$env:GOOGLE_OAUTH_CLIENT_ID = "<client-id>"
$env:GOOGLE_OAUTH_CLIENT_SECRET = "<client-secret>"
$env:GOOGLE_OAUTH_REDIRECT_URI = "<registered-callback-uri>"
```

Endpoint properties можно переопределить для локального OAuth stub. Никогда не добавляй client secret,
authorization code, access token или refresh token в `.env` под Git.

Публичные endpoints проверяют JWT. Trust settings не имеют значений по умолчанию и всегда приходят
из окружения. Для локального Keycloak передай:

```powershell
$env:OIDC_ISSUER = "http://localhost:8081/realms/portable-agent"
$env:OIDC_JWKS_URL = "http://localhost:8081/realms/portable-agent/protocol/openid-connect/certs"
$env:OIDC_AUDIENCE = "connection-service"
$env:OIDC_INTERNAL_CLIENTS = "action-service"
```

`OIDC_INTERNAL_CLIENTS` — разделённый запятыми allowlist значений JWT claim `azp`. Пустой список
безопасно запрещает всем клиентам internal token endpoint.
