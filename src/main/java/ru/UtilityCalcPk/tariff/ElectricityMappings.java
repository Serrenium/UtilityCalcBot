package ru.UtilityCalcPk.tariff;

public class ElectricityMappings {

    public static ElectricityPlanType mapPlanType(String consumptionTime) {
        if (consumptionTime == null) return null;
        String v = consumptionTime.toLowerCase();

        if (v.contains("без дифференциации")) {
            return ElectricityPlanType.ONE_TARIFF;
        }
        if (v.contains("двухтарифный учет")) {
            return ElectricityPlanType.TWO_TARIFF;
        }
        if (v.contains("многотарифный учет")) {
            return ElectricityPlanType.MULTI_TARIFF;
        }
        return null;
    }

    public static ElectricityZone mapZone(String consumptionTime, ElectricityPlanType type) {
        if (consumptionTime == null || type == null) return null;
        String v = consumptionTime.toLowerCase();

        if (type == ElectricityPlanType.ONE_TARIFF) {
            return ElectricityZone.SINGLE;
        }

        if (type == ElectricityPlanType.TWO_TARIFF) {
            if (v.contains("23:00 по 07:00")) {
                return ElectricityZone.NIGHT;
            } else {
                // "с 07:00 по 23:00 ч (двухтарифный учет)"
                return ElectricityZone.DAY;
            }
        }

        // MULTI_TARIFF
        if (v.contains("07:00 по 10:00") || v.contains("17:00 по 21:00")) {
            return ElectricityZone.PEAK;   // пиковый
        }
        if (v.contains("10:00 по 17:00") || v.contains("21:00 по 23:00")) {
            return ElectricityZone.DAY;    // средний
        }
        if (v.contains("23:00 по 07:00")) {
            return ElectricityZone.NIGHT;
        }
        return null;
    }
}
