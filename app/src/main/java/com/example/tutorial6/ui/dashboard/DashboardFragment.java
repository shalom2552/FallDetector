package com.example.tutorial6.ui.dashboard;

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
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.example.tutorial6.NavigationActivity;
import com.example.tutorial6.R;
import com.example.tutorial6.SerialListener;
import com.example.tutorial6.SerialService;
import com.example.tutorial6.SerialSocket;
import com.example.tutorial6.TextUtil;
import com.example.tutorial6.databinding.FragmentDashboardBinding;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Objects;

public class DashboardFragment extends Fragment implements ServiceConnection, SerialListener {

    private FragmentDashboardBinding binding;

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;

    private DashboardFragment.Connected connected = DashboardFragment.Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    Boolean recording = Boolean.FALSE;
    ArrayList<String[]> received_values = new ArrayList<>();
    ArrayList<Float> received_chunk_values = new ArrayList<>();
    Integer estimatedNumberOfSteps = 0;

    TextView textview_number_steps;
    TextView textView_bt_status;

    Boolean firstChunk = Boolean.TRUE;
    Float lastTime;
    Float currentTime;
    Float start_time;
    Boolean first = Boolean.TRUE;
    Boolean start = true;
    PyObject pyobj;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


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

//        //        String DEVICE_ADDRESS = "94:B5:55:34:0F:32";
//        String DEVICE_ADDRESS = "YAS2";
//        Bundle bundle = new Bundle();
//        bundle.putString("device", DEVICE_ADDRESS);
//        Intent intent = new Intent(getActivity(), NavigationActivity.class);
//        intent.putExtra("device", bundle);
//        startActivity(intent);
    }


    @Override
    public void onDestroy() {
        if (connected != DashboardFragment.Connected.False)
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


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);

        textview_number_steps = (TextView) view.findViewById(R.id.textview_number_steps);
        textView_bt_status = view.findViewById(R.id.textview_connected_status);

        Button start_btn = binding.getRoot().findViewById(R.id.btn_start);
        Button clear_steps_btn = binding.getRoot().findViewById(R.id.btn_clear_steps);
        Button btnSendSMS = binding.getRoot().findViewById(R.id.btn_send_sms);

        start_btn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                if (start) {
                    recording = Boolean.TRUE;
                    firstChunk = Boolean.TRUE;
                    start_btn.setText("Stop");
                    start = false;
                } else {
                    recording = Boolean.FALSE;
                    PyObject obj = pyobj.callAttr("main", received_chunk_values);
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

        btnSendSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "Hi!";
                SendSMS(msg);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


    @RequiresApi(api = Build.VERSION_CODES.S)
    @SuppressLint("UnlocalizedSms")
    private void SendSMS(String msg) {

        try {
            // Create the Google Maps link
            String googleMapsLink = "https://www.google.com/maps?q=" + "latitude" + "," + "longitude"; // todo

            // Create the message body with the Google Maps link
            String messageBody = "Click here to view the location: " + googleMapsLink;


            //Getting intent and PendingIntent instance
            Intent intent = new Intent(service.getApplicationContext(), DashboardFragment.class);
            @SuppressLint("UnspecifiedImmutableFlag")
            PendingIntent pi = PendingIntent.getActivity(service.getApplicationContext(), 0, intent, PendingIntent.FLAG_MUTABLE);

            //Get the SmsManager instance and call the sendTextMessage method to send message
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage("0526859466", null, msg, pi, null);
//            sms.sendTextMessage("0587708484", null, msg, pi, null);

            toast("SMS Sent successfully!");

        } catch (Exception e){
            e.printStackTrace();
            toast("Failed sending SMS!");
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
            connected = DashboardFragment.Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = DashboardFragment.Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if (connected != DashboardFragment.Connected.True) {
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
                        PyObject obj = pyobj.callAttr("main", received_chunk_values);
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
        connected = DashboardFragment.Connected.True;
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

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
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
                    if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    toast("Device " + device.getName() + " Connected!");
                    textView_bt_status.setText("Device Connected!");
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