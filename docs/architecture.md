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

## Хранилище

`account_connections` хранит владельца (`tenant_id`, `actor_id`), провайдера, внешний account id,
статус и результат шифрования refresh token: ciphertext, nonce и версию ключа. Открытого token в
модели repository нет. У одного пользователя может быть несколько аккаунтов одного провайдера;
repository возвращает их все, а явный выбор будет правилом service-слоя.

Снимок `connection-api.yaml` получен из `portable-agent/contracts v2.7.0`. Java API и DTO создаются в
`build/`, сгенерированный код не хранится в Git.

## Шифрование token

Refresh token шифруется AES-256-GCM со случайным 96-bit nonce. `tenantId`, `actorId` и provider
передаются как authenticated data: ciphertext из одной строки нельзя расшифровать для другого
владельца. В записи хранится версия ключа, поэтому после ротации старый ключ можно оставить только для
чтения, а новые token шифровать новой версией.

Ключи не имеют значений по умолчанию и не хранятся в Git. Crypto bean создаётся только при
`connection.token-keys.enabled=true`; неверный Base64, ключ не в 32 байта либо отсутствие текущей
версии останавливают запуск.

## OAuth-сессия

Таблица `oauth_sessions` не хранит raw `state`: первичным ключом служит lowercase SHA-256 hash.
PKCE verifier хранится тем же зашифрованным контейнером с nonce и версией ключа. Callback выполняет
один условный `UPDATE ... RETURNING`: запись возвращается только пока `consumed_at IS NULL` и
`expires_at > now`. Это делает state одноразовым и при параллельных запросах к разным инстансам.

Service генерирует `state` и verifier из 32 случайных байт, кодирует Base64 URL без padding и создаёт
PKCE challenge как `BASE64URL(SHA256(verifier))` с методом `S256`. Открытые значения существуют
только на границах start/callback; `toString` result-объектов их скрывает.

## OAuth-провайдеры

`OAuthProvider` — порт для authorization URL, code exchange, refresh и revoke. Реализации собираются
в `OAuthProviders` по enum-ключу, поэтому новый Microsoft/Jira adapter добавляется без цепочки `if`.

Google adapter запрашивает только `openid` и `calendar.events`, использует offline access и PKCE S256.
После обмена code он получает стабильный `sub` из UserInfo. Token/revoke/UserInfo endpoints приходят
из типизированной конфигурации: тесты подменяют их HTTP stub, production использует официальные URL.
Секреты и token не входят в `toString`, а provider-ошибки не переносят response body наружу.
