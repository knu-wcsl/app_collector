package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class HomeFragment extends Fragment implements MeasurementListener{

    // Status
    public boolean flag_measurement_running = false;

    // Measurement setting
    private String str_setting_sensor;
    private String str_setting_wifi;
    private boolean flag_collect_sensor_data = false;
    private boolean flag_collect_wifi_data = false;

    // Measurement status
    private long measurement_start_time_ms = 0;
    private String last_status_sensor = "";
    private String last_status_wifi = "";
    private long last_wifi_status_update_time_ms;

    // Layout
    private TextView tv, tv2;
    private Button btn, btn2;
    private ArrayList<String> log_str_set;
    private int max_n_log = 50;

    // Measurement module;
    private SensorModule sensorModule;
    private WifiModule wifiModule;
    private FileModule file;

    // Checkpoint
    private int check_point_counter = 0;

    public HomeFragment(){
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
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Layouts
        tv = v.findViewById(R.id.tv);         // measurement status
        tv2 = v.findViewById(R.id.tv2);       // log
        tv.setText("[Measurement]\nReady to run...");
        add_log("-------- Initialization --------", true);

        // Load modules
        wifiModule = new WifiModule(getActivity(), this);
        sensorModule = new SensorModule(getActivity(), this);

        // Button
        btn = (Button) v.findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (flag_measurement_running)
                    stop();
                else
                    start();
            }
        });

        // Button 2 (reserved for future function)
        btn2 = (Button) v.findViewById(R.id.btn2);
        btn2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (flag_measurement_running)
                    add_check_point();
                else
                    Toast.makeText(getContext(), "Checkpoint works while taking measurements", Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }

    private void start() {
        if (!flag_collect_wifi_data && !flag_collect_sensor_data) {
            Toast.makeText(getContext(), "Both sensor and WiFi measurements are disabled", Toast.LENGTH_LONG).show();
            return;
        }

        measurement_start_time_ms = elapsedRealtime();
        last_wifi_status_update_time_ms = measurement_start_time_ms;
        check_point_counter = 0;
        file = new FileModule(getActivity(), "sensor", true, true, ".txt");
        flag_measurement_running = true;
        btn.setText("Stop");

        // start all measurement
        add_log("---------- MEASUREMENT START ----------", false);
        add_log("File created: " + file.get_filename(), false);
        write_file_header();

        if (flag_collect_wifi_data)
            wifiModule.start_measurement(measurement_start_time_ms, file);
        if (flag_collect_sensor_data)
            sensorModule.start_measurement(measurement_start_time_ms, file);

        // prevent screen off during location tracking
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stop(){
        flag_measurement_running = false;
        btn.setText("Start");
        tv.setText("[Measurement]\nReady to run...");

        // stop all measurements
        sensorModule.stop_measurement();
        wifiModule.stop_measurement();
        add_log("Saved file:" + file.get_filename(), false);

        // restore screen off setting
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void write_file_header() {
        String header = "## ---------- Measurement Setup ----------\n";
        String[] str_wifi = str_setting_wifi.split("\n");
        for (int k = 0; k < str_wifi.length; k++)
            header += "## " + str_wifi[k] + "\n";
        String[] str_sensor = str_setting_sensor.split("\n");
        for (int k = 0; k < str_sensor.length; k++)
            header += "## " + str_sensor[k] + "\n";

        WifiAPManager wifiAPManager = new WifiAPManager(getActivity());
        header += wifiAPManager.get_ftm_list_for_file_header();
        header += "## ---------- Header End ----------\n";
        file.save_str_to_file(header);
    }

    private void add_check_point(){
        check_point_counter += 1;
        String check_str = String.format("Check point: %d, elapsed_time: %f\n", check_point_counter, (float)(elapsedRealtime()/1e3 - measurement_start_time_ms/1e3));
        file.save_str_to_file(check_str);
        add_log(String.format("Check point %d saved", check_point_counter), false);
    }

    public void add_log(String str, boolean clear_log){
        if ((log_str_set == null) || clear_log)
            log_str_set = new ArrayList<>();

        // Add log message
        String[] sub_str = str.split("\n");
        long elapsed_time_ms = 0;
        if (measurement_start_time_ms != 0)
            elapsed_time_ms = elapsedRealtime() - measurement_start_time_ms;
        for(int k=0; k<sub_str.length; k++)
            log_str_set.add(String.format("%02d:%02d.%03d: %s", elapsed_time_ms/60000,(elapsed_time_ms%1000)/60, elapsed_time_ms%1000, sub_str[k]));
        while (log_str_set.size() > max_n_log)
            log_str_set.remove(0);

        // Display log messages to textview
        String log_str = "[Log]\n";
        for(int i=0; i<log_str_set.size(); i++)
            log_str += log_str_set.get(i) + "\n";
        tv2.setText(log_str);
    }

//    public void update_measurement_status(String str, int type){
//        if (type == 0)
//            last_status_sensor = str;
//        else if (type == 1) {
//            last_status_wifi = str;
//            last_wifi_status_update_time_ms = elapsedRealtime();
//        }
//
//        int elapsed_app_time_s = (int) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
//        int elapsed_app_time_min = elapsed_app_time_s / 60;
//        elapsed_app_time_s %= 60;
//
//        String result_str = String.format("[Measurement] Elapsed time: %02d:%02d\n", elapsed_app_time_min, elapsed_app_time_s);
//        result_str += "[Sensor] " + last_status_sensor + "\n";
//        result_str += "[Wifi] " + last_status_wifi;
//
//        if ((elapsedRealtime() - last_wifi_status_update_time_ms) > 5000)
//            result_str +=String.format("\n    [Warning] No Wifi results measured for %d sec", (int)(elapsedRealtime()/1e3 - last_wifi_status_update_time_ms/1e3));
//
//        tv.setText(result_str);
//    }

//    private void loadSettings() {
//        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
//        SharedPreferences.Editor editor = pref.edit();
//
//        if (!flag_rtt_supported) {
//            editor.putString("option_wifi_source", "rss");
//            editor.apply();
//        }
//        add_log("---------- MEASUREMENT SETTINGS ----------", false);
//
//        flag_gps_enabled = pref.getBoolean("option_gps_enable", true);
//        flag_wifi_enabled = pref.getBoolean("option_wifi_enable", true);
//        flag_rss_enabled = "rss".equals(pref.getString("option_wifi_source", "rtt"));
//        ftm_bandwidth = Integer.parseInt(pref.getString("option_ftm_bandwidth", "1"));
//        if ((ftm_bandwidth != 0) && (ftm_bandwidth != 1) && (ftm_bandwidth != 2) && ftm_bandwidth != 3) {
//            editor.putString("option_ftm_bandwidth", "1");
//            editor.apply();
//            ftm_bandwidth = 1;      // Default: 40 MHz
//        }
//        update_log("Collect Sensor data (rate: 100 Hz)", false);
//        if (flag_gps_enabled)
//            update_log("Collect GPS data (fastest)", false);
//        else
//            update_log("[Warning] disabled GPS data collection", false);
//
//        if (flag_wifi_enabled) {
//            if (flag_rss_enabled) {
//                update_log("Collect WiFi RSS data (fastest)", false);
//                WifiManager wifiManager = (WifiManager) getContext().getSystemService(WIFI_SERVICE);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                    if (!wifiManager.isScanThrottleEnabled())
//                        flag_wifi_throttling_enabled = false;
//                }
//                if (flag_wifi_throttling_enabled && flag_rss_enabled) {
//                    update_log("[Warning] WiFi RSS scan performance may be limited. Please disable WiFi scan throttling option from developer options", false);
//                }
//            }
//            else {
//                if (ftm_bandwidth == 0)
//                    update_log("Collect WiFi RTT (BW: 20 MHz) data (500 ms)", false);
//                else if (ftm_bandwidth == 1)
//                    update_log("Collect WiFi RTT (BW: 40 MHz) data (500 ms)", false);
//                else if (ftm_bandwidth == 2)
//                    update_log("Collect WiFi RTT (BW: 80 MHz) data (500 ms)", false);
//                else if (ftm_bandwidth == 3)
//                    update_log("Collect WiFi RTT (BW: ALL) data (500 ms)", false);
//            }
//        }
//        else
//            update_log("[Warning] WiFi data collection is disabled", false);
//    }


    @Override
    public void onStop() {
        super.onStop();
        wifiModule.unregister_receiver();
    }

    @Override
    public void log_msg(String log) {
        add_log(log, false);
    }

    @Override
    public void sensor_status(String status, int type) {
        if (type == MeasurementListener.TYPE_INIT_SUCCESS) {
            str_setting_sensor = status;
            flag_collect_sensor_data = true;
        } else if (type == MeasurementListener.TYPE_INIT_FAILED) {
            str_setting_sensor = status;
            flag_collect_sensor_data = false;
        } else if (type == MeasurementListener.TYPE_SENSOR_STATUS) {
            last_status_sensor = status;
            update_display();
        }
        // sensor values are not displayed in this activity
    }

    @Override
    public void wifi_status(String status, int type) {
        if (type == MeasurementListener.TYPE_INIT_SUCCESS) {
            str_setting_wifi = status;
            flag_collect_wifi_data = true;
        } else if (type == MeasurementListener.TYPE_INIT_FAILED) {
            str_setting_wifi = status;
            flag_collect_wifi_data = false;
        } else if (type == MeasurementListener.TYPE_WIFI_STATUS) {
            last_status_wifi = status;
            last_wifi_status_update_time_ms = elapsedRealtime();
            update_display();
        }
    }

    public void update_display(){
        int elapsed_app_time_s = (int) (elapsedRealtime()/1e3 - measurement_start_time_ms/1e3);
        int time_since_last_wifi_status_s = (int) (elapsedRealtime()/1e3 - last_wifi_status_update_time_ms/1e3);

        String result_str = String.format("[Measurement] Elapsed time: %02d:%02d\n", elapsed_app_time_s / 60, elapsed_app_time_s % 60);
        if (time_since_last_wifi_status_s > 5)
            result_str += "[Wifi] " + last_status_wifi + String.format(" (since last scan: %d s)", time_since_last_wifi_status_s) + "\n[Sensor] " + last_status_sensor;
        else
            result_str += "[Wifi] " + last_status_wifi + "\n[Sensor] " + last_status_sensor;
        tv.setText(result_str);
    }
}