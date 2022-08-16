package icsl.apps.collector;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    private String TAG = "SettingFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        SwitchPreferenceCompat pref_rss = (SwitchPreferenceCompat) findPreference("option_collect_rss");
        SwitchPreferenceCompat pref_rtt = (SwitchPreferenceCompat) findPreference("option_collect_rtt");

        // check if rtt is supported
        boolean rtt_supported = false;
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
            rtt_supported = true;

        if(!rtt_supported) {
            pref_rtt.setChecked(false);
            pref_rtt.setEnabled(false);
            pref_rtt.setSummary("RTT feature is not supported");
        }

        // rss and rtt scan can't be enabled simultaneously
        pref_rss.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (pref_rss.isChecked())
                    pref_rss.setChecked(false);
                else{
                    pref_rss.setChecked(true);
                    if (pref_rtt.isChecked()){
                        Toast.makeText(getContext(), "RSS/RTT scan can't be performed simultaneously", Toast.LENGTH_SHORT).show();
                        pref_rtt.setChecked(false);
                    }
                }
                return false;
            }
        });

        pref_rtt.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (pref_rtt.isChecked())
                    pref_rtt.setChecked(false);
                else{
                    pref_rtt.setChecked(true);
                    if(pref_rss.isChecked()){
                        Toast.makeText(getContext(), "RSS/RTT scan can't be performed simultaneously", Toast.LENGTH_SHORT).show();
                        pref_rss.setChecked(false);
                    }
                }
                return false;
            }
        });
    }
}