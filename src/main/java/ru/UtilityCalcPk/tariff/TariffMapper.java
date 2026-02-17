package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.MosRu.MosRuCells64058;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
/**
 * Утилитарный класс для преобразования сырых данных с портала mos.ru (из таблицы Мосэнергосбыта и др.)
 * в доменные объекты приложения, такие как {@link Tariff}, а также для фильтрации и валидации данных.
 *
 * Основные функции:
 * - Определение типа услуги по названию тарифа (вода, горячее водоснабжение, канализация, электричество)
 * - Проверка, учитывается ли тариф по счётчику
 * - Парсинг дат начала и окончания действия тарифа
 * - Преобразование строки из таблицы Mos.ru в объект {@link Tariff}
 * - Проверка, актуален ли тариф на указанную дату
 *
 * Используется в основном при импорте тарифов из внешнего источника (например, парсинг PDF или CSV с mos.ru).
 */
public class TariffMapper {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");
   /**
     * Преобразует текстовое описание услуги (например, "Холодное водоснабжение") в перечисление {@link ServiceType}.
     *
     * Поддерживаемые ключевые слова:
     * - "холодное водоснабжение" → {@link ServiceType#WATER_COLD}
     * - "горячее водоснабжение" → {@link ServiceType#WATER_HOT}
     * - "водоотведение", "канализация" → {@link ServiceType#SEWERAGE}
     * - "электроэнергия" → {@link ServiceType#ELECTRICITY}
     *
     * Регистронезависимо. Если совпадений нет — возвращает null.
     *
     * @param tariffItem строка с описанием услуги, может быть null
     * @return соответствующий тип услуги или null, если не распознан
     */
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
/**
     * Определяет, применяется ли тариф только при наличии счётчика.
     *
     * Анализирует поле "Наличие прибора учёта":
     * - "да" / "Да" / "ДА" → true (тариф действует только по счётчику)
     * - "нет" / "Нет" / "НЕТ" → false (тариф без счётчика)
     * - "да/нет" или любое другое значение → true (поддерживается оба случая)
     *
     * По умолчанию возвращает true, чтобы не блокировать данные с неясным значением.
     *
     * @param measureDeviceAvail значение поля "Наличие прибора учёта", может быть null
     * @return true, если тариф действует при наличии счётчика; false — если только без счётчика
     */
    public static boolean mapByMeter(String measureDeviceAvail) {
        if (measureDeviceAvail == null) return true;
        String s = measureDeviceAvail.toLowerCase(Locale.ROOT);
        if (s.contains("да/нет")) return true;
        if (s.contains("да")) return true;
        if (s.contains("нет")) return false;
        return true;
    }
/**
     * Парсит строку с датой в формате "dd.MM.yyyy" в объект {@link LocalDate}.
     *
     * Используется для обработки дат начала и окончания действия тарифа.
     *
     * @param ddMMyyyy строка с датой, например "01.04.2025", может быть null или пустой
     * @return объект LocalDate или null, если строка пустая или невалидная
     */
    public static LocalDate parseDate(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) return null;
        return LocalDate.parse(ddMMyyyy, DATE_FMT);
    }
    /**
     * Преобразует объект {@link MosRuCells64058} (одна строка из таблицы тарифов с mos.ru)
     * в доменный объект {@link Tariff}, применяя фильтрацию по региону, цели потребления и организации.
     *
     * Фильтрация:
     * - Только услуги, поддерживаемые в приложении (вода, свет, канализация)
     * - Только для "на бытовые нужды", если услуга — электроэнергия
     * - Только для региона "город Москва"
     * - Только для разрешённых организаций (через {@link AgencyFilter})
     *
     * Если запись не проходит фильтрацию — возвращается null.
     *
     * @param c объект с данными из таблицы тарифов mos.ru
     * @return заполненный объект {@link Tariff} или null, если запись должна быть пропущена
     */
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
        t.setProviderShort(ProviderNames.shortName(c.getAgency()));
        t.setUnit(c.getUnitOfMeasure());
        t.setByMeter(mapByMeter(c.getMeasureDeviceAvail()));
        t.setStartDate(parseDate(c.getStartDate()));
        t.setEndDate(parseDate(c.getEndDate()));
        t.setValue(c.getTariffValue());
        return t;
    }
    /**
     * Проверяет, является ли тариф активным на указанную дату.
     *
     * Условия:
     * - Если startDate != null и date < startDate → false
     * - Если endDate != null и date > endDate → false
     * - В остальных случаях — true
     *
     * @param tariff объект тарифа, может быть null
     * @param date   дата, на которую проверяется актуальность
     * @return true, если тариф действует на указанную дату; false — если не действует или даты не заданы корректно
     */
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