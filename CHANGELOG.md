# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
проект придерживается [SemVer](https://semver.org/) (до 1.0 обратная совместимость не гарантируется).

## [0.1.0] - 2026-07-28

### Added

- Каркас проекта: Gradle (Kotlin DSL, version catalog, wrapper), toolchain Java 25, CI (GitHub Actions, ubuntu/macos, Temurin 25), Spotless (palantir-java-format), JaCoCo.
- `Key`, `Loc`, `SyncPolicy`, `StoreConfig`, интерфейс `KeyValueStore` (FR-1, FR-2).
- Кодек формата записи (`kvibe.format`): заголовок файла и запись с CRC32C, big-endian (FR-5, FR-9, раздел 6).
- ADR-0001 (модель Bitcask) и ADR-0002 (формат записи, CRC32C, порядок байтов).
- `KvibeStore`: put/get/delete в один поток, открытие/создание файла, восстановление индекса при открытии (FR-1, FR-3, FR-4, FR-6, FR-9).
- `kvibe.recovery.Recovery`: последовательное восстановление keydir, усечение хвоста после торн-записи или порчи (FR-3, FR-5).
- Property-тест с оракулом (jqwik, TR-2): put/get/delete/reopen сверяются с `HashMap`.
- `FileLock` при открытии: повторное открытие занятого файла отклоняется `StoreAlreadyOpenException` вместо тихой порчи данных (FR-8).
- Poisoned state: сбой записи отклоняет последующие `put`/`delete` через `IllegalStateException`, чтения продолжают работать (NFR-7).
- ADR-0004 (политика долговечности по умолчанию и poisoned state).
- `KvibeStore` теперь безопасен для конкурентного использования: писатели (`put`/`delete`/`close`) сериализованы одним `ReentrantLock`, читатели (`get`/`size`) остаются без блокировок (5.1-5.3).
- Тест конкурентности TR-5 (`@Tag("slow")`): пул виртуальных потоков, несколько писателей и читателей, 10+ секунд под нагрузкой.
- ADR-0003 (модель конкурентности) и ADR-0005 (отказ от `MappedByteBuffer`/FFM в пользу `FileChannel`) — все 5 ADR, обязательных для 0.1, готовы.
- Краш-тест TR-3 (`@Tag("slow")`): дочерняя JVM (`CrashWriterMain`) непрерывно пишет новые ключи, подтверждая каждую запись в stdout; родитель убивает её `Process.destroyForcibly()` в случайный момент (случайная политика `SyncPolicy`, случайная задержка) и проверяет, что все подтверждённые записи переживают крэш без потери и без порчи. 20 итераций в `slowTest` (CI), задача `crashTestExtended` — 500 итераций локально (DoD, раздел 10).
- Fuzz-тест TR-4 (`kvibe.fuzz.KvibeStoreFuzzTest`): инвертирует случайный бит в случайной позиции файла 300 раз подряд и проверяет, что `open()` либо восстанавливается с усечением, либо кидает внятное `IOException`, никогда не пропуская `BufferUnderflowException`/`NegativeArraySizeException`/`OutOfMemoryError` наружу, и что записи до точки порчи остаются читаемыми.
- `docs/arch-overview.md`: краткий (2-3 минуты чтения) обзор архитектуры кода — три пакета и их роли, разбор термина «кодек», пути данных `put`/`open`, таблица ключевых типов.
- TR-6: задача `jacocoTestCoverageVerification` с порогом 80% строк отдельно для `kvibe.format` и `kvibe.recovery` (фактически 98.8% и 96.2% на момент внедрения), подключена к `check`.
- `docs/format.md`, `docs/concurrency.md`, `docs/testing.md`, `docs/journal.md` (раздел 9 REQUIREMENTS.md) — спецификация формата, модель конкурентности и её ограничения, карта тестовой стратегии по всем пяти уровням TR-1, лабораторный журнал с baseline-замером производительности (NFR-5: ≈19 100 put/с на `NEVER`, ≈120 put/с на `EVERY_WRITE`, ≈650 000 get/с независимо от политики — Apple M1 Pro, APFS, Temurin 25).
- Задача `concurrencyTestExtended`: прогоняет тест конкурентности TR-5 10 минут вместо 10 секунд (DoD, раздел 10), локально, аналогично `crashTestExtended`.
- Javadoc публичного API (`kvibe`: `KeyValueStore`, `KvibeStore`, `Key`, `Loc`, `StoreConfig`, `StoreAlreadyOpenException`) дополнен до нулевых предупреждений `javadoc` — `@param`/`@return`/`@throws` на всех публичных методах и конструкторах, включая описание гарантий потокобезопасности (в `KeyValueStore`/`KvibeStore`, ссылается на `docs/concurrency.md`).
- `README.md` переписан: снят устаревший статус «Этап 0, код не написан», добавлены пример использования, таблица документации, актуальные команды сборки.

### Changed

- Property-тест TR-2 (`KvibeStorePropertyTest`) поднят с 500 до 1000 попыток (DoD, раздел 10).
- TR-8 (REQUIREMENTS.md, раздел 7): потолок быстрого набора `test` поднят с 60 до 120 секунд — рост property-теста до 1000 попыток увеличил быстрый набор до ≈47с, запас в 13с сочли недостаточным для более медленных CI-раннеров.
- TR-5 (REQUIREMENTS.md, раздел 7): явно зафиксирован сплит «10 секунд в `slowTest`/CI, 10 минут — `concurrencyTestExtended` локально для DoD», устраняющий скрытое противоречие между TR-5 (10 секунд) и Definition of Done (10 минут).

### Fixed

- CI: джоб `test` теперь запускает `./gradlew check` вместо `./gradlew test` — до этого `spotlessCheck` (заявленный в разделе 9 REQUIREMENTS.md) и новый порог JaCoCo (TR-6) не проверялись в CI вовсе, только локально.
- Задача `slowTest` в `build.gradle.kts` не наследовала `testClassesDirs`/`classpath` от `test` и была `NO-SOURCE` с Этапа 0 — просто никто не замечал, пока не появился первый тест с тегом `slow`.
