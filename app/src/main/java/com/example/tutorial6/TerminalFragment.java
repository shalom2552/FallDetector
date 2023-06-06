package com.example.tutorial6;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.SplittableRandom;


// Done 1. translate the graph to show N
// Done 2. TextView of calculated steps on RT
// TODO 3. stop button stops the count
// Done 4. reset button reset steps count
// Done 5. save button request file name on click (remove old field)
// Done 6. add csv field that represent STEPS OF NUMBER ESTIMATED holds the calculated num of steps
// TODO 6. calculate csv field that represent STEPS_OF_NUMBER_ESTIMATED
// Done 7. load file from a list of files in the csv_dir
// TODO 8. update textview_number_steps

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    LineChart mpLineChart;
    LineDataSet lineDataSet;
    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;
    Boolean recording = Boolean.FALSE;
    ArrayList<String[]> received_values = new ArrayList<>();
    Integer estimatedNumberOfSteps = 0;


    EditText editText_num_steps;
    EditText editText_filename;
    TextView textview_number_steps;
    Spinner spinner_state;

    Float start_time;
    Boolean first = Boolean.TRUE;
//    Boolean stopped = Boolean.FALSE;


    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

        if (! Python.isStarted()){
            Python.start(new AndroidPlatform(getActivity()));
        }
        Python py = Python.getInstance();
        PyObject pyobj = py.getModule("python");
//        PyObject obj = pyobj.callAttr("main", );

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        Button start_btn = view.findViewById(R.id.btn_start);
        Button stop_btn = view.findViewById(R.id.btn_stop);
        Button reset_btn = view.findViewById(R.id.btn_reset);
        Button save_btn = view.findViewById(R.id.btn_save);

        editText_num_steps = (EditText) view.findViewById(R.id.edittext_num_steps);
        editText_filename = (EditText) view.findViewById(R.id.edittext_filename);
        textview_number_steps = (TextView) view.findViewById(R.id.textview_number_steps); // TODO calculate on RT
        spinner_state = view.findViewById(R.id.spinner_state);

        textview_number_steps.setText("5");


        // set spinner to have Walking and Running options
        String[] items = new String[]{"Walking", "Running"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, items);
        spinner_state.setAdapter(adapter);

        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recording = Boolean.TRUE;
            }
        });


        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recording = Boolean.FALSE;
//                stopped = Boolean.TRUE;
            }
        });

        reset_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                estimatedNumberOfSteps = 0;
                // reset data
                resetData();
                // clear chart
                clearChart();
                // toast to user
                toast("reset");
            }
        });

        save_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = editText_filename.getText().toString().trim();
                if (TextUtils.isEmpty(text)) {
                    toast("Please Enter File Name First!");
                } else {
                    // Get file name from user
                    String file_name = editText_filename.getText().toString();
                    // Get number of steps from user
                    String num_steps = editText_num_steps.getText().toString();
                    // Get state from user
                    String state = spinner_state.getSelectedItem().toString();
                    // path to save file
                    String path = "/sdcard/csv_dir/";
                    try {
                        setUpCsv(path, file_name, num_steps, state);
                        clearChart();
                        resetData();
                        toast("File " + file_name + ".csv Saved Successfully!");
                    } catch (Exception e) {
                        toast("Cannot save file!");
                        e.printStackTrace();
                    }
                }
            }
        });
        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);

        lineDataSet = new LineDataSet(emptyDataValues(), "Norma");
        lineDataSet.setCircleColor(Color.RED);
        lineDataSet.setColor(Color.BLUE);

        dataSets.add(lineDataSet);

        data = new LineData(dataSets);
        mpLineChart.setData(data);
        mpLineChart.invalidate();

        Button buttonCsvShow = (Button) view.findViewById(R.id.button2);


        buttonCsvShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLoadCSV();

            }
        });

        return view;
    }

    private void resetData() {
        // reset data
        received_values = new ArrayList<>();
        recording = Boolean.FALSE;
        // reset fields
        editText_num_steps.setText(null);
        editText_filename.setText(null);
    }

    private void toast(String msg) {
        Toast toast = Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT);
        TextView vv = (TextView) toast.getView().findViewById(android.R.id.message);
        vv.setTextColor(Color.BLACK);
        toast.show();
    }

    private void clearChart() {  // TODO clear chart focus (could by times)
        LineData data = mpLineChart.getData();
        ILineDataSet set = data.getDataSetByIndex(0);
        while (set.removeLast()) {
        }
        mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
        mpLineChart.invalidate(); // refresh
        first = Boolean.TRUE;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr) {
        for (int i = 0; i < stringsArr.length; i++) {
            stringsArr[i] = stringsArr[i].replaceAll(" ", "");
        }


        return stringsArr;
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if (hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] message) {
        if (hexEnabled) {
            receiveText.append(TextUtil.toHexString(message) + '\n');
        } else {
            String msg = new String(message);
            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                String msg_to_save = msg;
                msg_to_save = msg.replace(TextUtil.newline_crlf, TextUtil.emptyString);
                // check message length
                if (msg_to_save.length() > 1) {
                    // split message string by ',' char
                    String[] parts = msg_to_save.split(",");
                    // function to trim blank spaces
                    parts = clean_str(parts);

                    float floatTime = roundFloat(Float.parseFloat(parts[3]));
                    if (first) {
                        first = Boolean.FALSE;
                        start_time = floatTime;
                    }

                    floatTime -= start_time;
                    String row[] = new String[]{String.valueOf(floatTime), parts[0], parts[1], parts[2]};

                    received_values.add(row);

                    float x = Float.parseFloat(parts[0]);
                    float y = Float.parseFloat(parts[1]);
                    float z = Float.parseFloat(parts[2]);

                    float norma = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));

                    data.addEntry(new Entry(floatTime, norma), 0);

                    lineDataSet.notifyDataSetChanged(); // let the data know a dataSet changed

                    mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
                    mpLineChart.invalidate(); // refresh

                }

                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // send msg to function that saves it to csv
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
            if (recording) {
                receive(data);
            }
        } catch (Exception e) {
            System.out.println("ERROR!! NO DATA!");
            e.printStackTrace();
        }
    }

    private void setUpCsv(String path, String file_name, String num_steps, String state) {
        try {
            file_name = file_name + ".csv";

            File file = new File(path);
            file.mkdirs();
            String csv = path + file_name;
            CSVWriter csvWriter = new CSVWriter(new FileWriter(csv, true));

            @SuppressLint("SimpleDateFormat") DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Date date = new Date();
            csvWriter.writeNext(new String[]{"NAME:", file_name, "", ""});
            csvWriter.writeNext(new String[]{"EXPERIMENT TIME:", dateFormat.format(date), "", ""});
            csvWriter.writeNext(new String[]{"ACTIVITY TYPE:", state, "", ""});
            csvWriter.writeNext(new String[]{"COUNT OF ACTUAL STEPS:", num_steps, "", ""});
            csvWriter.writeNext(new String[]{"ESTIMATED NUMBER OF STEPS:", Integer.toString(estimatedNumberOfSteps), "", ""});
            csvWriter.writeNext(new String[]{});
            csvWriter.writeNext(new String[]{"Time [sec]", "ACC X", "ACC Y", "ACC Z"});

            String[] strings;
            for (int i = 0; i < received_values.size(); i++) {
                strings = received_values.get(i);
                csvWriter.writeNext(strings);
            }

            csvWriter.close();
        } catch (IOException e) {
            Toast.makeText(getActivity(), "ERROR", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues() {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV() {
        Intent intent = new Intent(getContext(), LoadCSV.class);
        startActivity(intent);
    }

    public static float roundFloat(float value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.###");
        String roundedValueString = decimalFormat.format(value);
        return Float.parseFloat(roundedValueString);
    }
}
