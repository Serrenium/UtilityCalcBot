package ru.UtilityCalcPk.tariff;

import ru.Db;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TariffRepository {

    public void saveTariff(Tariff t) {
        String sql = """
            INSERT INTO tariffs(
              service, provider_short, value, unit, start_date, end_date
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, t.getService().name());
            ps.setString(2, t.getProviderShort());
            ps.setBigDecimal(3, t.getValue());
            ps.setString(4, t.getUnit());
            ps.setString(5, t.getStartDate() != null ? t.getStartDate().toString() : null);
            ps.setString(6, t.getEndDate() != null ? t.getEndDate().toString() : null);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveElectricityPlan(ElectricityPlan p) {
        String sql = """
            INSERT INTO electricity_plans(
              provider_short, stove_type, type,
              start_date, end_date,
              day_tariff, night_tariff, peak_tariff
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, p.getProviderShort());
            ps.setString(2, p.getStoveType());
            ps.setString(3, p.getType().name());
            ps.setString(4, p.getStartDate() != null ? p.getStartDate().toString() : null);
            ps.setString(5, p.getEndDate() != null ? p.getEndDate().toString() : null);
            ps.setBigDecimal(6, p.getDayTariff());
            ps.setBigDecimal(7, p.getNightTariff());
            ps.setBigDecimal(8, p.getPeakTariff());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Tariff> findActiveByDate(LocalDate date) {
        String sql = """
            SELECT service, provider_short, value, unit, start_date, end_date
            FROM tariffs
            """;
        List<Tariff> result = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Tariff t = new Tariff();
                t.setService(ServiceType.valueOf(rs.getString("service")));
                t.setProviderShort(rs.getString("provider_short"));
                t.setValue(rs.getBigDecimal("value"));
                t.setUnit(rs.getString("unit"));

                String s = rs.getString("start_date");
                String e = rs.getString("end_date");
                if (s != null) t.setStartDate(LocalDate.parse(s));
                if (e != null) t.setEndDate(LocalDate.parse(e));

                if (TariffMapper.isActiveOnDate(t, date)) {
                    result.add(t);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    public List<Tariff> listTariffs() {
        String sql = """
            SELECT service, provider_short, value, unit, start_date, end_date
            FROM tariffs
            """;
        List<Tariff> result = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Tariff t = new Tariff();
                t.setService(ServiceType.valueOf(rs.getString("service")));
                t.setProviderShort(rs.getString("provider_short"));
                t.setValue(rs.getBigDecimal("value"));
                t.setUnit(rs.getString("unit"));

                String s = rs.getString("start_date");
                String e = rs.getString("end_date");
                if (s != null) t.setStartDate(LocalDate.parse(s));
                if (e != null) t.setEndDate(LocalDate.parse(e));

                result.add(t);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    public List<ElectricityPlan> findActiveElectricityPlans(LocalDate date) {
        String sql = """
            SELECT provider_short, stove_type, type,
                   start_date, end_date,
                   day_tariff, night_tariff, peak_tariff
            FROM electricity_plans
            """;
        List<ElectricityPlan> result = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ElectricityPlan p = new ElectricityPlan();
                p.setProviderShort(rs.getString("provider_short"));
                p.setStoveType(rs.getString("stove_type"));
                p.setType(ElectricityPlanType.valueOf(rs.getString("type")));

                String s = rs.getString("start_date");
                String e = rs.getString("end_date");
                if (s != null) p.setStartDate(LocalDate.parse(s));
                if (e != null) p.setEndDate(LocalDate.parse(e));

                p.setDayTariff(rs.getBigDecimal("day_tariff"));
                p.setNightTariff(rs.getBigDecimal("night_tariff"));
                p.setPeakTariff(rs.getBigDecimal("peak_tariff"));

                LocalDate start = p.getStartDate();
                LocalDate end = p.getEndDate();
                if (start != null && date.isBefore(start)) continue;
                if (end != null && date.isAfter(end)) continue;

                result.add(p);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    // --- учёт даты последнего обновления тарифов ---

    public LocalDate getLastUpdateDate() {
        String sql = "SELECT last_update_date FROM tariff_updates WHERE id = 1";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String s = rs.getString(1);
                return s != null ? LocalDate.parse(s) : null;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setLastUpdateDate(LocalDate date) {
        String sql = "INSERT OR REPLACE INTO tariff_updates(id, last_update_date) VALUES (1, ?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, date != null ? date.toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public void deleteAllTariffs() {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tariffs")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAllElectricityPlans() {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM electricity_plans")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
