package ru.UtilityCalcPk.flat;

import ru.Db;

import java.sql.*;
import java.util.*;

public class FlatRepository {

    public List<Flat> findByChatId(Long chatId) {
        String sql = "SELECT id, chat_id, name FROM flats WHERE chat_id = ?";
        List<Flat> result = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Flat f = new Flat();
                    f.setId(rs.getLong("id"));
                    f.setChatId(rs.getLong("chat_id"));
                    f.setName(rs.getString("name"));
                    result.add(f);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public void save(Flat flat) {
        if (flat.getId() == null) {
            String sql = "INSERT INTO flats(chat_id, name) VALUES (?, ?)";
            try (Connection c = Db.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setLong(1, flat.getChatId());
                ps.setString(2, flat.getName());
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        flat.setId(rs.getLong(1));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            String sql = "UPDATE flats SET name = ? WHERE id = ? AND chat_id = ?";
            try (Connection c = Db.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setString(1, flat.getName());
                ps.setLong(2, flat.getId());
                ps.setLong(3, flat.getChatId());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean hasFlats(Long chatId) {
        String sql = "SELECT 1 FROM flats WHERE chat_id = ? LIMIT 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean existsByChatIdAndName(Long chatId, String name) {
        String sql = "SELECT 1 FROM flats WHERE chat_id = ? AND name = ? LIMIT 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(Long chatId, Long flatId) {
        String sql = "DELETE FROM flats WHERE id = ? AND chat_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, flatId);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

