package ru.UtilityCalcPk.flat;

import java.util.*;

public class FlatRepository {

    // ключ: chatId, значение: список квартир пользователя
    private final Map<Long, List<Flat>> flatsByChat = new HashMap<>();

    public List<Flat> findByChatId(Long chatId) {
        return flatsByChat.getOrDefault(chatId, Collections.emptyList());
    }

    public void save(Flat flat) {
        flatsByChat
                .computeIfAbsent(flat.getChatId(), id -> new ArrayList<>())
                .add(flat);
    }

    public boolean hasFlats(Long chatId) {
        return !findByChatId(chatId).isEmpty();
    }
}
