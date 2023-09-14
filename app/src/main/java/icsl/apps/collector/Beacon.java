package icsl.apps.collector;

import java.util.UUID;

public class Beacon {
    private String beacon_type;     // iBeacon, Eddystone, etc
    private String mac_addr;
    private int rssi;
    private int major_ID;
    private int minor_ID;
    private UUID uuid;

    public Beacon(String beacon_type, String mac_addr, int rssi, int major_ID, int minor_ID, UUID uuid) {
        this.beacon_type = beacon_type;
        this.mac_addr = mac_addr;
        this.rssi = rssi;
        this.major_ID = major_ID;
        this.minor_ID = minor_ID;
        this.uuid = uuid;
    }

    public String getBeacon_type() {
        return beacon_type;
    }

    public String getMac_addr() {
        return mac_addr;
    }

    public int getRssi() {
        return rssi;
    }

    public int getMajor_ID() {
        return major_ID;
    }

    public int getMinor_ID() {
        return minor_ID;
    }

    public UUID getUuid() {
        return uuid;
    }
}
