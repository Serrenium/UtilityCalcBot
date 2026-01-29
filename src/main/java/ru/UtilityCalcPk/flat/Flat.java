package ru.UtilityCalcPk.flat;

public class Flat {

    private Long chatId;
    private Long flatId;
    private String name;          // "Квартира на Пушкина"

    // геттеры/сеттеры

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return flatId;
    }

    public void setId(Long flatId) {
        this.flatId = flatId;
    }
}
