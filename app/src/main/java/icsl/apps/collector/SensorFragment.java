package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class SensorFragment extends Fragment implements MeasurementListener {
    private String TAG = "SENSOR_FRAGMENT";

    // Status
    public boolean flag_measurement_running = false;
    private long measurement_start_time_ms = 0;
    private String str_setting;
    private String str_status;
    private String str_value;

    // Layout
    private TextView tv;
    private Button btn;

    // Sensor module
    private SensorModule sensorModule;

    public SensorFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View v = inflater.inflate(R.layout.fragment_sensor, container, false);

        tv = (TextView) v.findViewById(R.id.tv_sen);
        tv.setText("[Sensor test]\nSensor data will not be stored in a file");

        btn = (Button) v.findViewById(R.id.btn_sen);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flag_measurement_running)
                    stop();
                else
                    start();
            }
        });

        sensorModule = new SensorModule(getActivity(), this);
//        loadSettings();
//
        return v;
    }

    private void start(){
        measurement_start_time_ms = elapsedRealtime();
        if (sensorModule.start_measurement(measurement_start_time_ms, null)) {  // Success to start measurement
            btn.setText("Stop");
            flag_measurement_running = true;
        }
    }

    private void stop(){
        btn.setText("Start");
        tv.setText(str_setting);
        sensorModule.stop_measurement();
        flag_measurement_running = false;
    }

    @Override
    public void log_msg(String log) {
        // Nothing to implement
    }

    @Override
    public void wifi_status(String status, int type){
        // Nothing to implement
    }

    @Override
    public void server_status(String status, int type) {

    }

    @Override
    public void sensor_status(String status, int type){
        String str_out = "";
        if (type == MeasurementListener.TYPE_INIT_SUCCESS || type == MeasurementListener.TYPE_INIT_FAILED){
            str_setting = "[Sensor test]\nMeasurement data are not saved in a file\n\n" + status;
            tv.setText(str_setting);
        }
        else if (type == MeasurementListener.TYPE_SENSOR_STATUS)
            str_status = status;
        else if (type == MeasurementListener.TYPE_SENSOR_VALUE){
            str_value = status;
            str_out = String.format("[Sensor test]\n Measurement is running (elapsed time: %.2f s)", (elapsedRealtime() - measurement_start_time_ms) / 1e3);
            str_out += "\n\n" + str_status + "\n\n" + str_value;
            tv.setText(str_out);
        }
    }
}