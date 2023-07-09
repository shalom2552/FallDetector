package com.example.tutorial6;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Toast;

import com.example.tutorial6.ui.dashboard.DashboardFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.tutorial6.databinding.ActivityNavigationBinding;
import com.opencsv.CSVReader;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class NavigationActivity extends AppCompatActivity {

    private ActivityNavigationBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNavigationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_navigation);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);



    }


    @RequiresApi(api = Build.VERSION_CODES.S)
    @SuppressLint("UnlocalizedSms")
    public static void SendSMS(SerialService service, String msg) {

        try {
//            // Create the Google Maps link
//            String googleMapsLink = "https://www.google.com/maps?q=" + "latitude" + "," + "longitude"; // todo
//            // Create the message body with the Google Maps link
//            String messageBody = "Click here to view the location: " + googleMapsLink;

            String fileName = "contacts.csv";
            String path = "/sdcard/csv_dir/contacts/" + fileName;
            ArrayList<String[]> csvData = CsvRead(path);
            String contactName = null;
            String contactNumber = null;

            for (int i = 0; i < csvData.size(); i++) { // todo change if you want more then one contacts
                String[] line = csvData.get(i);
                contactName = line[0];
                contactNumber = line[1];
                break;
            }


            msg = "Hi " + contactName + "\n" + msg;
            ArrayList<String> parts = splitStringByLength(msg, 40);

            //Get the SmsManager instance and call the sendTextMessage method to send message
            SmsManager sms = SmsManager.getDefault();
            sms.sendMultipartTextMessage(contactNumber, null, parts, null, null);
//            sms.sendTextMessage(contactNumber, null, "12345678901234567890123456789012345678901234567890", null, null);
            toast(service.getApplicationContext(), "SMS Sent to " + contactName + "- " + contactNumber);

        } catch (Exception e){
            e.printStackTrace();
            toast(service.getApplicationContext(), "Failed sending SMS!");
        }
    }

    public static ArrayList<String> splitStringByLength(String text, int length) {
        ArrayList<String> parts = new ArrayList<>();
        int textLength = text.length();
        int startIndex = 0;

        while (startIndex < textLength) {
            int endIndex = Math.min(startIndex + length, textLength);
            String part = text.substring(startIndex, endIndex);
            parts.add(part);
            startIndex += length;
        }

        return parts;
    }


    public static ArrayList<String[]> CsvRead(String path) {
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[] nextline;
            while ((nextline = reader.readNext()) != null) {
                if (nextline != null) {
                    CsvData.add(nextline);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return CsvData;
    }

    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }
}