package com.example.pdr;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.SystemClock.elapsedRealtime;

public class MainActivity extends AppCompatActivity {

    private boolean is_permission_granted = false;
    private boolean is_measurement_running = false;
    private boolean is_rtt_supported = false;
    private int ftm_bandwidth;
    private int check_point_counter = 0;
    private long measurement_start_time_ms = 0;

    // Measurement module
    private SensorModule sensorModule;
    private WifiModule wifiModule;
    private FileModule file;

    // Measuremet settings
    private boolean is_enable_gps = true;
    private boolean is_enable_wifi = true;
    private boolean is_enable_rss = true;
    private boolean is_enable_wifi_throttling = true;

    // Measurement status
    private String last_status_sensor = "";
    private String last_status_wifi = "";
    private long last_wifi_status_update_time_ms;

    // Layout
    private TextView tv, tv2;
    private Button btn, btn2;
    private ArrayList<String> log_str_set;
    private int max_n_log = 10;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Permission Request
        //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, CHANGE_WIFI_STATE, ACCESS_WIFI_STATE, WRITE_EXTERNAL_STORAGE}, 1);
        ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, ACCESS_WIFI_STATE}, 1);

        // Layouts
        tv = findViewById(R.id.tv);         // measurement status
        tv2 = findViewById(R.id.tv2);       // log
        tv.setText("[Measurement]");

        update_log("---------------- INITIALIZATION ----------------", true);
        // Check if rtt is supported
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            is_rtt_supported = true;
            update_log("This device supports RTT feature", false);
        }
        else{
            update_log("This device doesn't support RTT feature", false);
        }

        // Load modules
        wifiModule = new WifiModule(this);
        sensorModule = new SensorModule(this);


        // Button 1 (start/stop scan)
        btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (is_measurement_running)
                    stop_tracking();
                else
                    start_tracking();
            }
        });

        // Button 2 (add timestamp to the file)
        btn2 = (Button) findViewById(R.id.btn2);
        btn2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (is_measurement_running)
                    add_check_point();
                else
                    Toast.makeText(getApplicationContext(), "Measurement is not running", Toast.LENGTH_SHORT).show();
            }
        });
        // Load settings
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        loadSettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(is_measurement_running){
            Toast.makeText(getApplicationContext(), "Measurement is running. Try again later.", Toast.LENGTH_SHORT).show();
            return false;
        }

        switch(item.getItemId()){
            case R.id.sensor:
                startActivity(new Intent(MainActivity.this, SensorActivity.class));
                break;

            case R.id.wifi:
                startActivity(new Intent(MainActivity.this, WifiActivity.class));
                break;

            case R.id.settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void update_measurement_status(String str, int type){
        if (type == 0)
            last_status_sensor = str;
        else if (type == 1) {
            last_status_wifi = str;
            last_wifi_status_update_time_ms = elapsedRealtime();
        }

        int elapsed_app_time_s = (int) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
        int elapsed_app_time_min = elapsed_app_time_s / 60;
        elapsed_app_time_s %= 60;

        String result_str = String.format("[Measurement] Elapsed time: %02d:%02d\n", elapsed_app_time_min, elapsed_app_time_s);
        result_str += "[Sensor] " + last_status_sensor + "\n";
        result_str += "[Wifi] " + last_status_wifi;

        if ((elapsedRealtime() - last_wifi_status_update_time_ms) > 5000)
            result_str +=String.format("\n    [Warning] No Wifi results measured for %d sec", (int)(elapsedRealtime()/1e3 - last_wifi_status_update_time_ms/1e3));

        tv.setText(result_str);
    }

    public void update_log(String str, boolean clear_log){
        if ((log_str_set == null) || clear_log)
            log_str_set = new ArrayList<>();

        int elapsedTime = 0;
        if (measurement_start_time_ms != 0)
            elapsedTime = (int)(elapsedRealtime() - measurement_start_time_ms);
        int ms = elapsedTime % 1000;
        elapsedTime = elapsedTime / 1000;
        int s = elapsedTime % 60;
        int m = elapsedTime / 60;

        log_str_set.add(String.format("%02d:%02d.%03d: %s", m, s, ms, str));
        while (log_str_set.size() > max_n_log)
            log_str_set.remove(0);

        String log_str = "[Log]\n";
        for(int i=0; i<log_str_set.size(); i++)
            log_str += log_str_set.get(i) + "\n";
        tv2.setText(log_str);
    }

    private void start_tracking(){
        if (!is_permission_granted){
            Toast.makeText(getApplicationContext(), "[ERROR] Permissions are not granted. Please restart the APP", Toast.LENGTH_LONG).show();
            return;
        }
        is_measurement_running = true;
        btn.setText("Stop");

        check_point_counter = 0;
        measurement_start_time_ms = elapsedRealtime();
        last_wifi_status_update_time_ms = measurement_start_time_ms;

        // start all measurement
        update_log("---------- MEASUREMENT START ----------", false);
        file = new FileModule(this, "sensor", true, true, ".txt");
        write_file_header();
        update_log("File created: " + file.get_filename(), false);
        sensorModule.start_tracking(measurement_start_time_ms, file, is_enable_gps);
        if (is_enable_wifi)
            wifiModule.start_tracking(measurement_start_time_ms, file, is_enable_rss, ftm_bandwidth);

        // prevent screen off during location tracking
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    private void stop_tracking(){
        if (!is_permission_granted) {
            Toast.makeText(getApplicationContext(), "Permissions are not granted. Please Restart the application", Toast.LENGTH_LONG).show();
            return;
        }
        is_measurement_running = false;
        btn.setText("Start");

        // stop all measurements
        sensorModule.stop_tracking();
        wifiModule.stop_tracking();
        update_log("Saved file:" + file.get_filename(), false);

        // restore screen off setting back
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    private void write_file_header(){
        String header = "## -------- Measurement setup --------\n";
        if (is_rtt_supported)
            header += "## RTT feature: supported\n";
        else
            header += "## RTT feature: not supported\n";

        if (is_enable_gps)
            header += "## GPS data collection: enabled\n";
        else
            header += "## GPS data collection: disabled\n";

        if (is_enable_wifi) {
            header += "## Wifi data collection: enabled\n";
            header += "## Wifi data type: ";
            if (is_enable_rss)
                header += "RSS\n";
            else {
                header += "RTT ";
                if (ftm_bandwidth == 0)
                    header += "(20MHz)\n";
                else if (ftm_bandwidth == 1)
                    header += "(40MHz)\n";
                else if (ftm_bandwidth == 2)
                    header += "(80MHz)\n";
                else
                    header += "(ALL)\n";
            }
        }
        else
            header += "## Wifi data collection: disabled\n";

        WifiAPManager wifiAPManager = new WifiAPManager(this);
        header += wifiAPManager.get_ftm_list_for_file_header();
        header += "## -------- Header End --------\n";

        file.save_str_to_file(header);
    }


    private void add_check_point(){
        check_point_counter += 1;
        String check_str = String.format("Check point: %d, elapsed_time: %f\n", check_point_counter, (float)(elapsedRealtime()/1e3 - measurement_start_time_ms/1e3));
        file.save_str_to_file(check_str);
        update_log(String.format("Check point %d saved", check_point_counter), false);
    }


    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for(int i = 0; i < grantResults.length; i++)
            if (grantResults[i] != PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "WARNING: Permissions are not granted", Toast.LENGTH_SHORT).show();
                return;
            }
        is_permission_granted = true;
    }

    private void loadSettings() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();

        if (!is_rtt_supported) {
            editor.putString("option_wifi_source", "rss");
            editor.apply();
        }
        update_log("---------- MEASUREMENT SETTINGS ----------", false);

        is_enable_gps = pref.getBoolean("option_gps_enable", true);
        is_enable_wifi = pref.getBoolean("option_wifi_enable", true);
        is_enable_rss = "rss".equals(pref.getString("option_wifi_source", "rtt"));
        ftm_bandwidth = Integer.parseInt(pref.getString("option_ftm_bandwidth", "1"));
        if ((ftm_bandwidth != 0) && (ftm_bandwidth != 1) && (ftm_bandwidth != 2) && ftm_bandwidth != 3) {
            editor.putString("option_ftm_bandwidth", "1");
            editor.apply();
            ftm_bandwidth = 1;      // Default: 40 MHz
        }

        update_log("Collect Sensor data (rate: 100 Hz)", false);
        if (is_enable_gps)
            update_log("Collect GPS data (fastest)", false);
        else
            update_log("[Warning] disabled GPS data collection", false);

        if (is_enable_wifi) {
            if (is_enable_rss) {
                update_log("Collect WiFi RSS data (fastest)", false);
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!wifiManager.isScanThrottleEnabled())
                        is_enable_wifi_throttling = false;
                }
                if (is_enable_wifi_throttling) {
                    update_log("[Warning] WiFi scan requests may be throttled. Please disable WiFi scan throttling option from developer options", false);
                }
            }
            else {
                if (ftm_bandwidth == 0)
                    update_log("Collect WiFi RTT (BW: 20 MHz) data (500 ms)", false);
                else if (ftm_bandwidth == 1)
                    update_log("Collect WiFi RTT (BW: 40 MHz) data (500 ms)", false);
                else if (ftm_bandwidth == 2)
                    update_log("Collect WiFi RTT (BW: 80 MHz) data (500 ms)", false);
                else if (ftm_bandwidth == 3)
                    update_log("Collect WiFi RTT (BW: ALL) data (500 ms)", false);
            }
        }
        else
            update_log("[Warning] WiFi data collection is disabled", false);
    }
}