package ru.UtilityCalcPk.tariff;

public enum ServiceType {
    WATER_COLD,
    WATER_HOT,
    SEWERAGE,
    ELECTRICITY;

    public String displayName() {
        return switch (this) {
            case WATER_COLD -> "Холодная вода";
            case WATER_HOT -> "Горячая вода";
            case SEWERAGE -> "Водоотведение";
            case ELECTRICITY -> "Электроэнергия";
        };
    }
}
