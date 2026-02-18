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

    public static void main(String[] args) {

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
}