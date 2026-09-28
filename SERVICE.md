# Карточка сервиса

Этот файл — короткая память для людей и AI-агентов. Обновляй его при изменении границ сервиса.

| Поле | Значение |
|---|---|
| Ответственность | Подключения внешних OAuth-аккаунтов и короткоживущие access token |
| Владелец | `portable-agent/backend` |
| Язык | Java 25, Spring Boot 4 |
| Входящие контракты | `portable-agent/contracts` v2.7.0: public OAuth API и internal token API |
| Исходящие контракты | OAuth token endpoints провайдеров |
| Свои данные | Зашифрованные refresh token и метаданные подключения |
| Не отвечает за | Команды, подтверждения, выбор MCP tool, вызов Calendar API |
| SLO | Не определён до бизнес-обсуждения |

## Текущее состояние

Есть схема `account_connections`, доменная модель и jOOQ repository. Сервис сохраняет только уже
зашифрованные token bytes, nonce и версию ключа; API для открытого refresh token в repository нет.
Поиск всегда ограничен `tenantId` и `actorId`, а несколько активных аккаунтов возвращаются списком без
скрытого выбора. AES-256-GCM шифратор привязывает ciphertext к владельцу и провайдеру через AAD,
поддерживает несколько версий ключа для ротации и включается только с внешней конфигурацией. OAuth
endpoints и provider clients ещё не реализованы.

Одноразовые OAuth-сессии хранят только SHA-256 hash от `state` и зашифрованный PKCE verifier. Метод
`consume` атомарно принимает только неистёкшую и ещё не использованную сессию, поэтому два callback
не могут обменять один authorization code повторно.

`OAuthSessionService` создаёт 256-bit random `state` и PKCE verifier, строит challenge методом S256,
сохраняет только hash/encrypted значения и завершает flow через атомарный `consume`. TTL задаётся
типизированным `connection.oauth.session-ttl` и сейчас по умолчанию равен 10 минутам.

Provider-слой использует стратегии из map, а не условные `if`. Реализован Google OAuth adapter:
authorization code + PKCE, offline refresh token, refresh, revoke и получение стабильного `sub` через
OpenID UserInfo. Adapter выключен по умолчанию и не создаётся без явной внешней конфигурации.

Интеграционные тесты запускают Spring Boot и repository с PostgreSQL в Testcontainers, проверяют
Flyway, jOOQ, tenant isolation, health и закрытый доступ к метрикам. Без Docker тесты завершаются
ошибкой, а не пропускаются.
