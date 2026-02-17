package ru;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.UtilityCalcPk.flat.FlatRepository;
import ru.UtilityCalcPk.meter.MeterRepository;
import ru.UtilityCalcPk.tariff.TariffRepository;
import ru.UtilityCalcPk.tariff.TariffService;

import java.io.IOException;

public class UtilityCalcBotApplication {
    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            // 1. Репозиторий тарифов
            TariffRepository repo = new TariffRepository();

            // 2. Загрузка тарифов
//            System.out.println("Start requesting tariffs to mos.ru");
//            MosRuTariffLoader.load(repo);
//            System.out.println("Tariff loading complete");

            // 3. Сервис тарифов
            TariffService tariffService = new TariffService(repo);
            tariffService.ensureTariffsUpToDate(); // проверим атуальные ли тарифы загружены в бд

            // Репозиторий квартир
            FlatRepository flatRepository = new FlatRepository();
            // Репозиторий счетчиков
            MeterRepository meterRepository = new MeterRepository();

            // 4. Бот с сервисами
            var bot = new UtilityCalc(tariffService, flatRepository, meterRepository);

            // 5. Регистрация бота
            var botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("UtilityCalcBot started!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
