package ru.UtilityCalcPk.tariff;

import java.time.LocalDate;
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
        sb.append("Текущие тарифы ЖКУ (").append(today).append("):\n\n");

        // сгруппуем по услуге
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
        if (s.contains("газ")) return "газовая плита";
        if (s.contains("электр")) return "электроплита";
        return stoveType;
    }
    private void formatElectricity(StringBuilder sb, LocalDate date) {
        List<ElectricityPlan> plans = repository.findActiveElectricityPlans(date);
        if (plans.isEmpty()) return;

        sb.append("Электроэнергия:\n");

        // сгруппуем по поставщику
        Map<String, List<ElectricityPlan>> byProvider = plans.stream()
                .collect(Collectors.groupingBy(ElectricityPlan::getProviderShort));

        for (var entry : byProvider.entrySet()) {
            String providerShort = entry.getKey();
            List<ElectricityPlan> providerPlans = entry.getValue();

            sb.append(providerShort).append(":\n");

            // внутри — по типу плиты
            Map<String, List<ElectricityPlan>> byStove = providerPlans.stream()
                    .collect(Collectors.groupingBy(ElectricityPlan::getStoveType));

            for (var e2 : byStove.entrySet()) {
                String stove = e2.getKey();
                List<ElectricityPlan> stovePlans = e2.getValue();

                sb.append("  ").append(humanStove(stove)).append(":\n");

                for (ElectricityPlan p : stovePlans) {
                    sb.append("    ")
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
}

