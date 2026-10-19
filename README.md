# Яндекс Погода на Java

Приложение делает GET-запрос к API Яндекс Погоды, выводит полный JSON, текущую температуру `fact.temp` и среднее `parts.day.temp_avg` за `limit` суток.

Требуются Java 17 или новее и Maven.

В IntelliJ IDEA выберите File → Open → корневой `pom.xml` → Open as Project.

## Ключ API

Скопируйте `.env.example` в `.env` и укажите свой ключ:

```properties
YANDEX_WEATHER_KEY=ваш_ключ
```

Не публикуйте `.env` с ключом.

## Сборка и запуск

Выполните команды из корня проекта:

```bash
mvn clean package
java -jar target/weather-1.0.0.jar
```

По умолчанию: Москва, `lat=55.75`, `lon=37.62`, `limit=3`.

Другие широта, долгота и число суток передаются аргументами `lat lon limit`:
```bash
java -jar target/weather-1.0.0.jar 59.93 30.32 5
```

Условия доступа: [официальные тарифы API](https://yandex.ru/dev/weather/doc/ru/concepts/pricing).
