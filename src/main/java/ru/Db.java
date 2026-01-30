package ru;

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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}

