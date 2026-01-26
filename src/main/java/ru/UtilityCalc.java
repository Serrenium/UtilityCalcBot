package ru;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class UtilityCalc extends TelegramLongPollingBot {
    @Override
    public String getBotUsername() {
        return "Utility_calc_bot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId);

            if (text.equals("/calc")) {
                msg.setText("Введите: горячая вода(м3), холодная вода(м3), свет(кВтч)\nПример: 50,10,200");
            } else if (text.matches("\\d+[,]\\d+[,]\\d+")) {
                String[] parts = text.split(",");
                double area = Double.parseDouble(parts[0]);
                double water = Double.parseDouble(parts[1]);
                double power = Double.parseDouble(parts[2]);
                double total = area * 50 + water * 40 + power * 5.5; // Пример тарифов Москва
                msg.setText(String.format("Итого: %.2f ₽", total));
            } else {
                msg.setText("Команды: /calc - расчёт ЖКУ");
            }
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
