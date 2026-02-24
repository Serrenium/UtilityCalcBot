package ru;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Db {

    private static final String URL = "jdbc:sqlite:utility_calc.db";

    static {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS flats (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  chat_id INTEGER NOT NULL,
                  name TEXT NOT NULL
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS meters (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  chat_id INTEGER NOT NULL,
                  flat_id INTEGER NOT NULL,
                  type TEXT NOT NULL,
                  provider_short TEXT,
                  stove_type TEXT,
                  initial_total NUMERIC,
                  initial_day NUMERIC,
                  initial_night NUMERIC,
                  initial_peak NUMERIC
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tariffs (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  service TEXT NOT NULL,
                  provider_short TEXT NOT NULL,
                  value NUMERIC NOT NULL,
                  unit TEXT NOT NULL,
                  start_date TEXT,
                  end_date TEXT
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS electricity_plans (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  provider_short TEXT NOT NULL,
                  stove_type TEXT NOT NULL,
                  type TEXT NOT NULL,
                  start_date TEXT,
                  end_date TEXT,
                  day_tariff NUMERIC,
                  night_tariff NUMERIC,
                  peak_tariff NUMERIC
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tariff_updates (
                  id INTEGER PRIMARY KEY CHECK (id = 1),
                  last_update_date TEXT
                )
                """);

            // 2. Импорт тарифов, если таблицы пустые
            importTariffsIfEmpty(c);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void importTariffsIfEmpty(Connection c) {
        try (Statement st = c.createStatement()) {
            // проверяем, есть ли уже тарифы
            var rs = st.executeQuery("SELECT COUNT(*) FROM tariffs");
            rs.next();
            int count = rs.getInt(1);
            rs.close();

            if (count > 0) {
                return; // уже что‑то есть — не импортируем повторно
            }

            // читаем tariff.sql из ресурсов
            try (InputStream is = Db.class.getResourceAsStream("/tariff.sql")) {
                if (is == null) {
                    // файла нет в jar — тихо выходим
                    return;
                }
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(is))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }

                    String[] statements = sb.toString().split(";");

                    for (String sql : statements) {
                        String trimmed = sql.trim();
                        if (trimmed.isEmpty()) continue;
                        st.executeUpdate(trimmed);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Ошибка чтения tariff.sql", e);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка импорта тарифов из tariff.sql", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}

