package ru.UtilityCalcPk.tariff;

public class ProviderNames {
    public static String shortName(String full) {
        if (full == null) return "";
        String s = full.toLowerCase();

        if (s.contains("мосводоканал")) return "Мосводоканал";
        if (s.contains("мосэнергосбыт")) return "Мосэнергосбыт";
        // добавишь свои правила

        return full; // по умолчанию без сокращения
    }
}
