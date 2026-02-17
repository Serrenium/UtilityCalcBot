//package ru;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.UtilityCalc;
import ru.UtilityCalcPk.flat.FlatRepository;
import ru.UtilityCalcPk.meter.MeterRepository;
import ru.UtilityCalcPk.tariff.MosRuTariffLoader;
import ru.UtilityCalcPk.tariff.TariffRepository;
import ru.UtilityCalcPk.tariff.TariffService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Тестовый класс для запуска Telegram-бота локально.
 * Устанавливает переменные окружения и запускает бота.
 */
public class BotTestMain {
    // тестовые данные
    private static final String BOT_TOKEN = "8416190970:AAG2SEo65mlzUOXepBdq7e13gZeGRfOoL34";
    private static final String BOT_USERNAME = "test_utility_calc_bot";
    // ===========================================

    public static void main(String[] args) {
        // Устанавливаем переменные окружения
//        setEnv("TELEGRAM_BOT_TOKEN", BOT_TOKEN);
//        setEnv("TELEGRAM_BOT_USERNAME", BOT_USERNAME);

        // Проверяем, что бот может стартовать
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String username = System.getenv("TELEGRAM_BOT_USERNAME");

        if (token == null || token.isEmpty()) {
            System.err.println("Ошибка: TELEGRAM_BOT_TOKEN не установлен!");
            return;
        }
        if (username == null || username.isEmpty()) {
            System.err.println("Ошибка: TELEGRAM_BOT_USERNAME не установлен!");
            return;
        }

        System.out.println("Запуск бота: " + username);

        try {
            // Инициализация API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Создаём бота (предполагается, что у него есть конструктор без аргументов
            // или вы можете передать зависимости — здесь упрощённый вариант)
            FlatRepository flatRepository = new FlatRepository(); // если есть конструктор
            MeterRepository meterRepository = new MeterRepository();
            TariffRepository repo = new TariffRepository();
//            MosRuTariffLoader.load(repo);
            TariffService tariffService = new TariffService(repo);
            tariffService.ensureTariffsUpToDate();

            UtilityCalc bot = new UtilityCalc(tariffService, flatRepository, meterRepository);

            // Регистрируем бота
            botsApi.registerBot(bot);

            System.out.println("✅ Бот успешно запущен и ожидает сообщения...");

        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка при запуске бота:");
            e.printStackTrace();
        }
    }

    /**
     * Утилита для установки переменных окружения через рефлексию.
     * Работает на OpenJDK / Oracle JDK 8–17. Не используйте в продакшене.
     */
    @SuppressWarnings("unchecked")
    private static void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> clazz = env.getClass();
            Field field = clazz.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> map = (Map<String, String>) field.get(env);
            map.put(key, value);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить переменную окружения: " + key, e);
        }
    }
}