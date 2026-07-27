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
