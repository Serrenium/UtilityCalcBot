package ru;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import ru.UtilityCalcPk.flat.Flat;
import ru.UtilityCalcPk.flat.FlatRepository;
import ru.UtilityCalcPk.meter.InitialReading;
import ru.UtilityCalcPk.meter.Meter;
import ru.UtilityCalcPk.meter.MeterRepository;
import ru.UtilityCalcPk.meter.MeterType;
import ru.UtilityCalcPk.tariff.TariffService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UtilityCalc extends TelegramLongPollingBot {

    enum AddMeterState {
        NONE,
        CHOOSING_FLAT,
        CHOOSING_METER_TYPE,
        CHOOSING_ELECTRICITY_TARIFF,
        CHOOSING_STOVE_TYPE,
        ENTERING_INITIAL_READING
        }

    class AddMeterSession {
        AddMeterState state = AddMeterState.NONE;
        Long selectedFlatId;
        MeterType tempMeterType;
        String tempProviderShort;
        String tempStoveType; // для электричества
    }

    enum AddFlatState {
        NONE,
        CONFIRM_DEFAULT_NAME,
        ENTERING_CUSTOM_NAME
    }

    class AddFlatSession {
        AddFlatState state = AddFlatState.NONE;
        String tempName; // если понадобится
    }

    private final TariffService tariffService;
    private final FlatRepository flatRepository;
    private final MeterRepository meterRepository;

    private final Map<Long, AddMeterSession> addMeterSessions = new HashMap<>();
    private final Map<Long, AddFlatSession> addFlatSessions = new HashMap<>();

    public UtilityCalc(TariffService tariffService,
                       FlatRepository flatRepository,
                       MeterRepository meterRepository) {
        this.tariffService = tariffService;
        this.flatRepository = flatRepository;
        this.meterRepository = meterRepository;
    }

    @Override
    public String getBotUsername() {
        return System.getenv("TELEGRAM_BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatIdLong = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // 1) если есть активный сценарий квартиры или счётчика — обрабатываем там
        AddMeterSession meterSession = addMeterSessions.get(chatIdLong);
        AddFlatSession flatSession = addFlatSessions.get(chatIdLong);
        if ((meterSession != null && meterSession.state != AddMeterState.NONE)
                || (flatSession != null && flatSession.state != AddFlatState.NONE)) {
            handleTextMessage(update);   // <- это важно
            return;
        }

        // иначе обычные команды
        String chatId = chatIdLong.toString();
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setReplyMarkup(buildMainMenu());

        if (text.equals("/calc")) {
            if (!flatRepository.hasFlats(chatIdLong)) {
                msg.setText("Пока нет ни одной квартиры.\n" +
                        "Добавьте квартиру командой /addflat.");
            } else {
                msg.setText("Введите: горячая вода(м3), холодная вода(м3), свет(кВт·ч)\n" +
                        "Пример: 50,10,200");
            }

        } else if (text.equals("/tariffs")) {
            String tariffsText = tariffService.formatTodayTariffsForBot();
            msg.setText(tariffsText);

        } else if (text.matches("\\d+,\\d+,\\d+")) {
            String[] parts = text.split(",");
            double hot = Double.parseDouble(parts[0]);
            double cold = Double.parseDouble(parts[1]);
            double power = Double.parseDouble(parts[2]);

            double total = hot * 40 + cold * 30 + power * 5.5;
            msg.setText(String.format("Итого: %.2f ₽", total));

        } else if (text.equals("/addflat")) {
            // здесь только старт сценария:
            boolean hasDefaultFlat = flatRepository.existsByChatIdAndName(chatIdLong, "Моя квартира");
            AddFlatSession s = addFlatSessions.computeIfAbsent(chatIdLong, id -> new AddFlatSession());

            if (hasDefaultFlat) {
                s.state = AddFlatState.ENTERING_CUSTOM_NAME;
                msg.setText("Как назовем новую квартиту?");
            } else {
                s.state = AddFlatState.CONFIRM_DEFAULT_NAME;
                msg.setText("Имя «Моя квартира» подойдет?\n" +
                        "Ответьте «Да» или «Нет».");
            }

            try {
                execute(msg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;

        } else if (text.equals("/addmeter")) {
            handleAddMeterCommand(update);
            return;

        } else if (text.equals("/flats")) {
            String flatsText = formatFlatsAndMeters(chatIdLong);
            msg.setText(flatsText);

        } else if (text.startsWith("/deleteflat")) {
            String[] parts = text.split("\\s+");
            if (parts.length != 2) {
                msg.setText("Использование: /deleteflat <id>");
            } else {
                try {
                    Long id = Long.parseLong(parts[1]);
                    flatRepository.deleteById(chatIdLong, id);
                    // удаляем все счётчики этой квартиры
                    meterRepository.deleteByFlat(chatIdLong, id);
                    msg.setText("Квартира #" + id + " и её счётчики удалены.");
                } catch (NumberFormatException e) {
                    msg.setText("id должен быть числом.");
                }
            }
        } else if (text.startsWith("/deletemeter")) {
            String[] parts = text.split("\\s+");
            if (parts.length != 2) {
                msg.setText("Использование: /deletemeter <id>");
            } else {
                try {
                    Long id = Long.parseLong(parts[1]);
                    meterRepository.deleteById(chatIdLong, id);
                    msg.setText("Счётчик #" + id + " удалён.");
                } catch (NumberFormatException e) {
                    msg.setText("id должен быть числом.");
                }
            }

        } else {
            msg.setText("Команды: /calc - расчёт ЖКУ, /tariffs - актуальные тарифы, /addflat, /addmeter");
        }

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendText(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setReplyMarkup(buildMainMenu()); // всегда показываем меню

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void handleTextMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // команды /start, /addflat, /calc и т.д.

        // 1) сценарий добавления счётчика
        AddMeterSession meterSession = addMeterSessions.get(chatId);
        if (meterSession != null && meterSession.state != AddMeterState.NONE) {
            continueAddMeter(update, meterSession);
            return;
        }

        // 2) сценарий добавления квартиры (имя)
        AddFlatSession flatSession = addFlatSessions.get(chatId);
        if (flatSession != null && flatSession.state != AddFlatState.NONE) {
            continueAddFlat(update, flatSession);
            return;
        }

        // остальной free‑text
    }

    private void continueAddFlat(Update update, AddFlatSession session) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        switch (session.state) {
            case CONFIRM_DEFAULT_NAME -> {
                if (text.equalsIgnoreCase("да")) {
                    Flat flat = new Flat();
                    flat.setChatId(chatId);
                    flat.setName("Моя квартира");
                    flatRepository.save(flat);

                    sendText(chatId,
                            "Квартира добавлена: Моя квартира\n\n" +
                                    "Теперь вы можете добавить счётчики командой /addmeter.");
                    session.state = AddFlatState.NONE;
                } else if (text.equalsIgnoreCase("нет")) {
                    sendText(chatId,
                            "Введите название новой квартиры (например, «Квартира на Пушкина»).");
                    session.state = AddFlatState.ENTERING_CUSTOM_NAME;
                } else {
                    sendText(chatId, "Ответьте, пожалуйста, «Да» или «Нет».");
                }
            }
            case ENTERING_CUSTOM_NAME -> {
                String name = text;
                if (name.isEmpty()) {
                    sendText(chatId, "Название не может быть пустым. Введите название квартиры.");
                    return;
                }

                Flat flat = new Flat();
                flat.setChatId(chatId);
                flat.setName(name);
                flatRepository.save(flat);

                sendText(chatId,
                        "Квартира добавлена: " + name +
                                "\n\nТеперь вы можете добавить счётчики командой /addmeter.");
                session.state = AddFlatState.NONE;
            }
        }
    }

    private void handleAddMeterCommand(Update update) {
        Long chatId = update.getMessage().getChatId();
        AddMeterSession session = addMeterSessions.computeIfAbsent(chatId, id -> new AddMeterSession());

        session.state = AddMeterState.CHOOSING_FLAT;
        session.selectedFlatId = null;
        session.tempMeterType = null;

        List<Flat> flats = flatRepository.findByChatId(chatId);
        if (flats.isEmpty()) {
            sendText(chatId,
                    "Пока нет квартир. Добавьте квартиру командой /addflat.");
            session.state = AddMeterState.NONE;
            return;
        }

        if (flats.size() == 1) {
            // сразу выбираем единственную квартиру
            Flat flat = flats.get(0);
            session.selectedFlatId = flat.getId();

            sendText(chatId,
                    "Выберите тип счётчика:\n" +
                            "1) Холодная вода\n" +
                            "2) Горячая вода\n" +
                            "3) Электроэнергия (однотарифный)\n" +
                            "4) Электроэнергия (двухтарифный)\n" +
                            "5) Электроэнергия (многотарифный)\n\n" +
                            "Отправьте номер варианта.");

            session.state = AddMeterState.CHOOSING_METER_TYPE;
            return;
        }

        StringBuilder sb = new StringBuilder("Выберите квартиру для счётчика:\n");
        for (int i = 0; i < flats.size(); i++) {
            Flat f = flats.get(i);
            sb.append(i + 1).append(") ").append(f.getName()).append("\n");
        }
        sb.append("\nОтправьте номер квартиры.");

        sendText(chatId, sb.toString());
    }
    private void continueAddMeter(Update update, AddMeterSession session) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        switch (session.state) {
            case CHOOSING_FLAT -> {
                List<Flat> flats = flatRepository.findByChatId(chatId);
                int idx;
                try {
                    idx = Integer.parseInt(text) - 1;
                } catch (NumberFormatException e) {
                    sendText(chatId, "Нужно отправить номер квартиры из списка.");
                    return;
                }
                if (idx < 0 || idx >= flats.size()) {
                    sendText(chatId, "Такой квартиры нет в списке. Попробуйте ещё раз.");
                    return;
                }
                Flat flat = flats.get(idx);
                session.selectedFlatId = flat.getId();

                sendText(chatId,
                        "Выберите тип счётчика:\n" +
                                "1) Холодная вода\n" +
                                "2) Горячая вода\n" +
                                "3) Электроэнергия (однотарифный)\n" +
                                "4) Электроэнергия (двухтарифный)\n" +
                                "5) Электроэнергия (многотарифный)\n\n" +
                                "Отправьте номер варианта.");

                session.state = AddMeterState.CHOOSING_METER_TYPE;
            }
            case CHOOSING_METER_TYPE -> {
                MeterType type = switch (text) {
                    case "1" -> MeterType.WATER_COLD;
                    case "2" -> MeterType.WATER_HOT;
                    case "3" -> MeterType.ELECTRICITY_ONE;
                    case "4" -> MeterType.ELECTRICITY_TWO;
                    case "5" -> MeterType.ELECTRICITY_MULTI;
                    default -> null;
                };
                if (type == null) {
                    sendText(chatId, "Нужно отправить номер из списка (1–5).");
                    return;
                }
                session.tempMeterType = type;

                // провайдеры по умолчанию
                switch (type) {
                    case WATER_COLD -> session.tempProviderShort = "Мосводоканал";
                    case WATER_HOT -> session.tempProviderShort = "МОЭК";
                    case ELECTRICITY_ONE, ELECTRICITY_TWO, ELECTRICITY_MULTI ->
                            session.tempProviderShort = "Мосэнергосбыт";
                }

                // для воды сразу спрашиваем показания
                if (type == MeterType.WATER_COLD || type == MeterType.WATER_HOT) {
                    sendText(chatId,
                            "Введите начальные показания счётчика (число, например 123.45).");
                    session.state = AddMeterState.ENTERING_INITIAL_READING;
                    return;
                }

                // для электричества сначала спрашиваем тип плиты
                sendText(chatId,
                        "Какой тип плиты в квартире?\n" +
                                "1) Газовая плита\n" +
                                "2) Электроплита\n\n" +
                                "Отправьте номер варианта.");
                session.state = AddMeterState.CHOOSING_STOVE_TYPE;

            }
            case CHOOSING_STOVE_TYPE -> {
                if ("1".equals(text)) {
                    session.tempStoveType = "газовая плита";
                } else if ("2".equals(text)) {
                    session.tempStoveType = "электроплита";
                } else {
                    sendText(chatId, "Нужно 1 или 2.");
                    return;
                }

                // теперь спрашиваем показания для одно/двух/трёхтарифного
                if (session.tempMeterType == MeterType.ELECTRICITY_ONE) {
                    sendText(chatId,
                            "Введите начальные показания счётчика (число, например 123.45).");
                } else if (session.tempMeterType == MeterType.ELECTRICITY_TWO) {
                    sendText(chatId,
                            "Введите два числа через пробел: день и ночь.\n" +
                                    "Например: 1234.5 678.9");
                } else {
                    sendText(chatId,
                            "Введите три числа через пробел: день, ночь, пик.\n" +
                                    "Например: 1234.5 678.9 12.3");
                }
                session.state = AddMeterState.ENTERING_INITIAL_READING;
            }
            case ENTERING_INITIAL_READING -> {
                String[] parts = text.replace(',', '.').trim().split("\\s+");
                InitialReading r = new InitialReading();

                try {
                    switch (session.tempMeterType) {
                        case WATER_COLD, WATER_HOT, ELECTRICITY_ONE -> {
                            if (parts.length != 1) {
                                sendText(chatId, "Нужно одно число. Попробуйте ещё раз.");
                                return;
                            }
                            r.setTotal(new BigDecimal(parts[0]));
                        }
                        case ELECTRICITY_TWO -> {
                            if (parts.length != 2) {
                                sendText(chatId,
                                        "Нужно два числа: день и ночь, через пробел.");
                                return;
                            }
                            r.setDay(new BigDecimal(parts[0]));
                            r.setNight(new BigDecimal(parts[1]));
                        }
                        case ELECTRICITY_MULTI -> {
                            if (parts.length != 3) {
                                sendText(chatId,
                                        "Нужно три числа: день, ночь, пик, через пробел.");
                                return;
                            }
                            r.setDay(new BigDecimal(parts[0]));
                            r.setNight(new BigDecimal(parts[1]));
                            r.setPeak(new BigDecimal(parts[2]));
                        }
                    }
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не получилось распознать числа. Введите ещё раз.");
                    return;
                }

                Meter meter = new Meter();
                meter.setChatId(chatId);
                meter.setFlatId(session.selectedFlatId);
                meter.setType(session.tempMeterType);
                meter.setProviderShort(session.tempProviderShort);
                meter.setStoveType(session.tempStoveType); // для воды будет null
                meter.setInitialReading(r);

                meterRepository.save(meter);

                sendText(chatId, "Счётчик добавлен ✅\n\n" +
                        "Добавить ещё один счётчик? Нажмите /addmeter или выберите в меню.");

                session.state = AddMeterState.NONE;
                session.selectedFlatId = null;
                session.tempMeterType = null;
                session.tempProviderShort = null;
                session.tempStoveType = null;
            }
        }
    }
    private ReplyKeyboardMarkup buildMainMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("/calc"));
        row1.add(new KeyboardButton("/tariffs"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("/addflat"));
        row2.add(new KeyboardButton("/addmeter"));

        List<KeyboardRow> keyboard = new java.util.ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
    private String formatFlatsAndMeters(Long chatId) {
        List<Flat> flats = flatRepository.findByChatId(chatId);
        if (flats.isEmpty()) {
            return "У вас пока нет квартир. Добавьте квартиру командой /addflat.";
        }

        StringBuilder sb = new StringBuilder("Ваши квартиры и счётчики:\n\n");
        for (Flat flat : flats) {
            sb.append("Квартира #").append(flat.getId())
                    .append(": ").append(flat.getName()).append("\n");

            List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
            if (meters.isEmpty()) {
                sb.append("  (нет счётчиков)\n");
            } else {
                for (Meter meter : meters) {
                    sb.append("  Счётчик #").append(meter.getId()).append(": ");

                    switch (meter.getType()) {
                        case WATER_COLD -> sb.append("Холодная вода");
                        case WATER_HOT -> sb.append("Горячая вода");
                        case ELECTRICITY_ONE -> sb.append("Электроэнергия (1‑тариф)");
                        case ELECTRICITY_TWO -> sb.append("Электроэнергия (2‑тарифа)");
                        case ELECTRICITY_MULTI -> sb.append("Электроэнергия (3‑тарифа)");
                    }

                    if (meter.getStoveType() != null) {
                        sb.append(", ").append(meter.getStoveType());
                    }
                    if (meter.getProviderShort() != null) {
                        sb.append(", ").append(meter.getProviderShort());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("Редактирование:\n")
                .append("/editflat <id> – изменить название квартиры\n")
                .append("/deleteflat <id> – удалить квартиру и её счётчики\n")
                .append("/editmeter <id> – изменить тип/плиту/показания счётчика\n")
                .append("/deletemeter <id> – удалить счётчик");

        return sb.toString();
    }

}