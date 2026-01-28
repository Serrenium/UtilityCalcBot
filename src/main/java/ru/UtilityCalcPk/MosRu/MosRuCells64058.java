package ru.UtilityCalcPk.MosRu;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class MosRuCells64058 {

    @JsonProperty("Region")
    private String region;

    @JsonProperty("TariffItem")
    private String tariffItem;

    @JsonProperty("UnitOfMeasure")
    private String unitOfMeasure;

    @JsonProperty("StartDate")
    private String startDate;

    @JsonProperty("EndDate")
    private String endDate;

    @JsonProperty("MeasureDeviceAvail")
    private String measureDeviceAvail;

    @JsonProperty("StoveType")
    private String stoveType;

    @JsonProperty("ConsumptionTime")
    private String consumptionTime;

    @JsonProperty("ConsumptionTarget")
    private String consumptionTarget;

    @JsonProperty("Agency")
    private String agency;

    @JsonProperty("TariffValue")
    private BigDecimal tariffValue;

    @JsonProperty("global_id")
    private long globalId;

    // геттеры/сеттеры под имена region, tariffItem, unitOfMeasure и т.д.

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getTariffItem() {
        return tariffItem;
    }

    public void setTariffItem(String tariffItem) {
        this.tariffItem = tariffItem;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getMeasureDeviceAvail() {
        return measureDeviceAvail;
    }

    public void setMeasureDeviceAvail(String measureDeviceAvail) {
        this.measureDeviceAvail = measureDeviceAvail;
    }

    public String getStoveType() {
        return stoveType;
    }

    public void setStoveType(String stoveType) {
        this.stoveType = stoveType;
    }

    public String getConsumptionTime() {
        return consumptionTime;
    }

    public void setConsumptionTime(String consumptionTime) {
        this.consumptionTime = consumptionTime;
    }

    public String getConsumptionTarget() {
        return consumptionTarget;
    }

    public void setConsumptionTarget(String consumptionTarget) {
        this.consumptionTarget = consumptionTarget;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public BigDecimal getTariffValue() {
        return tariffValue;
    }

    public void setTariffValue(BigDecimal tariffValue) {
        this.tariffValue = tariffValue;
    }

    public long getGlobalId() {
        return globalId;
    }

    public void setGlobalId(long globalId) {
        this.globalId = globalId;
    }
}
