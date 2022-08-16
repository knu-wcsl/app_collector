package icsl.apps.collector;

import static android.content.Context.WIFI_RTT_RANGING_SERVICE;

import android.content.pm.PackageManager;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class WifiFragment extends Fragment implements MeasurementListener{

    private TextView tv, tv2;
    private WifiModule wifiModule;
    private WifiAPManager wifiAPManager;

    public WifiFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_wifi, container, false);

        tv = (TextView) v.findViewById(R.id.tv_w);
        tv2 = (TextView) v.findViewById(R.id.tv_w2);
        Button btn = (Button) v.findViewById(R.id.btn_w);
        Button btn2 = (Button) v.findViewById(R.id.btn_w2);
        wifiModule = new WifiModule(getActivity(), this);
        wifiAPManager = wifiModule.wifiAPManager;

        boolean flag_rtt_supported = getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
        tv.setText("[Wifi test]\nRSS scan results will be displayed here");

        String str_tv = "[RTT]\n";
        if (flag_rtt_supported){
            str_tv += "This device support RTT feature\nLoaded FTM responders from 'ftmr_list.txt':\n(invoke RSS scan first to see if these are active)\n";
            str_tv += wifiAPManager.get_ftmr_list();
        } else {
            str_tv += "RTT feature is not available on this device\n";
        }
        tv2.setText(str_tv);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiModule.invoke_single_rss_scan();
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        return v;
    }

    @Override
    public void onStop() {
        super.onStop();
        wifiModule.unregister_receiver();
    }

    @Override
    public void log_msg(String log) {

    }

    @Override
    public void sensor_status(String status, int type) {

    }

    @Override
    public void wifi_status(String status, int type) {
        if (type == MeasurementListener.TYPE_RSS_VALUE) {
            tv.setText("[RSS Scan Results]\n" + status);
            tv2.setText("[RTT]\nFTM responders from 'ftmr_list.txt':\n" + wifiAPManager.get_ftmr_list());
        }
    }
}