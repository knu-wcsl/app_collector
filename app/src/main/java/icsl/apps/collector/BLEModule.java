package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class BLEModule {
    private String TAG = "BLE_MODULE";
    private boolean flag_is_ble_running = false;
    private long measurement_start_time_ms;
    private Activity mActivity;
    private MeasurementListener mListener;
    private FileModule file;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothAdapter.LeScanCallback leScanCallback;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;
    public BLEModule(Activity activity, MeasurementListener listener) {
        mActivity = activity;
        mListener = listener;
        bluetoothManager = (BluetoothManager) mActivity.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        leScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] bytes) {
                if (device.getName() != null || device.getName() == null) {
                    float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
//                    float elapsed_sensor_fw_time_s = (float) (event.timestamp / 1e9 - measurement_start_time_ms / 1e3);
                    String scanResult = "BLE, " + device.getName() + ", " + device.getAddress() + ", " + rssi + "\n";
                    Log.d(TAG,scanResult);
                    file.save_str_to_file(scanResult);
                }
            }
        };
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                ScanRecord scanRecord = result.getScanRecord();
                Log.d(TAG,"getTxPowerLevel: " + scanRecord.getTxPowerLevel());
                Log.d(TAG, "onScanResult: " + result.getDevice().getAddress() + ", " + result.getDevice().getName() + ", " + result.getRssi());
                Log.d(TAG, result.toString());
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
//        bluetoothAdapter.startLeScan(leScanCallback);
        bleScanner.startScan(scanCallback);

        flag_is_ble_running = true;
        return true;
    }

    public void stop_measurement() {
//        bluetoothAdapter.stopLeScan(leScanCallback);
        bleScanner.stopScan(scanCallback);
        flag_is_ble_running = false;
    }
}
