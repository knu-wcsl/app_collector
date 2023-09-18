package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BLEModule {
    private String TAG = "BLE_MODULE";
    private static final int APPLE = 0X004C;
    public static final int REQUEST_ENABLE_BT = 10;
    private boolean flag_is_ble_running = false;
    private long measurement_start_time_ms;
    private long last_scan_time_ms;
    private int count;
    private int count_iBeacon;
    private int count_Eddystone;
    private int count_other;
    private String curr_ble_value = "";
    private String last_ble_value = "";
    private Activity mActivity;
    private MeasurementListener mListener;
    private FileModule file;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ScanResult curr_ble_result;
    private ScanResult prev_ble_result;
    private BluetoothLeScanner bleScanner;
    private BeaconManager beaconManager;
    private ScanCallback scanCallback;
    public BLEModule(Activity activity, MeasurementListener listener) {
        mActivity = activity;
        mListener = listener;
        bluetoothManager = (BluetoothManager) mActivity.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
//                if (curr_ble_result != prev_ble_result)
                count++;
                super.onScanResult(callbackType, result);
                float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    // check is iBeacon
                    byte[] manufacture_data_apple =  scanRecord.getManufacturerSpecificData(APPLE);
                    if (manufacture_data_apple != null && manufacture_data_apple[0] == 0X0002) {
                        count_iBeacon++;
                        String ble_type = Beacon.TYPE_IBEACON;
                        String mac_addr = result.getDevice().getAddress();
                        int rssi = result.getRssi();
                        int tx_power = scanRecord.getTxPowerLevel();
                        byte[] arr_rssi_1m = Arrays.copyOfRange(manufacture_data_apple,22,23);
                        byte[] arr_uuid = Arrays.copyOfRange(manufacture_data_apple,2,18);
                        byte[] arr_major_ID = Arrays.copyOfRange(manufacture_data_apple,18,20);
                        byte[] arr_minor_ID = Arrays.copyOfRange(manufacture_data_apple,20,22);

                        UUID uuid = get_iBeacon_uuid(arr_uuid);
                        int major_ID = Integer.parseInt(bytes_to_hex_string(arr_major_ID),16);
                        int minor_ID = Integer.parseInt(bytes_to_hex_string(arr_minor_ID), 16);
                        int rssi_1m = arr_rssi_1m[0];
                        curr_ble_value = String.format("BLE4, %f, %s, %s, %d, %d, %d, %d, %d, %s", elapsed_app_time_s, ble_type, mac_addr
                                , rssi, rssi_1m, tx_power, major_ID, minor_ID, uuid);
                        beaconManager.add_new_beacon(new Beacon(ble_type,mac_addr,rssi,major_ID,minor_ID,uuid));
                        Log.d(TAG,curr_ble_value);
//                    } else if (scanRecord.getServiceUuids() != null) {
//                        List<ParcelUuid> uuidList  = scanRecord.getServiceUuids();
//                        ParcelUuid uuid = uuidList.get(0);
//                        UUID uuid2 = uuid.getUuid();
//                        byte[] serviceData = scanRecord.getServiceData(uuidList.get(0));
//                        Log.d(TAG,bytes_to_hex_string(serviceData));
                    } else {
                        count_other++;
                        String ble_type = Beacon.TYPE_OTHER;
                        String mac_addr = result.getDevice().getAddress();
                        int rssi = result.getRssi();
                        int tx_power = scanRecord.getTxPowerLevel();
                        String bytes = bytes_to_hex_string(scanRecord.getBytes());
                        curr_ble_value = String.format("BLE4, %f, %s, %s, %d, %d, %s", elapsed_app_time_s, ble_type, mac_addr
                                                        , rssi, tx_power, bytes);

                    }
                    String str_status = String.format("Count: %d\t\tiBeacon: %d\t\tother: %d",count, count_iBeacon,count_other);
                    mListener.ble_status(str_status, MeasurementListener.TYPE_BLE_STATUS);
                    mListener.ble_status(curr_ble_value + "\n", MeasurementListener.TYPE_BLE_VALUE);
                    if (file != null && !curr_ble_value.equals(last_ble_value)) {
                        file.save_str_to_file(curr_ble_value + "\n");
                        last_ble_value = curr_ble_value;
                    }
                }
            }
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }
            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };
    }

    public boolean start_measurement(long start_time_ms, FileModule _file) {
        file = _file;
        if (flag_is_ble_running) {
            Toast.makeText(mActivity.getApplicationContext(),"BLE measurement is already running", Toast.LENGTH_SHORT).show();
            return false;
        }
        measurement_start_time_ms = start_time_ms;
        count = 0;
        count_iBeacon = 0;
        count_Eddystone = 0;
        count_other = 0;
        last_ble_value = "";
        beaconManager = new BeaconManager();
        bleScanner.startScan(scanCallback);
        flag_is_ble_running = true;
        return true;
    }

    public void stop_measurement() {
        bleScanner.stopScan(scanCallback);
        flag_is_ble_running = false;
    }
    @NonNull
    private String bytes_to_hex_string(@NonNull byte[] mBytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : mBytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
    }
    private UUID get_iBeacon_uuid(byte[] mBytes) {
        String str_uuid = bytes_to_hex_string(mBytes);
        String str_iBeacon_uuid = str_uuid.substring(0, 8) + "-" + str_uuid.substring(8, 12) + "-"
                                + str_uuid.substring(12, 16) + "-" + str_uuid.substring(16, 20) + "-"
                                + str_uuid.substring(20);
        UUID uuid = UUID.fromString(str_iBeacon_uuid);
        return uuid;
    }

    public BeaconManager getBeaconManager() {
        return beaconManager;
    }
}
