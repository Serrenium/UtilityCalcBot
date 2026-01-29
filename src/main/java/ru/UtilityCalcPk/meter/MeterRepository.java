package ru.UtilityCalcPk.meter;

import java.util.*;
import java.util.stream.Collectors;

public class MeterRepository {

    // ключ: chatId, значение: список счетчиков пользователя
    private final Map<Long, List<Meter>> metersByChat = new HashMap<>();

    private long meterSeq = 1L;

    public void save(Meter meter) {
        if (meter.getId() == null) {
            meter.setId(meterSeq++);
        }
        metersByChat
                .computeIfAbsent(meter.getChatId(), id -> new ArrayList<>())
                .add(meter);
    }

    public List<Meter> findByChatId(Long chatId) {
        return metersByChat.getOrDefault(chatId, Collections.emptyList());
    }

    public List<Meter> findByFlat(Long chatId, Long flatId) {
        return findByChatId(chatId).stream()
                .filter(m -> Objects.equals(m.getFlatId(), flatId))
                .collect(Collectors.toList());
    }

    public void deleteById(Long chatId, Long meterId) {
        List<Meter> list = metersByChat.get(chatId);
        if (list == null) return;
        list.removeIf(m -> Objects.equals(m.getId(), meterId));
    }

    public void deleteByFlat(Long chatId, Long flatId) {
        List<Meter> list = metersByChat.get(chatId);
        if (list == null) return;
        list.removeIf(m -> Objects.equals(m.getFlatId(), flatId));
    }
}

