package ru.UtilityCalcPk.meter;

public class Meter {

    private Long id;
    private Long chatId;
    private Long flatId;          // если будешь идентифицировать квартиру id
    private MeterType type;
    private String providerShort; // Мосэнергосбыт, Мосводоканал, МОЭК и т.п.
    private String stoveType;     // только для электрических счётчиков (газовая/электроплита)
    private InitialReading initialReading; // начальные показания

    // геттеры/сеттеры

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getFlatId() {
        return flatId;
    }

    public void setFlatId(Long flatId) {
        this.flatId = flatId;
    }

    public MeterType getType() {
        return type;
    }

    public void setType(MeterType type) {
        this.type = type;
    }

    public InitialReading getInitialReading() {
        return initialReading;
    }

    public void setInitialReading(InitialReading initialReading) {
        this.initialReading = initialReading;
    }

    public String getStoveType() {
        return stoveType;
    }

    public void setStoveType(String stoveType) {
        this.stoveType = stoveType;
    }

    public String getProviderShort() {
        return providerShort;
    }

    public void setProviderShort(String providerShort) {
        this.providerShort = providerShort;
    }
}
