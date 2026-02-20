package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.flat.Flat;
import ru.UtilityCalcPk.meter.Meter;
import ru.UtilityCalcPk.meter.MeterType;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
/**
 * Сервис для работы с тарифами ЖКХ: получение актуальных значений, форматирование для вывода,
 * расчёт стоимости по показаниям, обновление из внешнего источника (mos.ru).
 *
 * Основные функции:
 * - Форматированный вывод тарифов пользователю (/tariffs)
 * - Получение конкретных тарифов для расчёта (вода, свет, отведение)
 * - Автоматическое обновление тарифов 1-го числа каждого месяца
 * - Поддержка разных периодов действия тарифов
 */
public class TariffService {

    private final TariffRepository repository;
 /**
     * Конструктор сервиса.
     *
     * @param repository репозиторий для доступа к данным тарифов в БД
     */
    public TariffService(TariffRepository repository) {
        this.repository = repository;
    }
    /**
     * Формирует строку с актуальными тарифами на водоснабжение и водоотведение,
     * сгруппированными по периодам действия.
     *
     * Показывает:
     * - Холодную воду (Мосводоканал)
     * - Горячую воду (МОЭК)
     * - Водоотведение (Мосводоканал)
     *
     * Группировка:
     * - Все тарифы группируются по периодам действия (startDate → endDate)
     * - Выводятся в хронологическом порядке
     * - Для каждого периода — список тарифов по поставщикам
     *
     * Пример вывода:
     * Актуальные тарифы ЖКУ (mos.ru)
     * с 01.04.2025 по 30.06.2025
     *
     * Холодная вода:
     * • 45.56 руб/м³ (Мосводоканал)
     *
     * ────────────
     *
     * @return отформатированная строка с тарифами и сроками действия, либо сообщение об отсутствии данных
     */
    public String formatTodayTariffsForBot() {
        LocalDate today = LocalDate.now();
        List<Tariff> active = repository.findActiveByDate(today);

        if (active.isEmpty()) {
            return "На " + today + " нет активных тарифов.";
        }

        // Группируем по периоду действия
        record Period(LocalDate start, LocalDate end) {}
        Map<Period, List<Tariff>> byPeriod = active.stream()
                .collect(Collectors.groupingBy(t -> new Period(t.getStartDate(), t.getEndDate())));

        StringBuilder sb = new StringBuilder();

        // Для стабильного вывода отсортируем периоды по startDate
        List<Map.Entry<Period, List<Tariff>>> entries = new ArrayList<>(byPeriod.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().start, Comparator.nullsFirst(LocalDate::compareTo)));

        for (Map.Entry<Period, List<Tariff>> entry : entries) {
            Period p = entry.getKey();
            List<Tariff> tariffs = entry.getValue();

            // Заголовок блока по периоду
            sb.append("Актуальные тарифы ЖКУ (mos.ru)\n");
            if (p.start != null || p.end != null) {
                sb.append("с ");
                sb.append(p.start != null ? p.start : "неизвестно");
                sb.append(" по ");
                sb.append(p.end != null ? p.end : "наст. время");
            } else {
                sb.append("период действия: не указан");
            }
            sb.append("\n\n");

            // Вода + водоотведение внутри этого периода
            for (ServiceType serviceType : List.of(ServiceType.WATER_COLD, ServiceType.WATER_HOT, ServiceType.SEWERAGE)) {
                List<Tariff> list = tariffs.stream()
                        .filter(t -> t.getService() == serviceType)
                        .toList();
                if (list.isEmpty()) continue;

                // Убираем дубликаты по ключу
                Map<String, Tariff> unique = new LinkedHashMap<>();
                for (Tariff t : list) {
                    String key = t.getProviderShort() + "|" + t.getValue() + "|" + t.getUnit();
                    unique.putIfAbsent(key, t);
                }

                sb.append(serviceType.displayName()).append(":\n");
                for (Tariff t : list) {
                    sb.append("• ")
                            .append(t.getValue()).append(" ").append(t.getUnit())
                            .append(" (").append(t.getProviderShort()).append(")\n");
                }
                sb.append("\n");
            }
            sb.append("────────────\n\n");
        }

        // Электричество оставляешь как сейчас, если хочешь отдельно:
        LocalDate today2 = LocalDate.now();
        formatElectricity(sb, today2);

        return sb.toString();
    }

    /**
     * Преобразует тип электроснабжения в читаемое название.
     *
     * @param type тип тарифного плана электроэнергии
     * @return человекочитаемое название: "Однотарифный", "Двухтарифный", "Многотарифный"
     */
    private String humanPlanType(ElectricityPlanType type) {
        return switch (type) {
            case ONE_TARIFF -> "Однотарифный";
            case TWO_TARIFF -> "Двухтарифный";
            case MULTI_TARIFF -> "Многотарифный";
        };
    }
        /**
     * Преобразует тип плиты в читаемое название.
     *
     * @param stoveType тип плиты ("газовая", "электрическая" и т.п.)
     * @return человекочитаемое название: "Газовая плита", "Электроплита", или оригинальное значение
     */
    private String humanStove(String stoveType) {
        if (stoveType == null) return "плита: не указана";
        String s = stoveType.toLowerCase();
        if (s.contains("газ")) return "Газовая плита";
        if (s.contains("электр")) return "Электроплита";
        return stoveType;
    }
    /**
     * Добавляет в StringBuilder информацию об актуальных тарифах на электроэнергию,
     * сгруппированных по периодам, поставщикам и типу плиты.
     *
     * Используется для полного вывода всех тарифов, включая электроэнергию.
     *
     * @param sb   буфер для формирования строки
     * @param date дата, на которую запрашиваются тарифы (обычно LocalDate.now())
     */
    private void formatElectricity(StringBuilder sb, LocalDate date) {
        List<ElectricityPlan> plans = repository.findActiveElectricityPlans(date);
        if (plans.isEmpty()) return;

        // Группируем по периоду действия
        record Period(LocalDate start, LocalDate end) {}
        Map<Period, List<ElectricityPlan>> byPeriod = plans.stream()
                .collect(Collectors.groupingBy(p -> new Period(p.getStartDate(), p.getEndDate())));

        List<Map.Entry<Period, List<ElectricityPlan>>> entries = new ArrayList<>(byPeriod.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().start, Comparator.nullsFirst(LocalDate::compareTo)));

        for (Map.Entry<Period, List<ElectricityPlan>> entry : entries) {
            Period p = entry.getKey();
            List<ElectricityPlan> periodPlans = entry.getValue();

            sb.append("Электроэнергия ");

            if (p.start != null || p.end != null) {
                sb.append("с ");
                sb.append(p.start != null ? p.start : "неизвестно");
                sb.append(" по ");
                sb.append(p.end != null ? p.end : "наст. время");
            } else {
                sb.append("(период действия не указан)");
            }
            sb.append(":\n");

            // внутри периода — группировка по поставщику
            Map<String, List<ElectricityPlan>> byProvider = periodPlans.stream()
                    .collect(Collectors.groupingBy(ElectricityPlan::getProviderShort));

            for (var entryProv : byProvider.entrySet()) {
                String providerShort = entryProv.getKey();
                List<ElectricityPlan> providerPlans = entryProv.getValue();

                sb.append("(").append(providerShort).append("):\n");

                // внутри поставщика — по типу плиты
                Map<String, List<ElectricityPlan>> byStove = providerPlans.stream()
                        .collect(Collectors.groupingBy(ElectricityPlan::getStoveType));

                for (var e2 : byStove.entrySet()) {
                    String stove = e2.getKey();
                    List<ElectricityPlan> stovePlans = e2.getValue();

                    sb.append("\n").append("  ").append(humanStove(stove)).append(":\n");

                    List<ElectricityPlan> sorted = new ArrayList<>(stovePlans);
                    sorted.sort(Comparator.comparingInt(pPlan -> switch (pPlan.getType()) {
                        case ONE_TARIFF -> 1;
                        case TWO_TARIFF -> 2;
                        case MULTI_TARIFF -> 3;
                    }));

                    for (ElectricityPlan pPlan : sorted) {
                        String key = pPlan.getType() + "|" + pPlan.getDayTariff() + "|" +
                                pPlan.getNightTariff() + "|" + pPlan.getPeakTariff();

                        Set<String> seenKeys = new LinkedHashSet<>();

                        if (seenKeys.add(key)) {
                            sb.append("   • ")
                                    .append(humanPlanType(pPlan.getType())).append(": ");

                            if (pPlan.getType() == ElectricityPlanType.ONE_TARIFF) {
                                sb.append(pPlan.getDayTariff()).append(" руб/кВт·ч");
                            } else if (pPlan.getType() == ElectricityPlanType.TWO_TARIFF) {
                                sb.append("день ").append(pPlan.getDayTariff())
                                        .append(", ночь ").append(pPlan.getNightTariff())
                                        .append(" руб/кВт·ч");
                            } else {
                                sb.append("день ").append(pPlan.getDayTariff())
                                        .append(", ночь ").append(pPlan.getNightTariff())
                                        .append(", пик ").append(pPlan.getPeakTariff())
                                        .append(" руб/кВт·ч");
                            }
                            sb.append("\n");
                        }
                    }
                }
                sb.append("\n");
            }

            sb.append("────────────\n\n");
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

        // временный лог для проверки
        System.out.println("Plans for today:");
        for (ElectricityPlan p : plans) {
            System.out.println("provider=" + p.getProviderShort()
                    + ", stoveType=" + p.getStoveType()
                    + ", type=" + p.getType()
                    + ", day=" + p.getDayTariff());
        }
        System.out.println("Looking for provider=" + provider
                + ", stove=" + stove
                + ", type=" + planType);

        return plans.stream()
                .filter(p -> p.getProviderShort() != null
                        && provider != null
                        && p.getProviderShort().equalsIgnoreCase(provider))
                .filter(p -> {
                    if (stove == null || p.getStoveType() == null) return false;
                    String ps = p.getStoveType().toLowerCase();
                    String s = stove.toLowerCase();
                    // допускаем частичные совпадения
                    return ps.contains("газ") && s.contains("газ")
                            || ps.contains("электр") && s.contains("электр");
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

    /**
     * Проверяет, что тарифы ещё не обновлялись в этом месяце — загружает актуальные тарифы с mos.ru.
     *
     * Процесс обновления:
     * - Удаляет все существующие тарифы и планы электроснабжения из репозитория
     * - Загружает новые данные через MosRuTariffLoader
     * - Фиксирует дату последнего обновления
     *
     * Обрабатывает ошибки сети или парсинга, не прерывая работу приложения.
     * Вызывается, например, при старте бота или по расписанию.
     */
    public void ensureTariffsUpToDate() {
        LocalDate today = LocalDate.now();
        List<Tariff> active = repository.findActiveByDate(today);
        LocalDate last = repository.getLastUpdateDate();

        if (last != null && last.getYear() == today.getYear() && last.getMonth() == today.getMonth()
                && !active.isEmpty()) {
            return; // в этом месяце уже обновляли и есть актуальные тарифы
        }

        if (active.isEmpty()) {
        // перезагружаем тарифы с mos.ru (твоя логика)
        try {
            repository.deleteAllTariffs();
            repository.deleteAllElectricityPlans();
            MosRuTariffLoader.load(repository);
            repository.setLastUpdateDate(today);
        } catch (IOException e) {
            // сеть / формат mos.ru
            e.printStackTrace();
            // можно sendText себе в личку или записать в лог-файл
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // корректно помечаем поток
            e.printStackTrace();
        }
    }
}

