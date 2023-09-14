package icsl.apps.collector;

import java.util.ArrayList;
import java.util.UUID;

public class BeaconManager {
    private ArrayList<Beacon> beaconList;

    public BeaconManager() {
        beaconList = new ArrayList<>();
    }

    public ArrayList<Beacon> getBeaconList() {
        return beaconList;
    }
    public boolean add_new_beacon(Beacon beacon) {
        if (beaconList.contains(beacon)) {
            int idx = beaconList.indexOf(beacon);
            beaconList.get(idx).setRssi(beacon.getRssi());
            return false;
        } else {
            beaconList.add(beacon);
            return true;
        }
    }

}
