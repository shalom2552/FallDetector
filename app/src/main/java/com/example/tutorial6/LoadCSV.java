package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;

import java.util.List;


public class LoadCSV extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Button BackButton = (Button) findViewById(R.id.button_back);
        LineChart lineChart = (LineChart) findViewById(R.id.line_chart);

        // initialize python
        if (! Python.isStarted()){ Python.start(new AndroidPlatform(this)); }
        Python py = Python.getInstance();
        PyObject pyobj = py.getModule("python");

        // get element from activity
        Button loadBtn = findViewById(R.id.load_btn);
        Spinner spinnerFiles = findViewById(R.id.spinner_file_name);
        TextView calculatedNumberSteps = findViewById(R.id.textview_number_steps);

        // get list of all csv in the directory
        final File folder = new File("/sdcard/csv_dir/");
        ArrayList<String> files = listFilesForFolder(folder);

        // set spinner to have Walking and Running options
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, files);
        spinnerFiles.setAdapter(adapter);

        // on click load button
        loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String fileName = spinnerFiles.getSelectedItem().toString();
                    String path = "/sdcard/csv_dir/" + fileName;
                    ArrayList<String[]> csvData = new ArrayList<>();
                    csvData = CsvRead(path);

                    for (int i = 0; i < csvData.size(); i++) {
                        String[] line = csvData.get(i);
                        if (line[0].contains("ESTIMATED")){
                            Integer numberSteps = Integer.parseInt(csvData.get(i)[1]);
                            calculatedNumberSteps.setText(Integer.toString(numberSteps));
                            break;
                        }
                    }


                    LineDataSet lineDataSetN = new LineDataSet(DataValues(csvData).get(0), "Norma");
                    lineDataSetN.setColor(Color.RED);
                    lineDataSetN.setCircleColor(Color.BLUE);

//                    // todo test
//                    PyObject obj = pyobj.callAttr("main", DataArray(csvData));
//                    int numberSteps = obj.toInt();
//                    calculatedNumberSteps.setText(Integer.toString(numberSteps));
//                    // todo test

                    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
                    dataSets.add(lineDataSetN);

                    LineData data = new LineData(dataSets);
                    lineChart.setData(data);
                    lineChart.invalidate();
                    toast("File opened successfully!");
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("Failed to open!");
                }
            }
        });

        BackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });
    }

    private void ClickBack() {
        finish();
    }

    private ArrayList<String[]> CsvRead(String path) {
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

    private ArrayList<ArrayList<Entry>> DataValues(ArrayList<String[]> csvData) {
        ArrayList<Entry> dataValsN = new ArrayList<Entry>();

        for (int i = 7; i < csvData.size(); i++) {
            float x = Float.parseFloat(csvData.get(i)[1]);
            float y = Float.parseFloat(csvData.get(i)[2]);
            float z = Float.parseFloat(csvData.get(i)[3]);
            float norma = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
            dataValsN.add(new Entry(Float.parseFloat(csvData.get(i)[0]), norma));
        }
        ArrayList<ArrayList<Entry>> dataVals = new ArrayList<>();
        dataVals.add(dataValsN);
        return dataVals;
    }

    private ArrayList<Float> DataArray(ArrayList<String[]> csvData) {
        ArrayList<Float> dataValsN = new ArrayList<>();

        for (int i = 7; i < csvData.size(); i++) {
            float x = Float.parseFloat(csvData.get(i)[1]);
            float y = Float.parseFloat(csvData.get(i)[2]);
            float z = Float.parseFloat(csvData.get(i)[3]);
            float norma = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
            dataValsN.add(norma);
        }
        return dataValsN;
    }

    private void toast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        TextView vv = (TextView) toast.getView().findViewById(android.R.id.message);
        vv.setTextColor(Color.BLACK);
        toast.show();
    }

    public ArrayList<String> listFilesForFolder(final File folder) {
        ArrayList<String> filesName = new ArrayList<>();
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry);
            } else {
                if (!fileEntry.isHidden()) {
                    filesName.add(fileEntry.getName());
                }
            }
        }
        return filesName;
    }

}
