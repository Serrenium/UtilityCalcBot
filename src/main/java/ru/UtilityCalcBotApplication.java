package ru;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class UtilityCalcBotApplication {
    public static void main(String[] args) {
        try {
            var bot = new UtilityCalc();
            var botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("UtilityCalcBot запущен!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}