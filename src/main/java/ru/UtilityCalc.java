package ru;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.UtilityCalcPk.flat.Flat;
import ru.UtilityCalcPk.flat.FlatRepository;
import ru.UtilityCalcPk.tariff.TariffService;

public class UtilityCalc extends TelegramLongPollingBot {

    private final TariffService tariffService;
    private final FlatRepository flatRepository;

    public UtilityCalc(TariffService tariffService, FlatRepository flatRepository) {
        this.tariffService = tariffService;
        this.flatRepository = flatRepository;
    }

    @Override
    public String getBotUsername() {
        return System.getenv("TELEGRAM_BOT_USERNAME"); //"Utility_calc_bot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText();
        String chatId = update.getMessage().getChatId().toString();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);

        if (text.equals("/tariffs")) {
            // новая команда: показать текущие тарифы
            String tariffsText = tariffService.formatTodayTariffsForBot();
            msg.setText(tariffsText);
        }
        else
        {
            msg.setText("Команды: /tariffs - актуальные тарифы");
        }

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}