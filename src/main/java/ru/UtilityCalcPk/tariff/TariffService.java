package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.flat.Flat;
import ru.UtilityCalcPk.meter.Meter;
import ru.UtilityCalcPk.meter.MeterType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TariffService {

    private final TariffRepository repository;

    public TariffService(TariffRepository repository) {
        this.repository = repository;
    }

    public String formatTodayTariffsForBot() {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("Актуальные тарифы ЖКУ (mos.ru):\n\n");

        // сгруппируем по услуге
        List<Tariff> active = repository.findActiveByDate(today);

        for (ServiceType serviceType : List.of(ServiceType.WATER_COLD, ServiceType.WATER_HOT, ServiceType.SEWERAGE)) {
            List<Tariff> list = active.stream()
                    .filter(t -> t.getService() == serviceType)
                    .toList();
            if (list.isEmpty()) continue;

            sb.append(serviceType.displayName()).append(":\n");
            for (Tariff t : list) {
                sb.append("• ")
                        .append(t.getValue()).append(" ").append(t.getUnit())
                        .append(" (").append(t.getProviderShort()).append(")\n");
            }
            sb.append("\n");
        }

        // электро отдельно
        formatElectricity(sb, today);

        return sb.toString();
    }

    private String humanPlanType(ElectricityPlanType type) {
        return switch (type) {
            case ONE_TARIFF -> "Однотарифный";
            case TWO_TARIFF -> "Двухтарифный";
            case MULTI_TARIFF -> "Многотарифный";
        };
    }
    private String humanStove(String stoveType) {
        if (stoveType == null) return "плита: не указана";
        String s = stoveType.toLowerCase();
        if (s.contains("газ")) return "Газовая плита";
        if (s.contains("электр")) return "Электроплита";
        return stoveType;
    }
    private void formatElectricity(StringBuilder sb, LocalDate date) {
        List<ElectricityPlan> plans = repository.findActiveElectricityPlans(date);
        if (plans.isEmpty()) return;

        sb.append("Электроэнергия ");

        // сгруппуем по поставщику
        Map<String, List<ElectricityPlan>> byProvider = plans.stream()
                .collect(Collectors.groupingBy(ElectricityPlan::getProviderShort));

        for (var entry : byProvider.entrySet()) {
            String providerShort = entry.getKey();
            List<ElectricityPlan> providerPlans = entry.getValue();

            sb.append("(").append(providerShort).append("):\n");

            // внутри — по типу плиты
            Map<String, List<ElectricityPlan>> byStove = providerPlans.stream()
                    .collect(Collectors.groupingBy(ElectricityPlan::getStoveType));

            for (var e2 : byStove.entrySet()) {
                String stove = e2.getKey();
                List<ElectricityPlan> stovePlans = e2.getValue();

                sb.append("\n").append("  ").append(humanStove(stove)).append(":\n");

                List<ElectricityPlan> sorted = new ArrayList<>(stovePlans);
                sorted.sort(Comparator.comparingInt(p -> switch (p.getType()) {
                    case ONE_TARIFF -> 1;
                    case TWO_TARIFF -> 2;
                    case MULTI_TARIFF -> 3;
                }));

                for (ElectricityPlan p : sorted) {
                    sb.append("   • ")
                            .append(humanPlanType(p.getType())).append(": ");

                    if (p.getType() == ElectricityPlanType.ONE_TARIFF) {
                        sb.append(p.getDayTariff()).append(" руб/кВт·ч");
                    } else if (p.getType() == ElectricityPlanType.TWO_TARIFF) {
                        sb.append("день ").append(p.getDayTariff())
                                .append(", ночь ").append(p.getNightTariff())
                                .append(" руб/кВт·ч");
                    } else {
                        sb.append("день ").append(p.getDayTariff())
                                .append(", ночь ").append(p.getNightTariff())
                                .append(", пик ").append(p.getPeakTariff())
                                .append(" руб/кВт·ч");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
    }

    public BigDecimal getColdWaterTariff(Flat flat) {
        LocalDate today = LocalDate.now();
        return repository.findActiveByDate(today).stream()
                .filter(t -> t.getService() == ServiceType.WATER_COLD)
                .filter(t -> "Мосводоканал".equalsIgnoreCase(t.getProviderShort()))
                .map(Tariff::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getHotWaterTariff(Flat flat) {
        LocalDate today = LocalDate.now();
        return repository.findActiveByDate(today).stream()
                .filter(t -> t.getService() == ServiceType.WATER_HOT)
                .filter(t -> "МОЭК".equalsIgnoreCase(t.getProviderShort()))
                .map(Tariff::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getSewerTariff(Flat flat) {
        LocalDate today = LocalDate.now();
        return repository.findActiveByDate(today).stream()
                .filter(t -> t.getService() == ServiceType.SEWERAGE)
                .filter(t -> "Мосводоканал".equalsIgnoreCase(t.getProviderShort()))
                .map(Tariff::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private ElectricityPlan findElectricPlan(Flat flat, Meter electric, ElectricityPlanType planType) {
        LocalDate today = LocalDate.now();
        List<ElectricityPlan> plans = repository.findActiveElectricityPlans(today);

        String provider = electric.getProviderShort();
        String stove = electric.getStoveType();

        return plans.stream()
                .filter(p -> p.getProviderShort().equalsIgnoreCase(provider))
                .filter(p -> {
                    // stoveType у планов и в Meter могут отличаться по регистру, учитываем это
                    String ps = p.getStoveType();
                    if (ps == null || stove == null) return false;
                    return ps.equalsIgnoreCase(stove);
                })
                .filter(p -> p.getType() == planType)
                .findFirst()
                .orElse(null);
    }

    public BigDecimal getElectricSingleTariff(Flat flat, Meter electric) {
        ElectricityPlan plan = findElectricPlan(flat, electric, ElectricityPlanType.ONE_TARIFF);
        return plan != null ? plan.getDayTariff() : BigDecimal.ZERO;
    }

    public BigDecimal getElectricDayTariff(Flat flat, Meter electric) {
        ElectricityPlan plan = findElectricPlan(flat, electric,
                electric.getType() == MeterType.ELECTRICITY_TWO
                        ? ElectricityPlanType.TWO_TARIFF
                        : ElectricityPlanType.MULTI_TARIFF);
        return plan != null ? plan.getDayTariff() : BigDecimal.ZERO;
    }

    public BigDecimal getElectricNightTariff(Flat flat, Meter electric) {
        ElectricityPlan plan = findElectricPlan(flat, electric,
                electric.getType() == MeterType.ELECTRICITY_TWO
                        ? ElectricityPlanType.TWO_TARIFF
                        : ElectricityPlanType.MULTI_TARIFF);
        return plan != null ? plan.getNightTariff() : BigDecimal.ZERO;
    }

    // при многотарифном, если нужно, добавь:
    public BigDecimal getElectricPeakTariff(Flat flat, Meter electric) {
        ElectricityPlan plan = findElectricPlan(flat, electric, ElectricityPlanType.MULTI_TARIFF);
        return plan != null ? plan.getPeakTariff() : BigDecimal.ZERO;
    }

}

