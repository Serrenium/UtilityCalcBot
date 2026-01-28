package ru.UtilityCalcPk.tariff;

import ru.UtilityCalcPk.MosRu.MosRuCells64058;

import java.util.List;

public class AgencyFilter {

    // что должно быть в строке
    private static final List<String> ALLOWED_SUBSTRINGS = List.of(
            "мосводоканал",
            "московская объединенная энергетическая компания",
            "мосэнергосбыт"
    );

    // что НЕ должно быть в строке
    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "на территории городского округа щербинка"
    );

    public static boolean isAllowed(MosRuCells64058 cells) {
        String agency = cells.getAgency();
        if (agency == null) return false;

        String lower = agency.toLowerCase();

        // сначала исключения
        for (String bad : FORBIDDEN_SUBSTRINGS) {
            if (lower.contains(bad)) {
                return false;
            }
        }

        // затем разрешённые
        for (String good : ALLOWED_SUBSTRINGS) {
            if (lower.contains(good)) {
                return true;
            }
        }
        return false;
    }
}