# KVibe

[![CI](https://github.com/vic0nt/kvibe/actions/workflows/ci.yml/badge.svg)](https://github.com/vic0nt/kvibe/actions/workflows/ci.yml)

**Учебный проект.** Встраиваемое (in-process) key-value хранилище на модели Bitcask. Это модель хранилища, основанная на принципе append-only log + хэш-индекс в памяти. Все записи последовательно дописываются в файлы на диске, а в оперативной памяти хранится только индекс, указывающий, где лежит последняя версия каждого ключа. Благодаря этому операции записи и чтения выполняются очень быстро. Разработана в компании Basho для базы данных Riak в 2010 году.

Цель проекта — не столько получить готовую библиотеку, сколько разобраться
на практике с долговечностью записи и обосновать модель конкурентности. Подробности и полная
спецификация — в [REQUIREMENTS.md](REQUIREMENTS.md).

Цели кратко:

1. Разобраться на практике, что такое долговечность записи (`write` ≠ «на диске») и как это тестировать.
2. Спроектировать и обосновать модель конкурентности, а не «навесить `synchronized` и молиться».
3. Отработать дисциплину: property-based тесты, краш-тесты, ADR, документация.
4. Сдать домашку по вайб-кодингу 😁

## Статус

Все этапы 0.1 (0-7) реализованы. См. `CHANGELOG.md` и [Definition of Done](REQUIREMENTS.md#10-definition-of-done-для-01).

## Пример использования

```java
try (KvibeStore store = KvibeStore.open(Path.of("data.kvibe"), StoreConfig.defaults())) {
    store.put("hello".getBytes(UTF_8), "world".getBytes(UTF_8));
    byte[] value = store.get("hello".getBytes(UTF_8)); // "world"
    store.delete("hello".getBytes(UTF_8));
}
```

Один писатель (сериализован внутри), неограниченное число читателей без блокировок. Подробности
модели конкурентности — [docs/concurrency.md](docs/concurrency.md), формат файла —
[docs/format.md](docs/format.md).

## Документация

| Документ | Назначение |
|---|---|
| [REQUIREMENTS.md](REQUIREMENTS.md) | Полная спецификация: функциональные/нефункциональные требования, DoD |
| [docs/arch-overview.md](docs/arch-overview.md) | Краткая (2-3 мин) карта кода: пакеты, термины, пути данных |
| [docs/format.md](docs/format.md) | Формат файла данных |
| [docs/concurrency.md](docs/concurrency.md) | Модель конкурентности, инварианты, известные ограничения |
| [docs/testing.md](docs/testing.md) | Стратегия тестирования — что каким уровнем проверяется |
| [docs/journal.md](docs/journal.md) | Лабораторный журнал: замеры, находки, объяснения |
| [docs/adr/](docs/adr/) | Архитектурные решения (MADR) |

## Стек

- Java 25 (Gradle toolchain)
- Gradle 9
- JUnit 5, jqwik, AssertJ
- Spotless (palantir-java-format), JaCoCo

## Сборка

```
./gradlew check          # быстрый набор тестов + Spotless + порог покрытия JaCoCo
./gradlew slowTest        # краш-тест и тест конкурентности (тег slow)
```

## CI

GitHub Actions, матрица `ubuntu-latest` + `macos-latest`, Temurin 25:

- На каждый push/PR — джоб `test`: `./gradlew check` (быстрый набор тестов, `spotlessCheck`,
  порог покрытия JaCoCo TR-6). Укладывается в 120 секунд (TR-8).
- По расписанию (еженедельно, `cron: '0 3 * * 1'`) — джоб `slow-test`: `./gradlew slowTest`
  (краш-тест TR-3 и тест конкурентности TR-5, тег `slow`). Не гоняется на каждый push
  намеренно: оба теста чувствительны к скорости диска/файловой системы конкретного раннера
  (риск R-2, раздел 12 REQUIREMENTS.md).

Расширенные локальные прогоны для Definition of Done (500 циклов краш-теста, 10 минут теста
конкурентности) в CI не участвуют — `crashTestExtended` и `concurrencyTestExtended` слишком
медленные для регулярного запуска, см. [docs/testing.md](docs/testing.md).

## Лицензия

MIT, см. [LICENSE](LICENSE).
