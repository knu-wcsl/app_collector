package icsl.apps.collector;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.WIFI_RTT_RANGING_SERVICE;
import static android.os.SystemClock.elapsedRealtime;

public class WifiModule {
    private String TAG = "WIFI_MODULE";

    // Status
    private boolean flag_is_wifi_running = false;

    // Measurement settings
    private boolean flag_collect_rss_data = false;
    private boolean flag_collect_rtt_data = false;
    private float target_rss_scan_interval_ms;
    private float target_rtt_scan_interval_ms;
    private int rtt_scan_bandwidth;
    private boolean flag_rss_scan_throttled = false;
    private boolean flag_rtt_supported = false;
    private boolean flag_test_single_scan = false;

    // Measurement
    private boolean clear_to_scan = false;
    private int wifi_scan_idx = 0;
    private final float retry_time_rss_ms = 8000;
    private final float retry_time_rtt_ms = 2000;

    private long measurement_start_time_ms;
    private long last_scan_init_time_ms;

    // WiFi Service
    private WifiManager wifiManager;
    private WifiReceiver wifiReceiver;
    public WifiAPManager wifiAPManager;
    private WifiRttManager wifiRttManager;

    // RTT measurement
    private Constructor responderConfigConstructor = null;
    private Method addResponderMethod = null;
    private int RESPONDER_AP = 0;
    private RangingRequest.Builder builder;
    private int curr_rtt_bandwidth;
    private int n_ftmr;
    private int[] n_active_ap;
    ArrayList<WifiAPManager.APInfo> aplist;
    private int curr_ap_idx;

    // Instances
    private Activity mActivity;
    private MeasurementListener mListener;
    private FileModule file;


    WifiModule(Activity activity, MeasurementListener listener){
        mActivity = activity;
        mListener = listener;

        wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        wifiAPManager = new WifiAPManager(mActivity);
        ArrayList<String> init_result = wifiAPManager.get_init_result();
//        for (int k=0; k<init_result.size(); k++)
//            ((MainActivity) mActivity).update_log(init_result.get(k), false);

        // To receive RSS scan results
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mActivity.getApplicationContext().registerReceiver(wifiReceiver, intentFilter);

        // Load measurement settings
        loadSettings();
        report_init_status();

        // Prepare RTT scan
        if (flag_rtt_supported && flag_collect_rtt_data){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                wifiRttManager = (WifiRttManager) mActivity.getApplicationContext().getSystemService(WIFI_RTT_RANGING_SERVICE);
                Log.d(TAG, RangingRequest.getMaxPeers() + "");
                prepare_custom_rtt_ranging();
                aplist = wifiAPManager.get_ap_list();
            }
        }
    }

    public void invoke_single_rss_scan() {
        flag_test_single_scan = true;
        if (clear_to_scan) {
            clear_to_scan = false;
            wifiManager.startScan();
            last_scan_init_time_ms = elapsedRealtime();
        } else if ((elapsedRealtime() - last_scan_init_time_ms) > retry_time_rss_ms) {
            wifiManager.startScan();
            last_scan_init_time_ms = elapsedRealtime();
        } else{
            Toast.makeText(mActivity.getApplicationContext(), "Received too many scan requests. Please try again later", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    public void invoke_single_rtt_scan(){
        if(clear_to_scan) {
            clear_to_scan = false;
            invoke_rtt_scan();
            last_scan_init_time_ms = elapsedRealtime();
        } else if ((elapsedRealtime() - last_scan_init_time_ms) > retry_time_rtt_ms){
            invoke_rtt_scan();
            last_scan_init_time_ms = elapsedRealtime();
        } else{
            Toast.makeText(mActivity.getApplicationContext(), "Received too many scan requests. Please try again later", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean start_measurement(long start_time_ms, FileModule _file){
        flag_is_wifi_running = true;
        measurement_start_time_ms = start_time_ms;
        file = _file;
        wifi_scan_idx = 0;
        clear_to_scan = true;
        wifi_scan_routine();

        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void prepare_custom_rtt_ranging() {
        // Access hidden class and method using Java reflection
        // Reference: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/wifi/java/android/net/wifi/rtt/ResponderConfig.java
        Class responderConfigClass = null;
        Field field = null;
        try {
            responderConfigClass = Class.forName("android.net.wifi.rtt.ResponderConfig");
            responderConfigConstructor = responderConfigClass.getConstructor(MacAddress.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class);
            addResponderMethod = RangingRequest.Builder.class.getMethod("addResponder", responderConfigClass);
            field = responderConfigClass.getDeclaredField("RESPONDER_AP");
            RESPONDER_AP = field.getInt(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void add_ftmr(String mac_str, int frequency, int centerFreq0, int centerFreq1, int channelWidth, int preamble){
        n_ftmr += 1;
        Object responder = null;
        try {
            responder = responderConfigConstructor.newInstance(MacAddress.fromString(mac_str), RESPONDER_AP, true, channelWidth, frequency, centerFreq0, centerFreq1, preamble);
            addResponderMethod.invoke(builder, responder);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void invoke_rtt_scan() {
        if (curr_ap_idx >= aplist.size()) {
            curr_rtt_bandwidth += 1;
            curr_ap_idx = 0;
        }

        if ((curr_rtt_bandwidth >= 3) || (curr_rtt_bandwidth > rtt_scan_bandwidth)){
            // Ranging is done
            clear_to_scan = true;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            String status = String.format("Scan index: %d, time: %d ms, # AP: (%d, %d, %d) (total: %d)", wifi_scan_idx, (int)elapsed_scan_time_ms, n_active_ap[0], n_active_ap[1], n_active_ap[2], aplist.size());
            mListener.wifi_status(status, MeasurementListener.TYPE_WIFI_STATUS);
            return;
        }
        Log.d(TAG, curr_rtt_bandwidth + ", " + rtt_scan_bandwidth + ", " + curr_ap_idx);

        n_ftmr = 0;
        builder = new RangingRequest.Builder();
        add_ftmr(aplist.get(curr_ap_idx).mac_addr, aplist.get(curr_ap_idx).freq, 0, 0, curr_rtt_bandwidth, 2);
        curr_ap_idx += 1;

        RangingRequest rangingRequest;
        rangingRequest = builder.build();
        if (n_ftmr == 0)
            return;
        if (ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            wifiRttManager.startRanging(rangingRequest, AsyncTask.SERIAL_EXECUTOR, rangingcallback);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private RangingResultCallback rangingcallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(final int i) { // ranging failed completely
            mListener.log_msg("[ERROR] RTT failed");
            invoke_rtt_scan();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results) {
            float elapsed_time_ms = elapsedRealtime() - measurement_start_time_ms;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            String str_to_file = String.format("RTT, %f, %f", elapsed_time_ms/1e3, elapsed_scan_time_ms/1e3);
            if (curr_rtt_bandwidth == 0)
                str_to_file += ", 20\n";
            else if (curr_rtt_bandwidth == 1)
                str_to_file += ", 40\n";
            else if (curr_rtt_bandwidth == 2)
                str_to_file += ", 80\n";

            for (int k=0; k < results.size(); k++) {
                RangingResult curr = results.get(k);
                if (curr.getStatus() == RangingResult.STATUS_SUCCESS) {
                    n_active_ap[curr_rtt_bandwidth] += 1;
                    str_to_file += String.format("RTT, %f, %s, %f, %f, %d, %d, %d\n", (float) (curr.getRangingTimestampMillis() - measurement_start_time_ms)/1e3, curr.getMacAddress(), (float) curr.getDistanceMm() / 1e3, (float) curr.getDistanceStdDevMm() / 1e3, curr.getRssi(), curr.getNumSuccessfulMeasurements(), curr.getNumAttemptedMeasurements());
                }
            }
            if (file != null)
                file.save_str_to_file(str_to_file);
            invoke_rtt_scan();
        }
    };

    private void wifi_scan_routine(){       // this function will be executed recursively
        if (!flag_is_wifi_running)
            return;

        long curr_time_ms = elapsedRealtime();
        int sleep_time_ms = 50;
        if (clear_to_scan) {                 // start new scan
//            last_scan_init_time_ms = elapsedRealtime();
//            is_scan_time_exceed_max_time = false;
//            clear_to_scan = false;

            // RSS scan
            if (flag_collect_rss_data && (curr_time_ms - last_scan_init_time_ms) >= target_rss_scan_interval_ms) {
                if (wifiManager.startScan()) {
                    clear_to_scan = false;
                    last_scan_init_time_ms = curr_time_ms;
                    sleep_time_ms = (int) target_rss_scan_interval_ms;
                    if (sleep_time_ms < 0)
                        sleep_time_ms = 0;
                    if (file != null)
                        file.save_str_to_file(String.format("RSS, %f\n", (float) (curr_time_ms - measurement_start_time_ms) / 1e3));
                } else {
                    mListener.log_msg("[Warning] Can't initiate RSS scan (throttling)");
                    sleep_time_ms = 5000;
                }
            }

            // RTT scan
            if (flag_collect_rtt_data && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (curr_time_ms - last_scan_init_time_ms) >= target_rtt_scan_interval_ms) {
                clear_to_scan = false;
                last_scan_init_time_ms = curr_time_ms;
                if (rtt_scan_bandwidth == 3)    // all bandwidths (20, 40, 80)
                    curr_rtt_bandwidth = 0;
                else
                    curr_rtt_bandwidth = rtt_scan_bandwidth;
                if (file != null)
                    file.save_str_to_file(String.format("RTT, %f\n", (float) ((elapsedRealtime() - measurement_start_time_ms) / 1e3)));
                n_active_ap = new int[3];
                curr_ap_idx = 0;
                wifi_scan_idx += 1;
                invoke_rtt_scan();

                sleep_time_ms = (int) (target_rtt_scan_interval_ms - (elapsedRealtime() - last_scan_init_time_ms));
                if (sleep_time_ms < 0)
                    sleep_time_ms = 0;
            }
        }
        else {                      // clear_to_scan = false -> not ready to start new scan
            float elapsed_time_ms = curr_time_ms - last_scan_init_time_ms;
            if (flag_collect_rss_data) {
                if (elapsed_time_ms < target_rss_scan_interval_ms)
                    sleep_time_ms = (int)(target_rss_scan_interval_ms - elapsed_time_ms);
                else if (elapsed_time_ms > retry_time_rss_ms){
                    // if previous measurement takes too long
                    clear_to_scan = true;
                    sleep_time_ms = 0;
                    mListener.log_msg("RSS scan routine is restarted");
                }
            }
            if (flag_collect_rtt_data){
                if (elapsed_time_ms < target_rtt_scan_interval_ms)
                    sleep_time_ms = (int) (target_rtt_scan_interval_ms - elapsed_time_ms);
                else if (elapsed_time_ms > retry_time_rtt_ms){
                    clear_to_scan = true;
                    sleep_time_ms = 0;
                    mListener.log_msg("RTT scan routine is restarted");
                }
            }
        }
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                wifi_scan_routine();
            }
        }, sleep_time_ms);
    }

    public void stop_measurement(){
        flag_is_wifi_running = false;
     }

     public void unregister_receiver(){
        try{
            mActivity.getApplicationContext().unregisterReceiver(wifiReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
     }

    private void loadSettings(){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = pref.edit();

        flag_collect_rss_data = pref.getBoolean("option_collect_rss", true);
        flag_collect_rtt_data = pref.getBoolean("option_collect_rtt", false);
        target_rss_scan_interval_ms = Float.parseFloat(pref.getString("option_rss_interval", "0"));
        target_rtt_scan_interval_ms = Float.parseFloat(pref.getString("option_rtt_interval", "0"));
        rtt_scan_bandwidth = Integer.parseInt(pref.getString("option_rtt_bandwidth", "3"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)     // Check if throttling is enabled
            if (wifiManager.isScanThrottleEnabled())
                flag_rss_scan_throttled = true;
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
            flag_rtt_supported = true;
    }

    private void report_init_status(){
        String str_setting = "[Wifi settings]\n";
        if (flag_collect_rss_data){
            str_setting += "Wifi data collection: enabled\n";
            if (target_rss_scan_interval_ms == 0)
                str_setting += "Wifi data type: RSS (interval: fastest)\n";
            else
                str_setting += String.format("Wifi data type: RSS (interval: %d sec)\n", (int)(target_rss_scan_interval_ms/1000));
            if (flag_rss_scan_throttled)
                str_setting += "Wifi throttling: enabled (RSS scan performance may by limited)\n";
            else
                str_setting += "Wifi throttling: disabled\n";
        }
        if (flag_rtt_supported)
            str_setting += "This device support RTT measurement\n";
        if (flag_collect_rtt_data){
            str_setting += "Wifi data collection: enabled\n";
            str_setting += "Wifi data type: RTT";
            if (rtt_scan_bandwidth == 0)
                str_setting += " (20MHz)\n";
            if (rtt_scan_bandwidth == 1)
                str_setting += " (40MHz)\n";
            if (rtt_scan_bandwidth == 2)
                str_setting += " (80MHz)\n";
            if (rtt_scan_bandwidth == 3)
                str_setting += " (ALL)\n";

            if (target_rtt_scan_interval_ms == 0)
                str_setting += "RTT scan interval: fastest\n";
            else
                str_setting += String.format("RTT scan interval: %.0f ms\n", target_rtt_scan_interval_ms);
        }

        if (!flag_collect_rss_data && !flag_collect_rtt_data)
            str_setting += "Wifi data collection: disabled\n";

        if (flag_collect_rss_data || (flag_collect_rtt_data && flag_rtt_supported))
            mListener.wifi_status(str_setting, MeasurementListener.TYPE_INIT_SUCCESS);
        else
            mListener.wifi_status(str_setting, MeasurementListener.TYPE_INIT_FAILED);
        mListener.log_msg(str_setting);
    }

    class WifiReceiver extends BroadcastReceiver {
        private List<ScanResult> scanResults;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received scan results");
            if ((!flag_is_wifi_running  || !flag_collect_rss_data) && !flag_test_single_scan)
                return;

            // Process RSS scan results
            scanResults = wifiManager.getScanResults();
            float elapsed_time_ms = elapsedRealtime() - measurement_start_time_ms;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            clear_to_scan = true;

            if (elapsed_scan_time_ms < 500)
                return;
            wifi_scan_idx += 1;

            int n_active_ap_2 = 0;
            int n_active_ap_5 = 0;
            String str_to_file = String.format("RSS, %f, %f\n", elapsed_time_ms/1e3, elapsed_scan_time_ms/1e3);
            String str_to_listener = "Mac, SSID, Freq [MHz], RSSI [dBm]\n";

            for (int k = 0; k < scanResults.size(); k++) {
                ScanResult curr = scanResults.get(k);
                wifiAPManager.update_freq(curr.BSSID, curr.frequency, curr.SSID);

                str_to_file += String.format("RSS, %f, %s, %s, %d, %d, %d, %d, %d", (float)(curr.timestamp/1e6 - measurement_start_time_ms/1e3), curr.BSSID, curr.SSID, curr.frequency, curr.centerFreq0, curr.centerFreq1, curr.channelWidth, curr.level);
                str_to_listener += String.format("%s, %s, %d, %d\n", curr.BSSID, curr.SSID, curr.frequency, curr.level);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
//                    List<ScanResult.InformationElement> info = curr.getInformationElements();
                    str_to_file += ", " + curr.getWifiStandard();
                }
                str_to_file += "\n";
                if (curr.frequency >= 5000)
                    n_active_ap_5 += 1;
                else
                    n_active_ap_2 += 1;
            }
            if (file != null)
                file.save_str_to_file(str_to_file);

            String update_result = wifiAPManager.update_ftmr_file();
            if (update_result != null)
                mListener.log_msg(update_result);

            String status = String.format("Scan index: %d, scan time: %d ms\n# AP: %d (2.4G: %d, 5G: %d)", wifi_scan_idx, (int)elapsed_scan_time_ms, n_active_ap_2+n_active_ap_5, n_active_ap_2, n_active_ap_5);
            mListener.wifi_status(status, MeasurementListener.TYPE_WIFI_STATUS);
            mListener.wifi_status(str_to_listener, MeasurementListener.TYPE_RSS_VALUE);
        }
    }
}
