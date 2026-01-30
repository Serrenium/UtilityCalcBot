package ru.UtilityCalcPk.meter;

import ru.Db;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class MeterRepository {

    public void save(Meter meter) {
        InitialReading r = meter.getInitialReading();

        if (meter.getId() == null) {
            String sql = """
                INSERT INTO meters(
                  chat_id, flat_id, type, provider_short, stove_type,
                  initial_total, initial_day, initial_night, initial_peak
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection c = Db.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setLong(1, meter.getChatId());
                ps.setLong(2, meter.getFlatId());
                ps.setString(3, meter.getType().name());
                ps.setString(4, meter.getProviderShort());
                ps.setString(5, meter.getStoveType());
                ps.setBigDecimal(6, r != null ? r.getTotal() : null);
                ps.setBigDecimal(7, r != null ? r.getDay() : null);
                ps.setBigDecimal(8, r != null ? r.getNight() : null);
                ps.setBigDecimal(9, r != null ? r.getPeak() : null);

                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        meter.setId(rs.getLong(1));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            String sql = """
                UPDATE meters SET
                  flat_id = ?, type = ?, provider_short = ?, stove_type = ?,
                  initial_total = ?, initial_day = ?, initial_night = ?, initial_peak = ?
                WHERE id = ? AND chat_id = ?
                """;
            try (Connection c = Db.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setLong(1, meter.getFlatId());
                ps.setString(2, meter.getType().name());
                ps.setString(3, meter.getProviderShort());
                ps.setString(4, meter.getStoveType());
                ps.setBigDecimal(5, r != null ? r.getTotal() : null);
                ps.setBigDecimal(6, r != null ? r.getDay() : null);
                ps.setBigDecimal(7, r != null ? r.getNight() : null);
                ps.setBigDecimal(8, r != null ? r.getPeak() : null);
                ps.setLong(9, meter.getId());
                ps.setLong(10, meter.getChatId());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public List<Meter> findByFlat(Long chatId, Long flatId) {
        String sql = """
            SELECT id, chat_id, flat_id, type, provider_short, stove_type,
                   initial_total, initial_day, initial_night, initial_peak
            FROM meters
            WHERE chat_id = ? AND flat_id = ?
            """;
        List<Meter> result = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            ps.setLong(2, flatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Meter m = new Meter();
                    m.setId(rs.getLong("id"));
                    m.setChatId(rs.getLong("chat_id"));
                    m.setFlatId(rs.getLong("flat_id"));
                    m.setType(MeterType.valueOf(rs.getString("type")));
                    m.setProviderShort(rs.getString("provider_short"));
                    m.setStoveType(rs.getString("stove_type"));

                    InitialReading r = new InitialReading();
                    r.setTotal(getDecimal(rs, "initial_total"));
                    r.setDay(getDecimal(rs, "initial_day"));
                    r.setNight(getDecimal(rs, "initial_night"));
                    r.setPeak(getDecimal(rs, "initial_peak"));
                    m.setInitialReading(r);

                    result.add(m);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public void deleteById(Long chatId, Long meterId) {
        String sql = "DELETE FROM meters WHERE id = ? AND chat_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, meterId);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByFlat(Long chatId, Long flatId) {
        String sql = "DELETE FROM meters WHERE chat_id = ? AND flat_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            ps.setLong(2, flatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal getDecimal(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        // SQLite часто отдаёт Integer/Long/Double — конвертим через строку
        return new BigDecimal(String.valueOf(value));
    }

}


