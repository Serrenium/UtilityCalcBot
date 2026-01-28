package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.MosRu.MosRuCells64058;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static ru.UtilityCalcPk.tariff.TariffMapper.parseDate;

public class ElectricityPlanBuilder {

    public static List<ElectricityPlan> buildPlans(List<MosRuCells64058> rows) {
        Map<Key, List<MosRuCells64058>> groups = rows.stream()
                .collect(Collectors.groupingBy(ElectricityPlanBuilder::keyOf));

        List<ElectricityPlan> result = new ArrayList<>();

        for (Map.Entry<Key, List<MosRuCells64058>> e : groups.entrySet()) {
            Key k = e.getKey();
            List<MosRuCells64058> group = e.getValue();

            ElectricityPlan plan = new ElectricityPlan();
            plan.setType(k.planType);
            plan.setStoveType(k.stoveType);
            plan.setProvider(k.agency);
            plan.setRegion(k.region);
            plan.setStartDate(k.startDate);
            plan.setEndDate(k.endDate);

            for (MosRuCells64058 c : group) {
                ElectricityPlanType planType =
                        ElectricityMappings.mapPlanType(c.getConsumptionTime());
                ElectricityZone zone =
                        ElectricityMappings.mapZone(c.getConsumptionTime(), planType);
                BigDecimal value = c.getTariffValue();

                if (planType == ElectricityPlanType.ONE_TARIFF) {
                    plan.setDayTariff(value); // одно значение
                } else if (planType == ElectricityPlanType.TWO_TARIFF) {
                    if (zone == ElectricityZone.DAY) {
                        plan.setDayTariff(value);
                    } else if (zone == ElectricityZone.NIGHT) {
                        plan.setNightTariff(value);
                    }
                } else if (planType == ElectricityPlanType.MULTI_TARIFF) {
                    if (zone == ElectricityZone.PEAK) {
                        plan.setPeakTariff(value);
                    } else if (zone == ElectricityZone.DAY) {
                        plan.setDayTariff(value);
                    } else if (zone == ElectricityZone.NIGHT) {
                        plan.setNightTariff(value);
                    }
                }
            }

            result.add(plan);
        }

        return result;
    }

    private static Key keyOf(MosRuCells64058 c) {
        return new Key(
                c.getAgency(),
                c.getRegion(),
                parseDate(c.getStartDate()),
                parseDate(c.getEndDate()),
                ElectricityMappings.mapPlanType(c.getConsumptionTime()),
                c.getStoveType(),
                c.getUnitOfMeasure()
        );
    }

    private static class Key {
        private final String agency;
        private final String region;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final ElectricityPlanType planType;
        private final String stoveType;
        private final String unit;

        Key(String agency,
            String region,
            LocalDate startDate,
            LocalDate endDate,
            ElectricityPlanType planType,
            String stoveType,
            String unit) {
            this.agency = agency;
            this.region = region;
            this.startDate = startDate;
            this.endDate = endDate;
            this.planType = planType;
            this.stoveType = stoveType;
            this.unit = unit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return Objects.equals(agency, key.agency)
                    && Objects.equals(region, key.region)
                    && Objects.equals(startDate, key.startDate)
                    && Objects.equals(endDate, key.endDate)
                    && planType == key.planType
                    && Objects.equals(stoveType, key.stoveType)
                    && Objects.equals(unit, key.unit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agency, region, startDate, endDate, planType, stoveType, unit);
        }
    }
}


