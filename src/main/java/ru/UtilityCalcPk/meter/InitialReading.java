package ru.UtilityCalcPk.meter;

import java.math.BigDecimal;

public class InitialReading {

    private BigDecimal total;   // для воды и однотарифного

    private BigDecimal day;     // для двух- и трёхтарифного
    private BigDecimal night;
    private BigDecimal peak;

    // геттеры/сеттеры

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getDay() {
        return day;
    }

    public void setDay(BigDecimal day) {
        this.day = day;
    }

    public BigDecimal getNight() {
        return night;
    }

    public void setNight(BigDecimal night) {
        this.night = night;
    }

    public BigDecimal getPeak() {
        return peak;
    }

    public void setPeak(BigDecimal peak) {
        this.peak = peak;
    }
}
