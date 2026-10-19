package ru.skillfactory.weather;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class WeatherApp {
    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Запуск: java -jar target/weather-1.0.0.jar [lat lon limit]");
            System.out.println("По умолчанию: 55.75 37.62 3. limit: 1–10 суток, включая текущие.");
            System.out.println("Ключ: YANDEX_WEATHER_KEY в окружении или файле .env.");
            return;
        }
        try {
            if (args.length != 0 && args.length != 3) {
                throw new IllegalArgumentException("Укажите три аргумента: lat lon limit, либо не указывайте аргументы.");
            }
            double lat = args.length == 0 ? 55.75 : Double.parseDouble(args[0]);
            double lon = args.length == 0 ? 37.62 : Double.parseDouble(args[1]);
            int limit = args.length == 0 ? 3 : Integer.parseInt(args[2]);
            var request = createRequest(lat, lon, limit, loadApiKey());
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                System.out.println(response.body());
                throw new IOException("Яндекс Погода вернул HTTP " + response.statusCode() + ". "
                        + switch (response.statusCode()) {
                            case 401, 403 -> "Проверьте ключ, его активацию и доступ к /v2/forecast.";
                            case 429 -> "Исчерпана квота или превышена частота запросов.";
                            default -> "Проверьте ответ сервиса выше.";
                        });
            }
            printWeather(response.body(), limit, System.out);
        } catch (NumberFormatException e) {
            System.err.println("Ошибка: lat и lon должны быть числами с точкой, limit — целым числом.");
            System.exit(1);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Ошибка: запрос прерван.");
            System.exit(1);
        }
    }

    static HttpRequest createRequest(double lat, double lon, int limit, String apiKey) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Широта lat должна быть в диапазоне от -90 до 90.");
        }
        if (!Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Долгота lon должна быть в диапазоне от -180 до 180.");
        }
        if (limit < 1 || limit > 10) {
            throw new IllegalArgumentException("limit должен быть от 1 до 10; доступный горизонт зависит от тарифа.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Задайте YANDEX_WEATHER_KEY в окружении или файле .env.");
        }
        String key = apiKey.strip();
        if (key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Ключ API содержит недопустимые символы.");
        }
        var uri = URI.create("https://api.weather.yandex.ru/v2/forecast"
                + "?lat=" + lat + "&lon=" + lon + "&limit=" + limit);
        return HttpRequest.newBuilder(uri)
                .header("X-Yandex-Weather-Key", key)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
    }

    static void printWeather(String json, int limit, PrintStream output) {
        // Print the original response before parsing; never reserialize or truncate it.
        output.println(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Сервис вернул некорректный JSON.", e);
        }
        BigDecimal current = numberAt(root, "fact", "temp");
        output.println("Текущая температура (fact.temp): " + current.toPlainString() + " °C");

        JsonElement forecasts = root.getAsJsonObject().get("forecasts");
        if (forecasts == null || !forecasts.isJsonArray() || forecasts.getAsJsonArray().size() < limit) {
            throw new IllegalArgumentException("В ответе нет прогноза на запрошенные " + limit + " суток.");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < limit; i++) {
            try {
                sum = sum.add(numberAt(forecasts.getAsJsonArray().get(i), "parts", "day", "temp_avg"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Нет числового forecasts[" + i
                        + "].parts.day.temp_avg. Среднее за " + limit + " суток вычислить нельзя.", e);
            }
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP);
        output.println("Средняя дневная температура за " + limit + " суток: " + average.toPlainString() + " °C");
    }

    private static BigDecimal numberAt(JsonElement element, String... path) {
        for (String field : path) {
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("Отсутствует числовое поле " + String.join(".", path) + ".");
            }
            element = element.getAsJsonObject().get(field);
        }
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Отсутствует числовое поле " + String.join(".", path) + ".");
        }
        return element.getAsBigDecimal();
    }

    private static String loadApiKey() throws IOException {
        String key = System.getenv("YANDEX_WEATHER_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        var properties = new Properties();
        var file = Path.of(".env");
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties.getProperty("YANDEX_WEATHER_KEY", "");
    }
}
