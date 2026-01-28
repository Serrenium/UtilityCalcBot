package ru.UtilityCalcPk.tariff;
import java.math.BigDecimal;
import java.time.LocalDate;

public class Tariff {
    private Long id;                 // пригодится, когда будет БД
    private ServiceType service;     // тип услуги
    private String region;           // "город Москва"
    private String provider;         // поставщик (Agency)
    private String unit;             // "руб/куб.м", "руб/кВт.ч" и т.п.
    private boolean byMeter;         // true — по счётчику, false — без
    private LocalDate startDate;     // StartDate
    private LocalDate endDate;       // EndDate
    private BigDecimal value;        // TariffValue

    public Tariff() {
    }

    public Tariff(ServiceType service,
                  String region,
                  String provider,
                  String unit,
                  boolean byMeter,
                  LocalDate startDate,
                  LocalDate endDate,
                  BigDecimal value) {
        this.service = service;
        this.region = region;
        this.provider = provider;
        this.unit = unit;
        this.byMeter = byMeter;
        this.startDate = startDate;
        this.endDate = endDate;
        this.value = value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ServiceType getService() { return service; }
    public void setService(ServiceType service) { this.service = service; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public boolean isByMeter() { return byMeter; }
    public void setByMeter(boolean byMeter) { this.byMeter = byMeter; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}
