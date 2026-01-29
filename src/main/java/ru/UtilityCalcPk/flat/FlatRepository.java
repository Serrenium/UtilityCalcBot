package ru.UtilityCalcPk.flat;

import java.util.*;

public class FlatRepository {

    // ключ: chatId, значение: список квартир пользователя
    private final Map<Long, List<Flat>> flatsByChat = new HashMap<>();

    public List<Flat> findByChatId(Long chatId) {
        return flatsByChat.getOrDefault(chatId, Collections.emptyList());
    }
    // счетчик для генерации уникальных идентификаторов
    private long flatSeq = 1L;

    public void save(Flat flat) {
        if (flat.getId() == null) {
            flat.setId(flatSeq++);
        }
        flatsByChat
                .computeIfAbsent(flat.getChatId(), id -> new ArrayList<>())
                .add(flat);
    }

    public boolean hasFlats(Long chatId) {
        return !findByChatId(chatId).isEmpty();
    }

    public boolean existsByChatIdAndName(Long chatId, String name) {
        return findByChatId(chatId).stream()
                .anyMatch(f -> name.equals(f.getName()));
    }

    public void deleteById(Long chatId, Long flatId) {
        List<Flat> list = flatsByChat.get(chatId);
        if (list == null) return;
        list.removeIf(f -> f.getId().equals(flatId));
    }

}
