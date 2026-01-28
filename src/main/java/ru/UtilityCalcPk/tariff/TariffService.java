package ru.UtilityCalcPk.tariff;

import java.time.LocalDate;
import java.util.List;

public class TariffService {

    private final TariffRepository repository;

    public TariffService(TariffRepository repository) {
        this.repository = repository;
    }

    public String formatTodayTariffsForBot() {
        LocalDate today = LocalDate.now();

        StringBuilder sb = new StringBuilder();
        sb.append("Текущие тарифы на ").append(today).append(":\n\n");

        // вода и водоотведение
        for (Tariff t : repository.findActiveByDate(today)) {
            sb.append("• ")
                    .append(t.getService()).append(" — ")
                    .append(t.getProvider()).append(" — ")
                    .append(t.getValue()).append(" ").append(t.getUnit())
                    .append("\n");
        }

        sb.append("\nЭлектроэнергия:\n");
        for (ElectricityPlan p : repository.findActiveElectricityPlans(today)) {
            sb.append("• ")
                    .append(p.getType())                 // одно/двух/многотарифный
                    .append(", плита: ").append(p.getStoveType())
                    .append(" — ").append(p.getProvider())
                    .append(" (день=").append(p.getDayTariff());
            if (p.getNightTariff() != null) {
                sb.append(", ночь=").append(p.getNightTariff());
            }
            if (p.getPeakTariff() != null) {
                sb.append(", пик=").append(p.getPeakTariff());
            }
            sb.append(" ").append("руб/кВт·ч").append(")\n");
        }

        return sb.toString();
    }
}

