package ru.UtilityCalcPk.tariff;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ElectricityPlan {

    private ElectricityPlanType type;     // одно/двух/многотарифный
    private String stoveType;   // газовая или электрическая плита

    private String provider;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;

    // 1..3 тарифа по зонам
    private BigDecimal dayTariff;   // или zone1
    private BigDecimal nightTariff; // или zone2
    private BigDecimal peakTariff;  // или zone3

    // геттеры/сеттеры

    public ElectricityPlanType getType() {
        return type;
    }

    public void setType(ElectricityPlanType type) {
        this.type = type;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getDayTariff() {
        return dayTariff;
    }

    public void setDayTariff(BigDecimal dayTariff) {
        this.dayTariff = dayTariff;
    }

    public BigDecimal getNightTariff() {
        return nightTariff;
    }

    public void setNightTariff(BigDecimal nightTariff) {
        this.nightTariff = nightTariff;
    }

    public BigDecimal getPeakTariff() {
        return peakTariff;
    }

    public void setPeakTariff(BigDecimal peakTariff) {
        this.peakTariff = peakTariff;
    }

    public String getStoveType() {
        return stoveType;
    }

    public void setStoveType(String stoveType) {
        this.stoveType = stoveType;
    }
}