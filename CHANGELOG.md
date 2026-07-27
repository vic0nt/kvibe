# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
проект придерживается [SemVer](https://semver.org/) (до 1.0 обратная совместимость не гарантируется).

## [Unreleased]

### Added

- Каркас проекта: Gradle (Kotlin DSL, version catalog, wrapper), toolchain Java 25, CI (GitHub Actions, ubuntu/macos, Temurin 25), Spotless (palantir-java-format), JaCoCo.
- `Key`, `Loc`, `SyncPolicy`, `StoreConfig`, интерфейс `KeyValueStore` (FR-1, FR-2).
- Кодек формата записи (`kvibe.format`): заголовок файла и запись с CRC32C, big-endian (FR-5, FR-9, раздел 6).
- ADR-0001 (модель Bitcask) и ADR-0002 (формат записи, CRC32C, порядок байтов).
