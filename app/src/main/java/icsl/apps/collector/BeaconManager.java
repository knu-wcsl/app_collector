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

}
