package icsl.apps.collector;

import static android.os.SystemClock.elapsedRealtime;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class ServerFragment extends Fragment implements MeasurementListener {

    // Status
    private String TAG = "SERVER";
    private boolean flag_connected = false;
    public boolean flag_measurement_running = false;
    private boolean flag_sync_finished = false;
    private boolean flag_collect_sensor_data = false;

    // Time
    private long measurement_start_time_ms = 0;
    private long server_time_offset_ms = 0;

    // Layout
    private EditText et, et2, et3, et4;
    private Button btn, btn2, btn3, btn4, btn5;
    private TextView tv, tv2, tv3;
    private String str_server, str_sensor, str_log;
    private String str_setting_sensor;

    // Log
    private ArrayList<String> log_str_set;
    private int max_n_log = 50;
    private int check_point_counter = 0;

    // Modules
    private ServerModule serverModule;
    private SensorModule sensorModule;
    private FileModule file;

    public ServerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_server, container, false);

        // Layout
        et = (EditText) v.findViewById(R.id.et_host);
        et2 = (EditText) v.findViewById(R.id.et_port);
        et3 = (EditText) v.findViewById(R.id.et_ch);
        et4 = (EditText) v.findViewById(R.id.et_bw);

        btn = (Button) v.findViewById(R.id.btn_s);
        btn2 = (Button) v.findViewById(R.id.btn_s2);
        btn3 = (Button) v.findViewById(R.id.btn_s3);
        btn4 = (Button) v.findViewById(R.id.btn_s4);
        btn5 = (Button) v.findViewById(R.id.btn_s5);

        tv = (TextView) v.findViewById(R.id.tv_s);
        tv2 = (TextView) v.findViewById(R.id.tv_s2);
        tv3 = (TextView) v.findViewById(R.id.tv_s3);

        // Load last success setting
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String host = pref.getString("option_host", "");
        int port = Integer.parseInt(pref.getString("option_port", "0"));
        et.setText(host);
        if (port > 0)
            et2.setText(String.valueOf(port));
        int ch = Integer.parseInt(pref.getString("option_channel", "1"));
        if (ch > 0)
            et3.setText(String.valueOf(ch));
        int bw = Integer.parseInt(pref.getString("option_bandwidth", "20"));
        if (bw > 0)
            et4.setText(String.valueOf(bw));

        // Initialize log
        add_log("-------- Initialization --------", true);

        // Load modules
        serverModule = new ServerModule(getActivity(), this);
        sensorModule = new SensorModule(getActivity(), this);

        // Button
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (flag_measurement_running)
                    stop();
                else
                    start();
            }
        });

        btn2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (flag_measurement_running)
                    add_check_point();
                else
                    Toast.makeText(getContext(), "Checkpoint works while taking measurements", Toast.LENGTH_SHORT).show();
            }
        });

        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    serverModule.connect(et.getText().toString(), Integer.parseInt(et2.getText().toString()));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        });

        btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!flag_connected)
                    Toast.makeText(getContext(), "This button works when connected to server", Toast.LENGTH_SHORT).show();
                else {
                    try {
                        serverModule.send_start_csi_msg(Integer.parseInt(et3.getText().toString()), Integer.parseInt(et4.getText().toString()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        btn5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!flag_connected)
                    Toast.makeText(getContext(), "This button works when connected to server", Toast.LENGTH_SHORT).show();
                else
                    serverModule.send_stop_csi_msg();
            }
        });

        return v;
    }

    private void start(){
        if (!flag_connected){
            Toast.makeText(getContext(), "Connect to the server first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!flag_sync_finished){
            Toast.makeText(getContext(), "Synchronization is not finished yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!flag_collect_sensor_data){
            Toast.makeText(getContext(), "Sensor measurement is disabled. Please check settings again", Toast.LENGTH_SHORT).show();
            return;
        }

        measurement_start_time_ms = elapsedRealtime();
        long curr_server_time_ms = Calendar.getInstance().getTimeInMillis() + server_time_offset_ms;

        check_point_counter = 0;
        file = new FileModule(getActivity(), String.format("sensor_%d", curr_server_time_ms), true, true, ".txt");

        flag_measurement_running = true;
        btn.setText("Stop");

        // start all measurement
        add_log("---------- MEASUREMENT START ----------", false);
        add_log("File created: " + file.get_filename(), false);
        write_file_header();

        sensorModule.start_measurement(measurement_start_time_ms, file);

        // prevent screen off during location tracking
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stop(){
        flag_measurement_running = false;
        btn.setText("Start");
        tv2.setText("[Sensor measurement]\nReady to collect sensor data...");

        // stop all measurements
        sensorModule.stop_measurement();
        add_log("Saved file:" + file.get_filename(), false);

        // restore screen off setting
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void write_file_header() {
        String header = "## ---------- Measurement Setup ----------\n";
        String[] str_sensor = str_setting_sensor.split("\n");
        for (int k = 0; k < str_sensor.length; k++)
            header += "## " + str_sensor[k] + "\n";
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

        str_log = log_str;
        update_tv(2);
    }

    private void update_tv(int tv_idx){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tv_idx == 0)
                    tv.setText(str_server);
                else if (tv_idx == 1)
                    tv2.setText(str_sensor);
                else if (tv_idx == 2)
                    tv3.setText(str_log);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        serverModule.disconnect();
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
            tv2.setText("[Sensor measurement]\nReady to collect sensor data...");
        } else if (type == MeasurementListener.TYPE_INIT_FAILED) {
            str_setting_sensor = status;
            flag_collect_sensor_data = false;
            tv2.setText("[Sensor measurement]\nSensor measurement is disabled.\nPlease check settings again");
        } else if (type == MeasurementListener.TYPE_SENSOR_STATUS) {
            str_sensor = "[Sensor measurement]\n" + status;
            tv2.setText(str_sensor);
        }
    }

    @Override
    public void wifi_status(String status, int type) {

    }

    @Override
    public void server_status(String status, int type) {
        if (type == MeasurementListener.TYPE_SERVER_CONNECTED) {
            flag_connected = true;
            add_log("Connected to the server", false);
            serverModule.sync_server_time();
            serverModule.run_server_status_checker();
            add_log("Server status check thread is initiated", false);
        } else if (type == MeasurementListener.TYPE_SERVER_FAILED_TO_CONNECT) {
            flag_connected = false;
            add_log("Failed to connect to the server. Please enter valid host/port", false);
        } else if (type == MeasurementListener.TYPE_SERVER_DISCONNECTED) {
            flag_connected = false;
            add_log("Disconnected from the server", false);
        } else if (type == MeasurementListener.TYPE_SERVER_STATUS) {
            String curr_time = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
            str_server = String.format("[Server status] Updated at %s\n", curr_time)  + status;
            update_tv(0);
        } else if (type == MeasurementListener.TYPE_SERVER_SYNC) {
            if (status.length() > 0){
                flag_sync_finished = true;
                server_time_offset_ms = Long.valueOf(status);
            }
        }
    }

    @Override
    public void ble_status(String status, int type) {

    }
}