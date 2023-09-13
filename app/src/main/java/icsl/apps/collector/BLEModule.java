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

import java.util.List;

public class BLEModule {
    private String TAG = "BLE_MODULE";
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
                    byte[] mBytes = scanRecord.getBytes();
                    if (mBytes != null) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mBytes) {
                            sb.append(String.format("%02X",b));
                            file.save_str_to_file(String.format("%02X",b) + " / ");
                        }
                        Log.d(TAG, sb.toString());
                        file.save_str_to_file("\n");
//                    file.save_str_to_file(sb.toString() + "\n");
                    }
//                String scanResult;
                    byte[] imanufactureData =  scanRecord.getManufacturerSpecificData(0X004C);

                    if (imanufactureData != null && imanufactureData.length > 23) {
                        count_iBeacon++;
                        last_ble_value = "BLE4;" + elapsed_app_time_s + ";" + result.getDevice().getAddress() + "; "
                                + "iBeacon;" + result.getDevice().getName() + "; " + result.getRssi() + ";" + imanufactureData.toString() + ";";
//                    Log.d(TAG, scanResult);
//                    if (result.getDevice().getType() == 0X02)
//                        Log.d(TAG, result.getDevice().getType() + "");
//                    scanRec

                    }
//                Log.d(TAG,result.toString());
//                Log.d(TAG,scanRecord.)
                    if (scanRecord.getServiceUuids() != null) {
                        String uuids = "";
                        for (int i=0; i<scanRecord.getServiceUuids().size(); i++) {
                            uuids += scanRecord.getServiceUuids().get(i).toString() + " / ";
                        }
                        if (scanRecord.getServiceUuids().get(0).toString().equals("0000FEAA-0000–1000–8000–00805F9B34FB"))
                            count_Eddystone++;
//                    Log.d(TAG,uuids);
                        uuids = scanRecord.getServiceUuids().get(0).toString();
                        last_ble_value += uuids + ";";
                    }
                    last_ble_value += "\n";
                    String str_status = String.format("Count: %d\t\tiBeacon: %d\t\tEddyStone: %d",count, count_iBeacon, count_Eddystone);
                    mListener.ble_status(str_status, MeasurementListener.TYPE_BLE_STATUS);
                    mListener.ble_status(scanRecord + result.getDevice().getAddress() + "\n", MeasurementListener.TYPE_BLE_VALUE);
                    if (file != null) {
//                    file.save_str_to_file(last_ble_value + "\n");
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
}
