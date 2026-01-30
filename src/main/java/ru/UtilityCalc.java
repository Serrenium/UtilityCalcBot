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
    enum OnboardingState {
        NONE,
        ASKING_STOVE_TYPE,
        ASKING_ELECTRIC_METER_TYPE,
        ASKING_INITIAL_READINGS_WATER_COLD,
        ASKING_INITIAL_READINGS_WATER_HOT,
        ASKING_INITIAL_READINGS_ELECTRIC
    }

    class OnboardingSession {
        OnboardingState state = OnboardingState.NONE;
        Long defaultFlatId;
        Long electricMeterId;
    }
    enum CalcState {
        NONE,
        ASKING_COLD,
        ASKING_HOT,
        ASKING_ELECTRIC
    }

    class CalcSession {
        CalcState state = CalcState.NONE;
        Long flatId;

        BigDecimal coldCurrent;
        BigDecimal hotCurrent;
        InitialReading electricCurrent; // day/night/peak для текущих показаний
    }
    private final TariffService tariffService;
    private final FlatRepository flatRepository;
    private final MeterRepository meterRepository;

    private final Map<Long, AddMeterSession> addMeterSessions = new HashMap<>();
    private final Map<Long, AddFlatSession> addFlatSessions = new HashMap<>();
    private final Map<Long, OnboardingSession> onboardingSessions = new HashMap<>();
    private final Map<Long, CalcSession> calcSessions = new HashMap<>();

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
        try {

            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            Long chatIdLong = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            // 1) если есть активный сценарий квартиры или счётчика — обрабатываем там
            AddMeterSession meterSession = addMeterSessions.get(chatIdLong);
            AddFlatSession flatSession = addFlatSessions.get(chatIdLong);
            OnboardingSession onboarding = onboardingSessions.get(chatIdLong);
            CalcSession calcSession = calcSessions.get(chatIdLong);
            if ((meterSession != null && meterSession.state != AddMeterState.NONE)
                    || (flatSession != null && flatSession.state != AddFlatState.NONE)
                    || (onboarding != null && onboarding.state != OnboardingState.NONE)
                    || (calcSession != null && calcSession.state != CalcState.NONE)) {
                handleTextMessage(update);
                return;
            }

            // иначе обычные команды
            String chatId = chatIdLong.toString();
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId);
            msg.setReplyMarkup(buildMainMenu());

            if (text.equals("/calc")) {

                ensureDefaultSetup(chatIdLong); // на всякий случай

                List<Flat> flats = flatRepository.findByChatId(chatIdLong);
                if (flats.isEmpty()) {
                    msg.setText("У вас пока нет квартир. Попробуйте /start или /addflat.");
                } else {
                    Flat flat = flats.get(0); // пока считаем по первой (дефолтной)

                    CalcSession session = calcSessions.computeIfAbsent(chatIdLong, id -> new CalcSession());
                    session.state = CalcState.ASKING_COLD;
                    session.flatId = flat.getId();
                    session.coldCurrent = null;
                    session.hotCurrent = null;
                    session.electricCurrent = null;

                    msg.setText("Введите текущие показания по холодной воде (м³), одно число, например 123.45.");
                }

                try {
                    execute(msg);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;

            } else if (text.equals("/tariffs")) {
                String tariffsText = tariffService.formatTodayTariffsForBot();
                msg.setText(tariffsText);

            } else if (text.matches("/\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+/gm")) {
                String[] parts = text.split(",");
                double hot = Double.parseDouble(parts[0]);
                double cold = Double.parseDouble(parts[1]);
                double power = Double.parseDouble(parts[2]);

                double total = hot * 40 + cold * 30 + power * 5.5;
                msg.setText(String.format("Итого: %.2f ₽", total));

            } else if (text.equals("/start")) {

                ensureDefaultSetup(chatIdLong);

                sendText(chatIdLong,
                        "Я создал для вас квартиру «Моя квартира» с тремя счётчиками:\n" +
                                "холодная вода, горячая вода и электроэнергия.\n\n" +
                                "Сейчас уточним тип плиты и тип электросчётчика, а затем попросим первые показания.");

                startOnboardingElectricity(update); // запустим мастер для плиты и типа счётчика
                return;

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
            execute(msg);

        } catch (Exception e) {
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

        // 3) онбординг
        OnboardingSession onboarding = onboardingSessions.get(chatId);
        if (onboarding != null && onboarding.state != OnboardingState.NONE) {
            continueOnboarding(update, onboarding);
            return;
        }

        CalcSession calcSession = calcSessions.get(chatId);
        if (calcSession != null && calcSession.state != CalcState.NONE) {
            continueCalc(update, calcSession);
            return;
        }
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
    private void ensureDefaultSetup(Long chatId) {
        // если у пользователя уже есть хоть одна квартира — ничего не делаем
        if (flatRepository.hasFlats(chatId)) {
            return;
        }

        // создаём дефолтную квартиру
        Flat flat = new Flat();
        flat.setChatId(chatId);
        flat.setName("Моя квартира");
        flatRepository.save(flat); // id заполнится из БД

        // холодная вода
        Meter cold = new Meter();
        cold.setChatId(chatId);
        cold.setFlatId(flat.getId());
        cold.setType(MeterType.WATER_COLD);
        cold.setProviderShort("Мосводоканал");
        cold.setInitialReading(new InitialReading()); // пока пусто
        meterRepository.save(cold);

        // горячая вода
        Meter hot = new Meter();
        hot.setChatId(chatId);
        hot.setFlatId(flat.getId());
        hot.setType(MeterType.WATER_HOT);
        hot.setProviderShort("МОЭК");
        hot.setInitialReading(new InitialReading());
        meterRepository.save(hot);

        // электроэнергия (тип и плита уточним потом)
        Meter el = new Meter();
        el.setChatId(chatId);
        el.setFlatId(flat.getId());
        el.setType(MeterType.ELECTRICITY_ONE); // временно однотарифный
        el.setProviderShort("Мосэнергосбыт");
        el.setInitialReading(new InitialReading());
        meterRepository.save(el);
    }
    private void startOnboardingElectricity(Update update) {
        Long chatId = update.getMessage().getChatId();

        // найдём дефолтную квартиру и её электросчётчик
        List<Flat> flats = flatRepository.findByChatId(chatId);
        if (flats.isEmpty()) {
            sendText(chatId, "Не удалось найти квартиру. Попробуйте ещё раз команду /start.");
            return;
        }
        Flat flat = flats.get(0); // у нас одна дефолтная

        List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
        Meter electric = meters.stream()
                .filter(m -> m.getType() == MeterType.ELECTRICITY_ONE
                        || m.getType() == MeterType.ELECTRICITY_TWO
                        || m.getType() == MeterType.ELECTRICITY_MULTI)
                .findFirst()
                .orElse(null);

        if (electric == null) {
            sendText(chatId, "Не найден электросчётчик. Попробуйте /addmeter.");
            return;
        }

        OnboardingSession session = onboardingSessions.computeIfAbsent(chatId, id -> new OnboardingSession());
        session.state = OnboardingState.ASKING_STOVE_TYPE;
        session.defaultFlatId = flat.getId();
        session.electricMeterId = electric.getId();

        sendText(chatId,
                "Какой у вас тип плиты?\n" +
                        "1) Газовая плита\n" +
                        "2) Электроплита\n\n" +
                        "Отправьте номер варианта.");
    }
    private void continueOnboarding(Update update, OnboardingSession session) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        switch (session.state) {
            case ASKING_STOVE_TYPE -> {
                String stove;
                if ("1".equals(text)) {
                    stove = "газовая плита";
                } else if ("2".equals(text)) {
                    stove = "электроплита";
                } else {
                    sendText(chatId, "Нужно 1 или 2.");
                    return;
                }

                // обновляем электросчётчик
                Meter el = getElectricMeter(chatId, session.electricMeterId);
                if (el == null) {
                    sendText(chatId, "Не удалось найти электросчётчик.");
                    session.state = OnboardingState.NONE;
                    return;
                }
                el.setStoveType(stove);
                meterRepository.save(el);

                sendText(chatId,
                        "Какой у вас электрический счётчик?\n" +
                                "1) Однотарифный\n" +
                                "2) Двухтарифный\n" +
                                "3) Многотарифный\n\n" +
                                "Отправьте номер варианта.");
                session.state = OnboardingState.ASKING_ELECTRIC_METER_TYPE;
            }
            case ASKING_ELECTRIC_METER_TYPE -> {
                MeterType type;
                if ("1".equals(text)) {
                    type = MeterType.ELECTRICITY_ONE;
                } else if ("2".equals(text)) {
                    type = MeterType.ELECTRICITY_TWO;
                } else if ("3".equals(text)) {
                    type = MeterType.ELECTRICITY_MULTI;
                } else {
                    sendText(chatId, "Нужно 1, 2 или 3.");
                    return;
                }

                Meter el = getElectricMeter(chatId, session.electricMeterId);
                if (el == null) {
                    sendText(chatId, "Не удалось найти электросчётчик.");
                    session.state = OnboardingState.NONE;
                    return;
                }
                el.setType(type);
                meterRepository.save(el);

                sendText(chatId,
                        "Теперь введите первые показания счётчиков.\n" +
                                "Сначала холодная вода (одно число, например 123.45):");
                session.state = OnboardingState.ASKING_INITIAL_READINGS_WATER_COLD;
            }
            case ASKING_INITIAL_READINGS_WATER_COLD -> {
                BigDecimal value;
                try {
                    value = new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать число. Введите показания ещё раз.");
                    return;
                }

                // найдём холодный счётчик и запишем
                Flat flat = flatRepository.findByChatId(chatId).get(0);
                List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
                Meter cold = meters.stream()
                        .filter(m -> m.getType() == MeterType.WATER_COLD)
                        .findFirst().orElse(null);
                if (cold != null) {
                    InitialReading r = cold.getInitialReading();
                    if (r == null) r = new InitialReading();
                    r.setTotal(value);
                    cold.setInitialReading(r);
                    meterRepository.save(cold);
                }

                sendText(chatId,
                        "Теперь горячая вода (одно число, например 123.45):");
                session.state = OnboardingState.ASKING_INITIAL_READINGS_WATER_HOT;
            }
            case ASKING_INITIAL_READINGS_WATER_HOT -> {
                BigDecimal value;
                try {
                    value = new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать число. Введите показания ещё раз.");
                    return;
                }

                Flat flat = flatRepository.findByChatId(chatId).get(0);
                List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
                Meter hot = meters.stream()
                        .filter(m -> m.getType() == MeterType.WATER_HOT)
                        .findFirst().orElse(null);
                if (hot != null) {
                    InitialReading r = hot.getInitialReading();
                    if (r == null) r = new InitialReading();
                    r.setTotal(value);
                    hot.setInitialReading(r);
                    meterRepository.save(hot);
                }

                // теперь спросим электричество в зависимости от типа
                Meter el = getElectricMeter(chatId, session.electricMeterId);
                if (el == null) {
                    sendText(chatId, "Не удалось найти электросчётчик.");
                    session.state = OnboardingState.NONE;
                    return;
                }

                if (el.getType() == MeterType.ELECTRICITY_ONE) {
                    sendText(chatId,
                            "Введите первые показания электросчётчика (одно число):");
                } else if (el.getType() == MeterType.ELECTRICITY_TWO) {
                    sendText(chatId,
                            "Введите два числа через пробел: день и ночь.\n" +
                                    "Например: 1234.5 678.9");
                } else {
                    sendText(chatId,
                            "Введите три числа через пробел: день, ночь, пик.\n" +
                                    "Например: 1234.5 678.9 12.3");
                }
                session.state = OnboardingState.ASKING_INITIAL_READINGS_ELECTRIC;
            }
            case ASKING_INITIAL_READINGS_ELECTRIC -> {
                Meter el = getElectricMeter(chatId, session.electricMeterId);
                if (el == null) {
                    sendText(chatId, "Не удалось найти электросчётчик.");
                    session.state = OnboardingState.NONE;
                    return;
                }

                String[] parts = text.replace(',', '.').trim().split("\\s+");
                InitialReading r = new InitialReading();

                try {
                    switch (el.getType()) {
                        case ELECTRICITY_ONE -> {
                            if (parts.length != 1) {
                                sendText(chatId, "Нужно одно число. Попробуйте ещё раз.");
                                return;
                            }
                            r.setTotal(new BigDecimal(parts[0]));
                        }
                        case ELECTRICITY_TWO -> {
                            if (parts.length != 2) {
                                sendText(chatId, "Нужно два числа: день и ночь.");
                                return;
                            }
                            r.setDay(new BigDecimal(parts[0]));
                            r.setNight(new BigDecimal(parts[1]));
                        }
                        case ELECTRICITY_MULTI -> {
                            if (parts.length != 3) {
                                sendText(chatId, "Нужно три числа: день, ночь, пик.");
                                return;
                            }
                            r.setDay(new BigDecimal(parts[0]));
                            r.setNight(new BigDecimal(parts[1]));
                            r.setPeak(new BigDecimal(parts[2]));
                        }
                    }
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать числа. Введите ещё раз.");
                    return;
                }

                el.setInitialReading(r);
                meterRepository.save(el);

                sendText(chatId,
                        "Готово! Квартира и счётчики настроены.\n" +
                                "Теперь можно использовать /calc или /flats для просмотра.");
                session.state = OnboardingState.NONE;
            }
        }
    }
    private Meter getElectricMeter(Long chatId, Long meterId) {
        // можно сделать метод findById в MeterRepository, но пока обойдёмся:
        // если часто нужен, лучше добавить нормальный метод
        Flat flat = flatRepository.findByChatId(chatId).get(0);
        return meterRepository.findByFlat(chatId, flat.getId()).stream()
                .filter(m -> m.getId().equals(meterId))
                .findFirst()
                .orElse(null);
    }
    private void continueCalc(Update update, CalcSession session) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        switch (session.state) {
            case ASKING_COLD -> {
                BigDecimal value;
                try {
                    value = new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать число. Введите показания холодной воды ещё раз.");
                    return;
                }
                session.coldCurrent = value;

                sendText(chatId,
                        "Теперь введите текущие показания по горячей воде (м³), одно число, например 123.45.");
                session.state = CalcState.ASKING_HOT;
            }
            case ASKING_HOT -> {
                BigDecimal value;
                try {
                    value = new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать число. Введите показания горячей воды ещё раз.");
                    return;
                }
                session.hotCurrent = value;

                // найдём электро‑счётчик и спросим показания по его типу
                Flat flat = flatRepository.findByChatId(chatId).get(0);
                List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
                Meter el = meters.stream()
                        .filter(m -> m.getType() == MeterType.ELECTRICITY_ONE
                                || m.getType() == MeterType.ELECTRICITY_TWO
                                || m.getType() == MeterType.ELECTRICITY_MULTI)
                        .findFirst()
                        .orElse(null);

                if (el == null) {
                    sendText(chatId, "Не найден электросчётчик. Расчёт только по воде.");
                    // сразу считаем по воде
                    finishCalc(chatId, session, flat, null);
                    session.state = CalcState.NONE;
                    return;
                }

                if (el.getType() == MeterType.ELECTRICITY_ONE) {
                    sendText(chatId,
                            "Введите текущие показания электросчётчика (одно число, например 1234.5).");
                } else if (el.getType() == MeterType.ELECTRICITY_TWO) {
                    sendText(chatId,
                            "Введите два числа через пробел: день и ночь.\nНапример: 1234.5 678.9");
                } else {
                    sendText(chatId,
                            "Введите три числа через пробел: день, ночь, пик.\nНапример: 1234.5 678.9 12.3");
                }
                session.state = CalcState.ASKING_ELECTRIC;
            }
            case ASKING_ELECTRIC -> {
                Flat flat = flatRepository.findByChatId(chatId).get(0);
                List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());
                Meter el = meters.stream()
                        .filter(m -> m.getType() == MeterType.ELECTRICITY_ONE
                                || m.getType() == MeterType.ELECTRICITY_TWO
                                || m.getType() == MeterType.ELECTRICITY_MULTI)
                        .findFirst()
                        .orElse(null);

                if (el == null) {
                    sendText(chatId, "Не найден электросчётчик. Расчёт только по воде.");
                    finishCalc(chatId, session, flat, null);
                    session.state = CalcState.NONE;
                    return;
                }

                String[] parts = text.replace(',', '.').trim().split("\\s+");
                InitialReading current = new InitialReading();

                try {
                    switch (el.getType()) {
                        case ELECTRICITY_ONE -> {
                            if (parts.length != 1) {
                                sendText(chatId, "Нужно одно число. Попробуйте ещё раз.");
                                return;
                            }
                            current.setTotal(new BigDecimal(parts[0]));
                        }
                        case ELECTRICITY_TWO -> {
                            if (parts.length != 2) {
                                sendText(chatId, "Нужно два числа: день и ночь.");
                                return;
                            }
                            current.setDay(new BigDecimal(parts[0]));
                            current.setNight(new BigDecimal(parts[1]));
                        }
                        case ELECTRICITY_MULTI -> {
                            if (parts.length != 3) {
                                sendText(chatId, "Нужно три числа: день, ночь, пик.");
                                return;
                            }
                            current.setDay(new BigDecimal(parts[0]));
                            current.setNight(new BigDecimal(parts[1]));
                            current.setPeak(new BigDecimal(parts[2]));
                        }
                    }
                } catch (NumberFormatException e) {
                    sendText(chatId, "Не удалось распознать числа. Введите ещё раз.");
                    return;
                }

                session.electricCurrent = current;

                finishCalc(chatId, session, flat, el);
                session.state = CalcState.NONE;
            }
        }
    }
    private void finishCalc(Long chatId, CalcSession session, Flat flat, Meter electric) {
        List<Meter> meters = meterRepository.findByFlat(chatId, flat.getId());

        Meter cold = meters.stream()
                .filter(m -> m.getType() == MeterType.WATER_COLD)
                .findFirst().orElse(null);
        Meter hot = meters.stream()
                .filter(m -> m.getType() == MeterType.WATER_HOT)
                .findFirst().orElse(null);

        BigDecimal coldStart = cold != null && cold.getInitialReading() != null
                ? cold.getInitialReading().getTotal() : BigDecimal.ZERO;
        BigDecimal hotStart = hot != null && hot.getInitialReading() != null
                ? hot.getInitialReading().getTotal() : BigDecimal.ZERO;

        BigDecimal coldUsage = session.coldCurrent.subtract(coldStart);
        BigDecimal hotUsage = session.hotCurrent.subtract(hotStart);

        BigDecimal sewerUsage = coldUsage.add(hotUsage); // водоотведение

        // Тарифы: здесь псевдо‑код, ты знаешь свою TariffService лучше
        BigDecimal coldTariff = tariffService.getColdWaterTariff(flat);     // м³
        BigDecimal hotTariff = tariffService.getHotWaterTariff(flat);       // м³ (вода + тепло)
        BigDecimal sewerTariff = tariffService.getSewerTariff(flat);        // м³
        BigDecimal elDayTariff = null;
        BigDecimal elNightTariff = null;
        BigDecimal elPeakTariff = null;

        InitialReading elStart = electric != null ? electric.getInitialReading() : null;
        BigDecimal elCost = BigDecimal.ZERO;

        StringBuilder report = new StringBuilder();
        report.append("Расчёт по квартире \"").append(flat.getName()).append("\":\n\n");

        // Холодная
        BigDecimal coldCost = coldUsage.multiply(coldTariff);
        report.append("Холодная вода: расход ")
                .append(coldUsage).append(" м³ × ")
                .append(coldTariff).append(" ₽ = ")
                .append(coldCost).append(" ₽\n");

        // Горячая
        BigDecimal hotCost = hotUsage.multiply(hotTariff);
        report.append("Горячая вода: расход ")
                .append(hotUsage).append(" м³ × ")
                .append(hotTariff).append(" ₽ = ")
                .append(hotCost).append(" ₽\n");

        // Водоотведение
        BigDecimal sewerCost = sewerUsage.multiply(sewerTariff);
        report.append("Водоотведение: расход ")
                .append(sewerUsage).append(" м³ × ")
                .append(sewerTariff).append(" ₽ = ")
                .append(sewerCost).append(" ₽\n");

        // Электричество
        if (electric != null && elStart != null && session.electricCurrent != null) {
            switch (electric.getType()) {
                case ELECTRICITY_ONE -> {
                    BigDecimal start = elStart.getTotal() != null ? elStart.getTotal() : BigDecimal.ZERO;
                    BigDecimal usage = session.electricCurrent.getTotal().subtract(start);
                    BigDecimal t = tariffService.getElectricSingleTariff(flat, electric);
                    elCost = usage.multiply(t);
                    report.append("Электроэнергия (1‑тариф): расход ")
                            .append(usage).append(" кВт·ч × ")
                            .append(t).append(" ₽ = ")
                            .append(elCost).append(" ₽\n");
                }
                case ELECTRICITY_TWO -> {
                    BigDecimal startDay = elStart.getDay() != null ? elStart.getDay() : BigDecimal.ZERO;
                    BigDecimal startNight = elStart.getNight() != null ? elStart.getNight() : BigDecimal.ZERO;
                    BigDecimal usageDay = session.electricCurrent.getDay().subtract(startDay);
                    BigDecimal usageNight = session.electricCurrent.getNight().subtract(startNight);

                    BigDecimal tDay = tariffService.getElectricDayTariff(flat, electric);
                    BigDecimal tNight = tariffService.getElectricNightTariff(flat, electric);

                    BigDecimal costDay = usageDay.multiply(tDay);
                    BigDecimal costNight = usageNight.multiply(tNight);
                    elCost = costDay.add(costNight);

                    report.append("Электроэнергия (2‑тариф):\n")
                            .append("  День: ").append(usageDay).append(" × ").append(tDay)
                            .append(" ₽ = ").append(costDay).append(" ₽\n")
                            .append("  Ночь: ").append(usageNight).append(" × ").append(tNight)
                            .append(" ₽ = ").append(costNight).append(" ₽\n");
                }
                case ELECTRICITY_MULTI -> {
                    BigDecimal startDay = elStart.getDay() != null ? elStart.getDay() : BigDecimal.ZERO;
                    BigDecimal startNight = elStart.getNight() != null ? elStart.getNight() : BigDecimal.ZERO;
                    BigDecimal startPeak = elStart.getPeak() != null ? elStart.getPeak() : BigDecimal.ZERO;

                    BigDecimal usageDay = session.electricCurrent.getDay().subtract(startDay);
                    BigDecimal usageNight = session.electricCurrent.getNight().subtract(startNight);
                    BigDecimal usagePeak = session.electricCurrent.getPeak().subtract(startPeak);

                    BigDecimal tDay = tariffService.getElectricDayTariff(flat, electric);
                    BigDecimal tNight = tariffService.getElectricNightTariff(flat, electric);
                    BigDecimal tPeak = tariffService.getElectricPeakTariff(flat, electric);

                    BigDecimal costDay = usageDay.multiply(tDay);
                    BigDecimal costNight = usageNight.multiply(tNight);
                    BigDecimal costPeak = usagePeak.multiply(tPeak);

                    elCost = costDay.add(costNight).add(costPeak);

                    report.append("Электроэнергия (3‑тариф):\n")
                            .append("  День: ").append(usageDay).append(" × ").append(tDay)
                            .append(" ₽ = ").append(costDay).append(" ₽\n")
                            .append("  Ночь: ").append(usageNight).append(" × ").append(tNight)
                            .append(" ₽ = ").append(costNight).append(" ₽\n")
                            .append("  Пик: ").append(usagePeak).append(" × ").append(tPeak)
                            .append(" ₽ = ").append(costPeak).append(" ₽\n");
                }
            }
        }

        BigDecimal total = coldCost.add(hotCost).add(sewerCost).add(elCost);

        report.append("\nИтого к оплате: ").append(total).append(" ₽");

        // 1) Обновляем начальные показания воды
        if (cold != null) {
            InitialReading r = cold.getInitialReading();
            if (r == null) r = new InitialReading();
            r.setTotal(session.coldCurrent);
            cold.setInitialReading(r);
            meterRepository.save(cold);
        }

        if (hot != null) {
            InitialReading r = hot.getInitialReading();
            if (r == null) r = new InitialReading();
            r.setTotal(session.hotCurrent);
            hot.setInitialReading(r);
            meterRepository.save(hot);
        }

        // 2) Обновляем начальные показания электричества
        if (electric != null && session.electricCurrent != null) {
            electric.setInitialReading(session.electricCurrent);
            meterRepository.save(electric);
        }

        sendText(chatId, report.toString());
    }

}