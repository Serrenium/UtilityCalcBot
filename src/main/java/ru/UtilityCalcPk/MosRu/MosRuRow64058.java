package ru.UtilityCalcPk.MosRu;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MosRuRow64058 {
    @JsonProperty("global_id")
    private long globalId;

    @JsonProperty("Number")
    private int number;

    @JsonProperty("Cells")
    private MosRuCells64058 cells;

    public long getGlobalId() { return globalId; }
    public void setGlobalId(long globalId) { this.globalId = globalId; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public MosRuCells64058 getCells() { return cells; }
    public void setCells(MosRuCells64058 cells) { this.cells = cells; }
}
