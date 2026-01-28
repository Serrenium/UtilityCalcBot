package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.MosRu.MosRuCells64058;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TariffMapper {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static ServiceType mapService(String tariffItem) {
        if (tariffItem == null) return null;
        String s = tariffItem.toLowerCase(Locale.ROOT);

        if (s.contains("холодное водоснабжение")) {
            return ServiceType.WATER_COLD;
        }
        if (s.contains("горячее водоснабжение")) {
            return ServiceType.WATER_HOT;
        }
        if (s.contains("водоотведение") || s.contains("канализац")) {
            return ServiceType.SEWERAGE;
        }
        if (s.contains("электроэнерг")) {
            return ServiceType.ELECTRICITY;
        }
        return null;
    }

    public static boolean mapByMeter(String measureDeviceAvail) {
        if (measureDeviceAvail == null) return true;
        String s = measureDeviceAvail.toLowerCase(Locale.ROOT);
        if (s.contains("да/нет")) return true;
        if (s.contains("да")) return true;
        if (s.contains("нет")) return false;
        return true;
    }

    public static LocalDate parseDate(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) return null;
        return LocalDate.parse(ddMMyyyy, DATE_FMT);
    }

    public static Tariff toTariff(MosRuCells64058 c) {
        ServiceType service = mapService(c.getTariffItem());
        if (service == null) {
            return null; // пропускаем записи, которые нам не нужны
        }

        // фильтр "на бытовые нужды" только для электроэнергии
        if (service == ServiceType.ELECTRICITY) {
            String target = c.getConsumptionTarget();
            if (target == null || !target.trim().equalsIgnoreCase("на бытовые нужды")) {
                return null; // пропускаем этот тариф
            }
        }

        // только Москва
        if (!c.getRegion().contains("город Москва")) {
            return null;
        }

        // фильтр на Организации
        if (!AgencyFilter.isAllowed(c)) {
            return null;
        }

        Tariff t = new Tariff();
        t.setService(service);
        t.setRegion(c.getRegion());
        t.setProvider(c.getAgency());
        t.setUnit(c.getUnitOfMeasure());
        t.setByMeter(mapByMeter(c.getMeasureDeviceAvail()));
        t.setStartDate(parseDate(c.getStartDate()));
        t.setEndDate(parseDate(c.getEndDate()));
        t.setValue(c.getTariffValue());
        return t;
    }

    public static boolean isActiveOnDate(Tariff tariff, LocalDate date) {
        if (tariff == null) return false;
        LocalDate start = tariff.getStartDate();
        LocalDate end = tariff.getEndDate();

        if (start != null && date.isBefore(start)) {
            return false;
        }
        if (end != null && date.isAfter(end)) {
            return false;
        }
        return true;
    }
}