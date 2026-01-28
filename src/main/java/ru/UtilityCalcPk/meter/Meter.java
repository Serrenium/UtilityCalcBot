package ru.UtilityCalcPk.meter;

import java.math.BigDecimal;

public class Meter {

    private Long chatId;
    private Long flatId;          // если будешь идентифицировать квартиру id
    private String flatName;      // или просто имя квартиры
    private MeterType type;
    private InitialReading initialReading; // начальные показания

    // для электрических
    private String stoveType;     // газовая/электрическая

    // геттеры/сеттеры

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

    public String getFlatName() {
        return flatName;
    }

    public void setFlatName(String flatName) {
        this.flatName = flatName;
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
}
