package com.example.pdr;

import android.app.Activity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class WifiAPManager {
    private Activity mActivity;
    private ArrayList<APInfo> aplist = new ArrayList<>();

    ArrayList<String> init_result = new ArrayList<>();
    private final String ftmr_filename = "ftmr_list.txt";
    private final int mac_addr_len = 17; // 00:00:00:00:00:00

    private ArrayList<String> freq_change_list = new ArrayList<>();

    WifiAPManager(Activity activity) {
        mActivity = activity;
        String line = null;
//        File folder = new File(mActivity.getApplicationContext().getExternalFilesDir(null), "");
//        File file = new File(folder, ftmr_filename);
        try {
            InputStream inputStream = null;
//            inputStream = new FileInputStream(file);
            inputStream = mActivity.getApplicationContext().getAssets().open(ftmr_filename);

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() < 2)
                    continue;
                if (line.substring(0, 2).equals("##"))
                    continue;
                if (!add_access_point(line))
                    init_result.add(String.format("[Warning] Can not decode \"%s\" line in \"%s\" file", line, ftmr_filename));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            init_result.add(String.format("[Warning] Unable to find \"%s\" file. Please put this file in assets folder to manually load FTMR list for RTT measurement", ftmr_filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
        init_result.add(String.format("Loaded %d FTMR(s) from \"%s\" file", aplist.size(), ftmr_filename));
    }


    public ArrayList<String> get_init_result(){
        return init_result;
    }


    public boolean update_freq(String mac, int _freq, String _SSID){
        for (int k=0; k<aplist.size(); k++) {
            if (aplist.get(k).mac_addr.equals(mac)){
                aplist.get(k).is_active = true;
                aplist.get(k).SSID = _SSID;
                if (aplist.get(k).freq != _freq) {
                    freq_change_list.add(String.format("(%s, %d -> %d MHz)", aplist.get(k).mac_addr, aplist.get(k).freq, _freq));
                    aplist.get(k).freq = _freq;
                }
                return true;
            }
        }
        return false;
    }


    public ArrayList<APInfo> get_ap_list(){
        return aplist;
    }


    public String get_ftm_list_for_file_header(){
        String result = "## AP list in ftmr_list.txt file\n";
        for (int k=0; k < aplist.size(); k++)
            result += "## " + aplist.get(k).group + ", " + aplist.get(k).name + ", " + aplist.get(k).mac_addr + ", " + aplist.get(k).freq + "\n";
        return result;
    }


    public String get_ftmr_list() {
        String result = "";
        for (int k = 0; k < aplist.size(); k++) {
            if (aplist.get(k).is_active)
                result += "(active) ";
            else
                result += "(inactive) ";
            result += aplist.get(k).mac_addr + ", " + aplist.get(k).SSID + ", " + aplist.get(k).freq + "MHz\n";
        }
        return result;
    }


    public String update_ftmr_file(){
        if (freq_change_list.size() == 0)
            return null;

        String result = "Freq changed: ";
        for (int k=0; k<freq_change_list.size(); k++) {
            if (k > 0)
                result += ", ";
            result += freq_change_list.get(k);
        }

        freq_change_list.clear();
        return result;
    }


    private boolean add_access_point(String line){
        String[] items = line.split(", ");
        if (items.length != 4)
            return false;
        String group = items[0];
        String name = items[1];
        String mac = items[2];
        int freq = Integer.parseInt(items[3]);

        // Check mac address format
        if (mac.length() != mac_addr_len)
            return false;

        aplist.add(new APInfo(group, name, mac, freq));
        return true;
    }


    // class for AP information
    class APInfo {
        public String group;
        public String name;
        public String mac_addr;
        public int freq;
        public boolean is_active = false;
        public String SSID = "";

        APInfo(String _group, String _name, String _mac_addr, int _freq) {
            group = _group;
            name = _name;
            mac_addr = _mac_addr;
            freq = _freq;
        }
    }
}
