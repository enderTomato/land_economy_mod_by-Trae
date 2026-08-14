package cn.autoforged.land_economy_mod_1783600667.data;

import java.util.HashMap;
import java.util.Map;

public enum RegionType {
    WASTELAND("荒地", 1, 1),
    VILLAGE("乡村", 50, 3),
    TOWNSHIP("乡镇", 200, 5),
    TOWN("城镇", 700, 8),
    CITY("都市", 1000, 10);

    private static final Map<String, double[]> OVERRIDES = new HashMap<>();

    private final String displayName;
    private final double minGdp;
    private final int minPopulation;

    RegionType(String displayName, double minGdp, int minPopulation) {
        this.displayName = displayName;
        this.minGdp = minGdp;
        this.minPopulation = minPopulation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMinGdp() {
        double[] override = OVERRIDES.get(name());
        return override != null ? override[0] : minGdp;
    }

    public int getMinPopulation() {
        double[] override = OVERRIDES.get(name());
        return override != null ? (int) override[1] : minPopulation;
    }

    public double getOriginalMinGdp() {
        return minGdp;
    }

    public int getOriginalMinPopulation() {
        return minPopulation;
    }

    public static void setOverride(String typeName, double gdp, double pop) {
        OVERRIDES.put(typeName, new double[]{gdp, pop});
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    public static Map<String, double[]> getOverrides() {
        return OVERRIDES;
    }

    public static RegionType getByGdpAndPopulation(double gdp, int population) {
        RegionType best = WASTELAND;
        for (RegionType type : values()) {
            if (gdp >= type.getMinGdp() && population >= type.getMinPopulation()) {
                if (type.ordinal() > best.ordinal()) {
                    best = type;
                }
            }
        }
        return best;
    }

    public static RegionType fromName(String name) {
        for (RegionType type : values()) {
            if (type.displayName.equals(name) || type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return WASTELAND;
    }
}
