package ru.UtilityCalcPk.tariff;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.UtilityCalcPk.MosRu.MosRuCells64058;
import ru.UtilityCalcPk.MosRu.MosRuRow64058;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MosRuTariffLoader {

    private static final String API_URL =
            "https://apidata.mos.ru/v1/datasets/64058/rows";
    private static final String API_KEY =
            "91975004-5468-4443-a68d-b59c283c6983";

    public static void load(TariffRepository repo) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        String url = API_URL
                + "?$top=" + 1000
                + "&$skip=" + 0
                + "&api_key=" + API_KEY;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("Ошибка загрузки mos.ru: " + response.statusCode());
            System.out.println(response.body());
            return;
        }

        String body = response.body();

        ObjectMapper mapper = new ObjectMapper();
        MosRuRow64058[] rows = mapper.readValue(body, MosRuRow64058[].class);

        List<MosRuCells64058> electricityRows = new ArrayList<>();
        List<MosRuCells64058> otherRows = new ArrayList<>();

        for (MosRuRow64058 row : rows) {
            MosRuCells64058 c = row.getCells();
            if (c == null) continue;

            ServiceType service = TariffMapper.mapService(c.getTariffItem());
            if (service == null) continue;

            // только Москва
            if (!c.getRegion().contains("город Москва")) {
                continue;
            }

            // фильтр на Организации
            if (!AgencyFilter.isAllowed(c)) {
                continue;
            }

            if (service == ServiceType.ELECTRICITY) {
                // фильтр "на бытовые нужды"
                String target = c.getConsumptionTarget();
                if (target == null
                        || !target.trim().equalsIgnoreCase("на бытовые нужды")) {
                    continue;
                }
                electricityRows.add(c);
            } else {
                otherRows.add(c);
            }
        }

        // вода / водоотведение
        LocalDate today = LocalDate.now();
        int waterCount = 0;

        for (MosRuCells64058 c : otherRows) {
            Tariff t = TariffMapper.toTariff(c);
            if (t == null) continue;
            if (!TariffMapper.isActiveOnDate(t, today)) continue;

            repo.saveTariff(t);
            waterCount++;
        }

        // электро планы
        int elecCount = 0;
        var plans = ElectricityPlanBuilder.buildPlans(electricityRows);
        for (ElectricityPlan p : plans) {
            // фильтр по дате
            LocalDate start = p.getStartDate();
            LocalDate end = p.getEndDate();
            if (start != null && today.isBefore(start)) continue;
            if (end != null && today.isAfter(end)) continue;

            repo.saveElectricityPlan(p);
            elecCount++;
        }

        System.out.println("MosRuTariffLoader: загружено тарифов воды/водоотведения = " + waterCount);
        System.out.println("MosRuTariffLoader: загружено электро планов = " + elecCount);
    }
}
