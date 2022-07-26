package icsl.apps.collector;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import icsl.apps.collector.R;

public class WifiActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private WifiReceiver wifiReceiver;
    private TextView tv, tvftm;
    private ScrollView svftm;
    private Button btn, btn2;
    private String TAG = "WifiActivity";
    private boolean is_rtt_supported = false;

    private WifiRttManager wifiRttManager;
    private boolean clear_to_scan = true;
    private ArrayList<ScanResult> ftmr_list = new ArrayList<>();
    private WifiAPManager wifiAPManager;

    // Custom rtt ranging
    private Constructor responderConfigConstructor = null;
    private Method addResponderMethod = null;
    private int RESPONDER_AP = 0;
    private RangingRequest.Builder builder;
    private ArrayList<String> ftmr_mac_list = new ArrayList<>();
    private int n_ftmr = 0;
    private int ftm_bandwidth;
    private int curr_ftm_bandwidth;
    private String ftm_result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi);

        // Layout
        tv = (TextView) findViewById(R.id.tv);
        tvftm = (TextView) findViewById(R.id.tvftm);
        svftm = (ScrollView) findViewById(R.id.svftm);
        btn = (Button) findViewById(R.id.btn);
        btn2 = (Button) findViewById(R.id.btn2);

        // Init message
        tv.setText("No scan results available yet");
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
            is_rtt_supported = true;

        // Button
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (wifiManager.startScan() == false)
                    Toast.makeText(getBaseContext(), "Unable to initiate Wifi scan (throttling)", Toast.LENGTH_SHORT).show();
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (is_rtt_supported) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (!clear_to_scan)
                            Toast.makeText(getApplicationContext(), "Previous FTM measurement is running. Please try it later", Toast.LENGTH_SHORT).show();
                        else {
                            if (ftm_bandwidth == 3)
                                curr_ftm_bandwidth = 0;
                            else
                                curr_ftm_bandwidth = ftm_bandwidth;
                            ftm_result = "";
                            invoke_rtt_scan();
                        }
                    }
                } else {
                    Toast.makeText(getBaseContext(), "FTM measurement is not supported", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // WifiManager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        wifiAPManager = new WifiAPManager(this);

        if (is_rtt_supported)
            tvftm.setText("This device supports FTM protocol\n[FTMR from file]\n" + wifiAPManager.get_ftmr_list());
        else
            tvftm.setText("This device does not support FTM protocol");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiReceiver, intentFilter);

        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        ftm_bandwidth = Integer.parseInt(pref.getString("option_ftm_bandwidth", "1"));
        Log.d(TAG, ftm_bandwidth + " ");

        // WifiRTTManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            wifiRttManager = (WifiRttManager) getApplicationContext().getSystemService(WIFI_RTT_RANGING_SERVICE);
            prepare_custom_rtt_ranging();
//            add_ftmr_responder("dc:8b:28:55:07:71", 5240,  0, 0, 0, 2);
//            add_ftmr_responder("38:2c:4a:91:27:24", 5500,  0, 0, 0, 2);
//            add_ftmr_responder("88:d7:f6:04:59:44", 5240,  0, 0, 1, 2);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void prepare_custom_rtt_ranging() {
        // Access hidden class and method using Java reflection
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
    private void add_ftmr_responder(String mac_str, int frequency, int centerFreq0, int centerFreq1, int channelWidth, int preamble){
        n_ftmr += 1;
        Class ResponderConfigClass=null, bulderClass = null;
        Field field = null;
        Method addResponderMethod = null;
        Object responder = null;
        try {
            // Reference: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/wifi/java/android/net/wifi/rtt/ResponderConfig.java?autodive=0%2F%2F
            // Constructor
            // public ResponderConfig(@NonNull MacAddress macAddress, @ResponderType int responderType,
            //      boolean supports80211mc, @ChannelWidth int channelWidth, int frequency, int centerFreq0,
            //      int centerFreq1, @PreambleType int preamble)

            ResponderConfigClass = Class.forName("android.net.wifi.rtt.ResponderConfig");
            Constructor ResponderConfigCon = ResponderConfigClass.getConstructor(MacAddress.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class);

            responder = ResponderConfigCon.newInstance(MacAddress.fromString(mac_str), RESPONDER_AP, true, channelWidth, frequency, centerFreq0, centerFreq1, preamble);
            addResponderMethod = RangingRequest.Builder.class.getMethod("addResponder", ResponderConfigClass);
            addResponderMethod.invoke(builder, responder);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
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
        n_ftmr = 0;
        builder = new RangingRequest.Builder();
        ArrayList<WifiAPManager.APInfo> aplist = wifiAPManager.get_ap_list();
        for (int k=0; k < aplist.size(); k++)
            add_ftmr_responder(aplist.get(k).mac_addr, 5180, 5210, 0, curr_ftm_bandwidth, 2);

        RangingRequest rangingRequest;
        rangingRequest = builder.build();

        if (n_ftmr == 0){
            Toast.makeText(getApplicationContext(), "No FTMR found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            wifiRttManager.startRanging(rangingRequest, AsyncTask.SERIAL_EXECUTOR, rangingcallback); //...and send out the ranging request. When results are obtained, rangingcallback will be called
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private RangingResultCallback rangingcallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(final int i) { // ranging failed completely
            if ((ftm_bandwidth == 3) && (curr_ftm_bandwidth < 2)){
                curr_ftm_bandwidth += 1;
                invoke_rtt_scan();
                return;
            }
            clear_to_scan = true;
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results) {
            if (curr_ftm_bandwidth == 0)
                ftm_result += "[FTM results (20 MHz)]\n";
            if (curr_ftm_bandwidth == 1)
                ftm_result += "[FTM results (40 MHz)]\n";
            if (curr_ftm_bandwidth == 2)
                ftm_result += "[FTM results (80 MHz)]\n";

            for (int k=0; k < results.size(); k++) {
                RangingResult curr = results.get(k);
                if (curr.getStatus() == RangingResult.STATUS_SUCCESS)
                    ftm_result += String.format("%s, dist: %.2f m, std: %.2f m, rssi: %d, # success: %d (from %d)\n", curr.getMacAddress(), (float) curr.getDistanceMm() / 1e3, (float) curr.getDistanceStdDevMm() / 1e3, curr.getRssi(), curr.getNumSuccessfulMeasurements(), curr.getNumAttemptedMeasurements());
                else
                    ftm_result += String.format("%s, ranging failed\n", curr.getMacAddress());
            }

            if ((ftm_bandwidth == 3) && (curr_ftm_bandwidth < 2)){
                curr_ftm_bandwidth += 1;
                invoke_rtt_scan();
                return;
            }
            clear_to_scan = true;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvftm.setText(ftm_result);
                }
            });
        }
    };

    class WifiReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received");
            List<ScanResult>scanResults = wifiManager.getScanResults();
            ftmr_list.clear();

            String str_ftm = "";
            String str_legacy = "";

            for (int k = 0; k < scanResults.size(); k++) {
                ScanResult curr = scanResults.get(k);

                boolean is_ap_in_list = wifiAPManager.update_freq(curr.BSSID, curr.frequency, curr.SSID);
                String curr_str = curr.BSSID + ", " + curr.SSID + ", " + curr.frequency + " MHz, ";
                if (curr.centerFreq0 != curr.frequency)
                    curr_str += "(cf: " + curr.centerFreq0 + "MHz), ";
                switch (curr.channelWidth){
                    case ScanResult.CHANNEL_WIDTH_20MHZ:
                        curr_str += "20 MHz";
                        break;
                    case ScanResult.CHANNEL_WIDTH_40MHZ:
                        curr_str += "40 MHz";
                        break;
                    case ScanResult.CHANNEL_WIDTH_80MHZ:
                        curr_str += "80 MHz";
                        break;
                    case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                        curr_str += "80+80 MHz";
                        break;
                    case ScanResult.CHANNEL_WIDTH_160MHZ:
                        curr_str += "160 MHz";
                        break;
                }
                curr_str += ", " + curr.level + "dBm";
                if (curr.is80211mcResponder()) {
                    if (!is_ap_in_list) {
                        ftmr_list.add(curr);
                        str_ftm += curr_str + "\n";
                    }
                }
                else
                    str_legacy += curr_str + "\n";
            }

            // Legacy AP list
            tv.setText("[Legacy AP list]\n" + str_legacy);

            // FTMR list
            String result_ftm = "";
            String update_result = wifiAPManager.update_ftmr_file();
            if (update_result != null)
                result_ftm += update_result + "\n";
            result_ftm += "[FTMR from file]\n" + wifiAPManager.get_ftmr_list();
            result_ftm += "[FTMR from Wifi scan]\n" + str_ftm;
            tvftm.setText(result_ftm);

//            if (str_ftm.equals(""))
//                tvftm.setText("No FTMR founded");
//            else
//                tvftm.setText("[FTMR list]\n" + str_ftm);

        }
    }
}