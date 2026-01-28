package ru.UtilityCalcPk.meter;

import java.util.*;

public class MeterRepository {

    // ключ: chatId, значение: список счетчиков пользователя
    private final Map<Long, List<Meter>> metersByChat = new HashMap<>();

    public void save(Meter meter) {
        metersByChat
                .computeIfAbsent(meter.getChatId(), id -> new ArrayList<>())
                .add(meter);
    }

    public List<Meter> findByChatId(Long chatId) {
        return metersByChat.getOrDefault(chatId, Collections.emptyList());
    }
}
