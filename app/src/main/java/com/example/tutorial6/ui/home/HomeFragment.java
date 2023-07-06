package com.example.tutorial6.ui.home;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tutorial6.R;
import com.example.tutorial6.databinding.FragmentHomeBinding;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    public String contactName;
    public String contactNumber;
    EditText name;
    EditText phone;
    Button save;
    Button clear;


    @SuppressLint("MissingInflatedId")
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        if (Build.VERSION.SDK_INT >= 30){
            if (!Environment.isExternalStorageManager()){
                Intent getpermission = new Intent();
                getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(getpermission);
            }
        }

        name = root.findViewById(R.id.editTextTextPersonName);
        phone = root.findViewById(R.id.editTextPhone);
        save = root.findViewById(R.id.btn_save);
        clear = root.findViewById(R.id.btn_clear);

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    removeAllContacts();  // todo remove if you want more contacts. now it just remove all at every start
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                contactName = name.getText().toString();
                contactNumber = phone.getText().toString();
                addContact(contactName, contactNumber);
                name.setText("");
                phone.setText("");
                toast("Contact " + contactName +  " Saved!");
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                name.setText("");
                phone.setText("");
                toast("Clear");
            }
        });

        return root;
    }

    private void removeAllContacts() throws IOException {
        @SuppressLint("SdCardPath") String path = "/sdcard/csv_dir/contacts/";
        String fileName = "contacts.csv";
        File file = new File(path);
        file.mkdir();

        new FileWriter(path + fileName, false).close();
    }

    private void addContact(String name, String number) {
        try {
            @SuppressLint("SdCardPath") String path = "/sdcard/csv_dir/contacts/";
            String fileName = "contacts.csv";
            File file = new File(path);
            file.mkdir();

            new FileWriter(path + fileName, false).close();

            File file2 = new File(path + fileName);
            file2.createNewFile();


            CSVWriter csvWriter = new CSVWriter(new FileWriter(path + fileName, true));

            csvWriter.writeNext(new String[]{name, number});

            csvWriter.close();
        } catch (IOException e) {
            Toast.makeText(getActivity(), "ERROR", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void toast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}