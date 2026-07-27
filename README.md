# KVibe

Учебное встраиваемое (in-process) key-value хранилище на модели Bitcask
(индекс в памяти + лог-структурированный файл на диске).

Цель проекта — не столько получить готовую библиотеку, сколько разобраться
на практике с долговечностью записи и обосновать модель конкурентности. Подробности и полная спецификация — в [REQUIREMENTS.md](REQUIREMENTS.md).

## Статус

Проект в самом начале. Готов Этап 0: Gradle-каркас, CI.
Код хранилища ещё не написан.

## Стек

- Java 25 (Gradle toolchain)
- Gradle 9
- JUnit 5, jqwik, AssertJ
- Spotless (palantir-java-format), JaCoCo

## Сборка

```
./gradlew test
```

## Лицензия

MIT, см. [LICENSE](LICENSE).
