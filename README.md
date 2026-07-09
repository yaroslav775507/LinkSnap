# LinkSnap — URL Shortener на Redis

Учебный pet-проект для практического изучения Redis.  
Идея: не абстрактный «ключ-значение туда-сюда», а прод-подобный сервис, где каждая структура данных Redis используется под свою реальную задачу.

**Postgres** — источник правды (persistent storage).  
**Redis** — всё, что должно быть быстрым: кэш, лимиты, счётчики, топы.

---

## Идея проекта

Сервис сокращения ссылок (как bit.ly):

1. Пользователь отправляет длинный URL → получает короткий код.
2. Переход по короткой ссылке → редирект на оригинальный URL.
3. Система считает клики и отдаёт топ самых популярных ссылок.

Простая бизнес-логика позволяет сосредоточиться именно на Redis.

---

## Какие структуры Redis где используются

| Фича                              | Структура данных          | Ключ                     | Команды                          |
|-----------------------------------|---------------------------|--------------------------|----------------------------------|
| Кэш редиректов (cache-aside)      | String + TTL              | `shortcode:{code}`       | `GET`, `SETEX`                   |
| Rate limiting (10 запросов/мин/IP)| String (fixed window)     | `ratelimit:{ip}`         | `INCR`, `EXPIRE`                 |
| Счётчик кликов (буфер перед БД)   | Hash                      | `clicks:{code}`          | `HINCRBY`, `HGET`, `DEL`         |
| Топ-10 ссылок по кликам           | Sorted Set                | `top:links`              | `ZINCRBY`, `ZREVRANGE`           |
| *(roadmap)* Live-дашборд кликов   | Pub/Sub                   | канал `link-clicks`      | `PUBLISH`, `SUBSCRIBE`           |
| *(roadmap)* Генерация кодов       | Distributed Lock (Redisson)| `lock:code-gen`          | `RLock`                          |

---

## Стек

- **Java 17**
- **Spring Boot 3** (Web, Data JPA, Data Redis)
- **PostgreSQL** — persistent storage
- **Redis 7** — кэш / лимиты / счётчики / топы
- **Lettuce** — Redis-клиент
- **Docker Compose** — окружение (redis + postgres)
- *(roadmap)* **Redisson** — распределённые локи

---

## Структура проекта

```
linksnap/
├── docker-compose.yml
├── pom.xml
├── src/main/resources/
│   └── application.yml
└── src/main/java/com/example/linksnap/
    ├── LinksnapApplication.java
    ├── config/
    │   └── RedisConfig.java
    ├── entity/
    │   └── Link.java
    ├── repository/
    │   └── LinkRepository.java
    ├── service/
    │   ├── LinkService.java
    │   ├── RateLimitService.java
    │   └── ClickCountService.java
    ├── filter/
    │   └── RateLimitFilter.java
    └── controller/
        └── LinkController.java
```

---

## Схема БД

```sql
CREATE TABLE links (
    id           BIGSERIAL PRIMARY KEY,
    short_code   VARCHAR(16) NOT NULL UNIQUE,
    original_url VARCHAR(2048) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    click_count  BIGINT NOT NULL DEFAULT 0
);
```

---

## Запуск

```bash
# 1. Поднять инфраструктуру
docker compose up -d        # redis:6379, postgres:5432

# 2. Запустить приложение
mvn spring-boot:run
```

Проверить Redis:

```bash
docker exec -it <redis-container> redis-cli PING
# PONG
```

---

## API

| Метод | Путь                | Описание                          |
|-------|---------------------|-----------------------------------|
| POST  | `/api/links`        | Создать короткую ссылку           |
| GET   | `/r/{code}`         | Редирект на оригинальный URL      |
| GET   | `/api/links/top`    | Топ-10 ссылок по кликам           |

---

## Примеры запросов

```bash
# Создать короткую ссылку
curl -X POST localhost:8080/api/links \
     -H "Content-Type: application/json" \
     -d '{"url":"https://github.com"}'
# → {"shortCode":"a1B2c3","url":"https://github.com"}

# Перейти по ссылке (302 редирект)
curl -L localhost:8080/r/a1B2c3

# Посмотреть данные в Redis
docker exec -it <redis-container> redis-cli
> GET shortcode:a1B2c3
> HGETALL clicks:a1B2c3
> ZREVRANGE top:links 0 9 WITHSCORES

# Получить топ
curl localhost:8080/api/links/top

# Проверить rate limit (11-й запрос → 429)
for i in {1..11}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/links \
       -H "Content-Type: application/json" -d '{"url":"https://test.com"}'
done
```
