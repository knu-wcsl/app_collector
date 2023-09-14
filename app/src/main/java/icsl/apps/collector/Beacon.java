package icsl.apps.collector;

import androidx.annotation.Nullable;

import java.util.UUID;

public class Beacon {
    public static final String TYPE_IBEACON = "iBeacon";
    public static final String TYPE_EDDY = "eddystone";
    public static final String TYPE_OTHER = "other";
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

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        Beacon target = (Beacon) obj;
        if (this.getMac_addr().equals(target.getMac_addr()))
            return true;
        else
            return false;
    }

    @Override
    public String toString() {
        return "Beacon{" +
                "beacon_type='" + beacon_type + '\'' +
                ", mac_addr='" + mac_addr + '\'' +
                ", rssi=" + rssi +
                ", major_ID=" + major_ID +
                ", minor_ID=" + minor_ID +
                ", uuid=" + uuid +
                '}';
    }
}
