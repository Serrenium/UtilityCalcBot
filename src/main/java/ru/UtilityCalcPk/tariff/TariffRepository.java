package ru.UtilityCalcPk.tariff;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TariffRepository {

    private final List<Tariff> tariffs = new ArrayList<>();
    private final List<ElectricityPlan> electricityPlans = new ArrayList<>();

    public void saveTariff(Tariff t) {
        tariffs.add(t);
    }

    public void saveElectricityPlan(ElectricityPlan p) {
        electricityPlans.add(p);
    }

    public List<Tariff> findActiveByDate(LocalDate date) {
        return tariffs.stream()
                .filter(t -> TariffMapper.isActiveOnDate(t, date))
                .toList();
    }

    public List<ElectricityPlan> findActiveElectricityPlans(LocalDate date) {
        return electricityPlans.stream()
                .filter(p -> {
                    LocalDate start = p.getStartDate();
                    LocalDate end = p.getEndDate();
                    if (start != null && date.isBefore(start)) return false;
                    if (end != null && date.isAfter(end)) return false;
                    return true;
                })
                .toList();
    }
}
