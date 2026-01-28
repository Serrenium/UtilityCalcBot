package ru;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
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
        ENTERING_INITIAL_READING
    }

    class AddMeterSession {
        AddMeterState state = AddMeterState.NONE;
        Long selectedFlatId;
        MeterType tempMeterType;
    }

    private final TariffService tariffService;
    private final FlatRepository flatRepository;
    private final Map<Long, AddMeterSession> addMeterSessions = new HashMap<>();
    private final MeterRepository meterRepository;

    public UtilityCalc(TariffService tariffService,
                       FlatRepository flatRepository,
                       MeterRepository meterRepository) {
        this.tariffService = tariffService;
        this.flatRepository = flatRepository;
        this.meterRepository = meterRepository;
    }

    @Override
    public String getBotUsername() {
        return "Utility_calc_bot";
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

        String text = update.getMessage().getText();

        // если есть активная сессия добавления счётчика — продолжаем её
        Long chatIdLong = update.getMessage().getChatId();
        AddMeterSession session = addMeterSessions.get(chatIdLong);
        if (session != null && session.state != AddMeterState.NONE) {
            continueAddMeter(update, session);
            return;
        }

        // иначе обычные команды
        String chatId = chatIdLong.toString();
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);

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

        } else if (text.matches("\\d+[,]\\d+[,]\\d+")) {
            String[] parts = text.split(",");
            double hot = Double.parseDouble(parts[0]);
            double cold = Double.parseDouble(parts[1]);
            double power = Double.parseDouble(parts[2]);

            double total = hot * 40 + cold * 30 + power * 5.5;
            msg.setText(String.format("Итого: %.2f ₽", total));

        } else if (text.equals("/addflat")) {
            Flat flat = new Flat();
            flat.setChatId(chatIdLong);
            flat.setName("Моя квартира");
            flat.setProviderShort("Мосэнергосбыт");
            flat.setStoveType("газовая плита");
            flatRepository.save(flat);

            msg.setText("Квартира добавлена: " + flat.getName());

        } else if (text.equals("/addmeter")) {
            handleAddMeterCommand(update);
            return;
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

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace(); // пока можно так, потом залогируешь нормально
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
                    "У вас пока нет квартир. Сначала добавьте квартиру командой /addflat.");
            session.state = AddMeterState.NONE;
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
    private void handleTextMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // команды /start, /addflat, /calc и т.д.

        AddMeterSession session = addMeterSessions.get(chatId);
        if (session != null && session.state != AddMeterState.NONE) {
            continueAddMeter(update, session);
            return;
        }

        // остальной free‑text
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

                sendText(chatId,
                        "Введите начальные показания счётчика (число, например 123.45).");
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
                meter.setInitialReading(r);

                meterRepository.save(meter);

                sendText(chatId, "Счётчик добавлен ✅");
                session.state = AddMeterState.NONE;
                session.selectedFlatId = null;
                session.tempMeterType = null;
            }

        }
    }

}