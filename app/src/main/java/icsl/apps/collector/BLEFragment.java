package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

public class BLEFragment extends Fragment implements MeasurementListener{
    private String TAG = "BLE_FRAGMENT";

    // Status
    public boolean flag_measurement_running = false;
    public boolean flag_save_file = false;
    private long measurement_start_time_ms = 0;
    private String str_setting;
    private String str_status;
    private String str_value;

    // Layout
    private TextView tv1;
    private TextView tv2;
    private Button btn;
    private Switch sw;
    private ListView lv;
    // Ble module
    private BLEModule bleModule;
    private BeaconManager beaconManager;
    private FileModule file;
    public BLEFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ble, container, false);
        tv1 = v.findViewById(R.id.tv_ble1);
        tv1.setText("[BLE test]\nSensor data will not be stored in a file");
        tv2 = v.findViewById(R.id.tv_ble2);
        tv2.setText("[BLE measurement]");

        btn = v.findViewById(R.id.btn_ble);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (flag_measurement_running)
                    stop();
                else
                    start();
            }
        });
        sw = v.findViewById(R.id.sw_ble);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    flag_save_file = true;
                    tv1.setText("[BLE test]\nSensor data will be stored in a file");
                }
                else {
                    flag_save_file = false;
                    tv1.setText("[BLE test]\nSensor data will not be stored in a file");
                }
            }
        });
        bleModule = new BLEModule(getActivity(), this);
        lv = v.findViewById(R.id.lv_ble);

        return v;
    }

    private void start() {
        measurement_start_time_ms = elapsedRealtime();
        if (flag_save_file) {
            file = new FileModule(getActivity(), "BLE",true,true,".txt");
        }
        else
            file = null;
        if (bleModule.start_measurement(measurement_start_time_ms, file)) {  // Success to start measurement
            btn.setText("Stop");
            flag_measurement_running = true;
        }
    }

    private void stop() {
        btn.setText("Start");
//        tv.setText(str_setting);
        bleModule.stop_measurement();
        flag_measurement_running = false;
    }

    @Override
    public void log_msg(String log) {

    }

    @Override
    public void sensor_status(String status, int type) {

    }

    @Override
    public void wifi_status(String status, int type) {

    }

    @Override
    public void server_status(String status, int type) {

    }

    @Override
    public void ble_status(String status, int type) {
        String str_out = "";
        if (type == MeasurementListener.TYPE_INIT_SUCCESS || type == MeasurementListener.TYPE_INIT_FAILED){
            str_setting = "[BLE test]\nMeasurement data are not saved in a file\n\n" + status;
//            tv.setText(str_setting);
        }
        else if (type == MeasurementListener.TYPE_BLE_STATUS) {
            str_status = status;
            tv2.setText("[BLE measurement]\n" + str_status);
            ArrayList<Beacon> beaconList = bleModule.getBeaconManager().getBeaconList();
            ArrayAdapter<Beacon> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,beaconList);
            lv.setAdapter(adapter);
        }
        else if (type == MeasurementListener.TYPE_BLE_VALUE){
            str_value = status;
            str_out = String.format("[BLE measurement]\n Measurement is running (elapsed time: %.2f s)", (elapsedRealtime() - measurement_start_time_ms) / 1e3);
            str_out += "\n\n" + str_status + "\n\n" + str_value;
            tv2.setText(str_out);
        }
    }
}
