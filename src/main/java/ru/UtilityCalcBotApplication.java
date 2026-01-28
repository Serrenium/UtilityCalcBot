package ru;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.UtilityCalcPk.tariff.TariffRepository;
import ru.UtilityCalcPk.tariff.TariffService;

public class UtilityCalcBotApplication {
    public static void main(String[] args) {
        try {
            // 1. Репозиторий тарифов
            TariffRepository repo = new TariffRepository();

            // 2. (позже) загрузка тарифов в repo
            // MosRuTariffLoader.load(repo);  // здесь будет твой код загрузки

            // 3. Сервис тарифов
            TariffService tariffService = new TariffService(repo);

            // 4. Бот с сервисом
            var bot = new UtilityCalc(tariffService);

            // 5. Регистрация бота
            var botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("UtilityCalcBot запущен!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
