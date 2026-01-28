package ru.UtilityCalcPk.flat;

public class Flat {

    private Long chatId;
    private Long flatId;
    private String name;          // "Квартира на Пушкина"
    private String providerShort; // Мосводоканал/Мосэнергосбыт и т.п.
    private String stoveType;     // газовая/электрическая

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

    public String getProviderShort() {
        return providerShort;
    }

    public void setProviderShort(String providerShort) {
        this.providerShort = providerShort;
    }

    public String getStoveType() {
        return stoveType;
    }

    public void setStoveType(String stoveType) {
        this.stoveType = stoveType;
    }

    public Long getId() {
        return flatId;
    }

    public void setFlatId(Long flatId) {
        this.flatId = flatId;
    }
}
