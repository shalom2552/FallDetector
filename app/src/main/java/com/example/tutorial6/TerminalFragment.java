package com.example.tutorial6;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.mikephil.charting.data.Entry;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;



public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    Boolean recording = Boolean.FALSE;
    ArrayList<String[]> received_values = new ArrayList<>();
    ArrayList<Float> received_chunk_values = new ArrayList<>();
    Integer estimatedNumberOfSteps = 0;

    Boolean firstChunk = Boolean.TRUE;
    Float lastTime;
    Float currentTime;

    TextView textview_number_steps;
    TextView textView_bt_status;

    Float start_time;
    Boolean first = Boolean.TRUE;
    Boolean start = true;
    PyObject pyobj;
    Button reconnect_btn;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getActivity()));
        }
        Python py = Python.getInstance();
        pyobj = py.getModule("python");

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        Objects.requireNonNull(getActivity()).registerReceiver(mReceiver, filter);

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
            try {
                service.detach();
            } catch (Exception e){
                System.out.println("Error!");
                e.printStackTrace();
            }
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

        Button start_btn = view.findViewById(R.id.btn_start);
        Button clear_steps_btn = view.findViewById(R.id.btn_clear_steps);
        Button set_contact_btn = view.findViewById(R.id.btn_set_contact);
        reconnect_btn = view.findViewById(R.id.btn_reconnect);

        textview_number_steps = (TextView) view.findViewById(R.id.textview_number_steps);
        textView_bt_status = view.findViewById(R.id.textview_connected_status);


        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (start) {
                    recording = Boolean.TRUE;
                    firstChunk = Boolean.TRUE;
                    start_btn.setText("Stop");
                    start = false;
                } else {
                    recording = Boolean.FALSE;
                    PyObject obj = pyobj.callAttr("main", received_chunk_values, 8.9);
                    int numberSteps = obj.toInt();
                    estimatedNumberOfSteps += numberSteps;
                    textview_number_steps.setText(Integer.toString(estimatedNumberOfSteps));
                    // reset chunk data
                    received_chunk_values = new ArrayList<>();
                    start_btn.setText("Start");
                    start = true;
                }
            }
        });

        clear_steps_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearSteps();
            }
        });

        // todo sms func
        Button btnSendSMS = view.findViewById(R.id.btn_send_sms);

        btnSendSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "Hi!";
                SendSMS(msg);
                toast("SMS Sent successfully!");
            }
        });

//        set_contact_btn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent intent = new Intent(getContext(), ContactActivity.class);
//                startActivity(intent);
//            }
//        });

        reconnect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Objects.requireNonNull(getActivity()).onBackPressed();
            }
        });


        return view;
    }

    private void SendSMS(String msg){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NavigationActivity.SendSMS(service, msg);
        } else {
            toast("Failed! SDK Version!");
        }
    }


    private void toast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();

    }


    private void clearSteps() {
        received_chunk_values = new ArrayList<>();
        estimatedNumberOfSteps = 0;
        textview_number_steps.setText(estimatedNumberOfSteps.toString());
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
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] message) {
        if (hexEnabled) {
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
                    received_chunk_values.add(norma);

                    if (firstChunk) {
                        lastTime = floatTime;
                        firstChunk = Boolean.FALSE;
                    }
                    currentTime = floatTime;


                    if (currentTime - lastTime > 3.0) {
                        // send it to python
                        PyObject obj = pyobj.callAttr("main", received_chunk_values, 8.9);
                        int numberSteps = obj.toInt();
                        estimatedNumberOfSteps += numberSteps;
                        textview_number_steps.setText(Integer.toString(estimatedNumberOfSteps));
                        // clear chunk list
                        received_chunk_values = new ArrayList<>();
                        // update lastTime
                        lastTime = currentTime;
                    }

                    // here was mp_line chart update

                }

                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);

                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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


    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//           ... //Device found
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                try {
                    toast("Device " + device.getName() + " Connected!");
                    textView_bt_status.setText("Device Connected!");
                    reconnect_btn.setVisibility(View.VISIBLE);
                    reconnect_btn.setClickable(false);
                } catch (Exception e) {
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//           ... //Done searching
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
//           ... //Device is about to disconnect
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
//           ... //Device has disconnected
            }

        }
    };

}
