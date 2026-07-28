# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
проект придерживается [SemVer](https://semver.org/) (до 1.0 обратная совместимость не гарантируется).

## [Unreleased]

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

### Fixed

- Задача `slowTest` в `build.gradle.kts` не наследовала `testClassesDirs`/`classpath` от `test` и была `NO-SOURCE` с Этапа 0 — просто никто не замечал, пока не появился первый тест с тегом `slow`.
