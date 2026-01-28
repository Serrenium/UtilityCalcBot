import com.fasterxml.jackson.databind.ObjectMapper;
import ru.UtilityCalcPk.MosRu.MosRuRow64058;
import ru.UtilityCalcPk.MosRu.MosRuCells64058;
import ru.UtilityCalcPk.tariff.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MosRuTestMain {

    private static final String API_URL =
            "https://apidata.mos.ru/v1/datasets/64058/rows";
    private static final String API_KEY =
            "91975004-5468-4443-a68d-b59c283c6983";

    public static void main(String[] args) throws IOException, InterruptedException {
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
            System.out.println("Ошибка: " + response.statusCode());
            System.out.println(response.body());
            return;
        }

        String body = response.body();

        ObjectMapper mapper = new ObjectMapper();
        MosRuRow64058[] rows = mapper.readValue(body, MosRuRow64058[].class);

        LocalDate today = LocalDate.now();

        List<MosRuCells64058> electricityRows = new ArrayList<>();
        List<MosRuCells64058> otherRows = new ArrayList<>();

        // 1. Разбираем строки и фильтруем базовые условия
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
                // фильтр "на бытовые нужды" только для электроэнергии
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

        // 2. Вода / водоотведение — как раньше через TariffMapper
        System.out.println("=== WATER / SEWERAGE TARIFFS ===");
        for (MosRuCells64058 c : otherRows) {
            Tariff tariff = TariffMapper.toTariff(c);
            if (tariff == null) continue;

            if (!TariffMapper.isActiveOnDate(tariff, today)) {
                continue;
            }

            System.out.println(
                    tariff.getService() + " | " +
//                            tariff.getRegion() + " | " +
                            tariff.getProvider() + " | " +
                            tariff.getValue()
            );
        }

        // 3. Электрические тарифные планы (одно/двух/многотарифные)
        System.out.println("=== ELECTRICITY PLANS ===");
        List<ElectricityPlan> plans = ElectricityPlanBuilder.buildPlans(electricityRows);

        for (ElectricityPlan p : plans) {

            if (p.getStoveType().equals("газовая")
                    && p.getType() == ElectricityPlanType.ONE_TARIFF )
                // здесь пока просто вывод, дальше можно завернуть в репозиторий/сервис
                System.out.println(
                        p.getType() + " | " +
                                p.getStoveType() + " | " +
    //                            p.getRegion() + " | " +
                                p.getProvider() + " | " +
                                p.getEndDate() + " | " +
                                "day=" + p.getDayTariff() +
                                ", night=" + p.getNightTariff() +
                                ", peak=" + p.getPeakTariff()
                );
        }
    }
}
