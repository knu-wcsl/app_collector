package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

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
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BLEModule {
    private String TAG = "BLE_MODULE";
    private static final int APPLE = 0X004C;
    private static final int TYPE_BEACON = 0X0002;
    private boolean flag_is_ble_running = false;
    private long measurement_start_time_ms;
    private int count;
    private int count_iBeacon;
    private int count_Eddystone;
    private String last_ble_value;
    private Activity mActivity;
    private MeasurementListener mListener;
    private FileModule file;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
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
                count++;
                super.onScanResult(callbackType, result);
                float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    // check is iBeacon
                    byte[] manufacture_data_apple =  scanRecord.getManufacturerSpecificData(APPLE);
                    if (manufacture_data_apple != null && manufacture_data_apple[0] == TYPE_BEACON) {
                        count_iBeacon++;
                        byte[] arr_uuid = Arrays.copyOfRange(manufacture_data_apple,2,18);
                        byte[] arr_major_ID = Arrays.copyOfRange(manufacture_data_apple,18,20);
                        byte[] arr_minor_ID = Arrays.copyOfRange(manufacture_data_apple,20,22);

                        UUID uuid = get_iBeacon_uuid(arr_uuid);
                        int major_ID = Integer.parseInt(bytes_to_hex_string(arr_major_ID));
                        int minor_ID = Integer.parseInt(bytes_to_hex_string(arr_minor_ID));
                        last_ble_value = String.format("BLE4, %f, iBeacon, %s, %d, %d, %d, %d, %s",elapsed_app_time_s, result.getDevice().getAddress()
                                                        , result.getRssi(),scanRecord.getTxPowerLevel(),major_ID,minor_ID,uuid);
                        Log.d(TAG,scanRecord.getDeviceName() + "");
                        Log.d(TAG,result.getDevice().getName() + "");
                    }
//                    if (scanRecord.getServiceUuids() != null) {
//                        String uuids = "";
//                        for (int i=0; i<scanRecord.getServiceUuids().size(); i++) {
//                            uuids += scanRecord.getServiceUuids().get(i).toString() + " / ";
//                        }
//                        if (scanRecord.getServiceUuids().get(0).toString().equals("0000FEAA-0000–1000–8000–00805F9B34FB"))
//                            count_Eddystone++;
////                    Log.d(TAG,uuids);
//                        uuids = scanRecord.getServiceUuids().get(0).toString();
//                    }
                    String str_status = String.format("Count: %d\t\tiBeacon: %d",count, count_iBeacon);
                    mListener.ble_status(str_status, MeasurementListener.TYPE_BLE_STATUS);
                    mListener.ble_status(last_ble_value + "\n", MeasurementListener.TYPE_BLE_VALUE);
                    if (file != null) {
                        file.save_str_to_file(last_ble_value + "\n");
                    }
                }
            }
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                for (int i=0; i<results.size(); i++) {
                    Log.d(TAG, results.get(i).toString());
                }
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
        last_ble_value = "";
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
}
