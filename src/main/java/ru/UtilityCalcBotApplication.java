package ru;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.UtilityCalcPk.tariff.MosRuTariffLoader;
import ru.UtilityCalcPk.tariff.TariffRepository;
import ru.UtilityCalcPk.tariff.TariffService;

public class UtilityCalcBotApplication {
    public static void main(String[] args) {
        try {
            // 1. Репозиторий тарифов
            TariffRepository repo = new TariffRepository();

            // 2. Загрузка тарифов
            System.out.println("Start requesting tariffs to mos.ru");
            MosRuTariffLoader.load(repo);
            System.out.println("Tariff loading complete");

            // 3. Сервис тарифов
            TariffService tariffService = new TariffService(repo);

            // 4. Бот с сервисом
            var bot = new UtilityCalc(tariffService);

            // 5. Регистрация бота
            var botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("UtilityCalcBot started!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
