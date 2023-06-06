package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

        // get element from activity
        Button loadBtn = findViewById(R.id.load_btn);
        TextView fileName = findViewById(R.id.load_file_name_txt);

        // on click load button
        loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String path = "/sdcard/csv_dir/" + fileName.getText() + ".csv";
                    ArrayList<String[]> csvData = new ArrayList<>();
                    csvData = CsvRead(path);

//                    System.out.println(DataValues(csvData).get(0));
//                    System.out.println(DataValues(csvData).get(1));
//                    System.out.println(DataValues(csvData).get(2));

                    LineDataSet lineDataSetX = new LineDataSet(DataValues(csvData).get(0), "ACC X");
                    lineDataSetX.setColor(Color.RED);
                    lineDataSetX.setCircleColor(Color.RED);
                    LineDataSet lineDataSetY = new LineDataSet(DataValues(csvData).get(1), "ACC Y");
                    lineDataSetY.setColor(Color.GREEN);
                    lineDataSetY.setCircleColor(Color.GREEN);
                    LineDataSet lineDataSetZ = new LineDataSet(DataValues(csvData).get(2), "ACC Z");
                    lineDataSetZ.setColor(Color.BLUE);
                    lineDataSetZ.setCircleColor(Color.BLUE);

                    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
                    dataSets.add(lineDataSetX);
                    dataSets.add(lineDataSetY);
                    dataSets.add(lineDataSetZ);
                    LineData data = new LineData(dataSets);
                    lineChart.setData(data);
                    lineChart.invalidate();
                } catch (Exception e){
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

    private void ClickBack(){
        finish();
    }

    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[] nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);
                }
            }
            toast("File opened successfully!");
        }catch (Exception e){
            e.printStackTrace();
        }
        return CsvData;
    }

    private ArrayList<ArrayList<Entry>> DataValues(ArrayList<String[]> csvData){
        ArrayList<Entry> dataValsX = new ArrayList<Entry>();
        ArrayList<Entry> dataValsY = new ArrayList<Entry>();
        ArrayList<Entry> dataValsZ = new ArrayList<Entry>();
        for (int i = 6; i < csvData.size(); i++){
            dataValsX.add(new Entry(Float.parseFloat(csvData.get(i)[0]), Float.parseFloat(csvData.get(i)[1])));
            dataValsY.add(new Entry(Float.parseFloat(csvData.get(i)[0]), Float.parseFloat(csvData.get(i)[2])));
            dataValsZ.add(new Entry(Float.parseFloat(csvData.get(i)[0]), Float.parseFloat(csvData.get(i)[3])));
        }
        ArrayList<ArrayList<Entry>> dataVals = new ArrayList<>();
        dataVals.add(dataValsX);
        dataVals.add(dataValsY);
        dataVals.add(dataValsZ);
        return dataVals;
    }

    private void toast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        TextView vv = (TextView) toast.getView().findViewById(android.R.id.message);
        vv.setTextColor(Color.BLACK);
        toast.show();
    }

}