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

Controller отвечает только за HTTP и DTO. Service проверяет правила и управляет сценарием.
Repository содержит только jOOQ-запросы. Provider client общается с Google либо тестовым OAuth stub.
Сетевой обмен code с provider не помещается в транзакцию БД: upsert подключения выполняется одним
атомарным SQL-запросом.

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

## Подключение аккаунта

`ConnectionService.start` сначала получает provider из registry и только затем создаёт OAuth-сессию,
чтобы не оставлять бесполезную сессию для выключенного provider. Результат содержит URL и срок жизни,
но `toString` скрывает URL вместе со state.

`ConnectionService.complete` потребляет одноразовый state, получает проверенного владельца и PKCE
verifier, обменивает code и шифрует только refresh token. Repository делает `INSERT ... ON CONFLICT DO
UPDATE` по владельцу, provider и внешнему account id. При reconnect сохраняются прежние `id` и
`created_at`, а token, status и `updated_at` заменяются. Если шифрование или upsert не удались после
обмена, refresh token отзывается как компенсация. Отказ пользователя проходит через отдельный
`reject` и потребляет state без расшифровки verifier.

## Список и отключение

Список читается только по паре `tenant_id + actor_id`; controller не сможет запросить данные без
обоих значений из проверенного JWT. Отключение также ищет и меняет запись только в границах владельца,
поэтому чужой и отсутствующий id дают одинаковый результат.

Операция отключения сначала ставит `DISCONNECTED`. После этого локальная выдача access token уже
невозможна. Затем сервис расшифровывает refresh token, вызывает revoke выбранной provider-стратегии и
удаляет запись. Если decrypt или revoke завершились ошибкой, зашифрованные credentials остаются только
для повторного DELETE, но статус уже не разрешает их использовать. Условные UPDATE и DELETE также
сверяют сохранённые ciphertext, nonce и версию ключа: старый запрос отключения не может изменить или
удалить подключение, которое пользователь успел обновить через reconnect.

## HTTP и JWT

Controller реализует сгенерированный `ConnectionsApi`, преобразует только HTTP DTO и вызывает один
метод service на endpoint. Start, list и disconnect доступны только с bearer JWT. Валидаторы требуют
правильные подпись, issuer, audience `connection-service`, UUID `sub` и UUID `tenant_id`; эти claims
становятся единственным источником владельца. Callback открыт, потому что его вызывает OAuth
provider, а владельца восстанавливает одноразовый state из БД.

Callback принимает ровно одно из `code` или `error`. Это правило находится в service. Отказ provider
погашает state без расшифровки PKCE verifier. HTML результата статичен и не содержит входных данных,
а start и callback возвращают `Cache-Control: no-store`. Ошибки отображаются в Problem Details без
provider body, token, code или state.

## Внутренняя выдача access token

`POST /internal/v1/tokens` доступен только service JWT с audience `connection-service`, authority
`SCOPE_connection:token` и claim `azp`, который входит в конфигурируемый allowlist. Tenant всегда
берётся из JWT, а actor id приходит от доверенного Action Service в теле запроса.

`TokenService` запрашивает все активные подключения actor/provider. Ноль записей означает, что нужно
подключение; больше одной требует явного выбора в будущем контракте. При ровно одной записи refresh
token расшифровывается внутри сервиса и передаётся provider-стратегии. Наружу выходит только access
token со сроком жизни и `Cache-Control: no-store`; его `toString` всегда редактирует секрет.
