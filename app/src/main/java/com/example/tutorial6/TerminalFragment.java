package com.example.tutorial6;

import static com.example.tutorial6.NavigationActivity.CsvRead;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.example.tutorial6.ui.login.ContactActivity;

import java.text.DecimalFormat;
import java.util.ArrayList;
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
    Float lastTimeFallDetected;
    Float currentTime;

    TextView textview_number_steps;
    TextView textView_bt_status;

//    ImageView gif_run;
//    ImageView gif_walk;

    Float start_time;
    Boolean first = Boolean.TRUE;
    Boolean start = true;
    PyObject pyobj;
    ImageButton reconnect_btn;
    ImageButton set_contact_btn;
    LinearLayout linearLayout_steps_counter;
    ProgressBar progressBar;
    ImageView gifImageView;

    public String EmergencyAlert = "\nEmergency Alert: \nThis message is to inform you that immediate assistance is required. \nThe fall detection feature on the app has been triggered, indicating a potential emergency situation. Please act promptly to provide the necessary aid. \n\nYou have received this message because you are registered as an emergency contact in the fall app. Thank you.";
    public String FallDetected = "\nFall Detected Alert: \nWe regret to inform you that a fall has been detected. \nThe app's fall detection feature has been triggered, indicating a potential injury or distress. Please reach out to the individual as soon as possible to ensure their well-being. \n\nYou have received this message because you are registered as an emergency contact in the fall app. Thank you for your swift action.";
    public String StatusUpdate = "\nStatus Update: \nThis message is to inform you that the individual has indicated that they are well and have not experienced a fall. \nPlease continue to monitor their condition and provide any necessary support. \n\nYou have received this message because you are registered as an emergency contact in the fall app. Thank you for your attention and care.";
    private TextView textViewContactName;

    private boolean BTconnected = false;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

        Objects.requireNonNull(getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

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

//    @Override
//    public void onStop() {
//        if (service != null && !getActivity().isChangingConfigurations())
//            try {
//                service.detach();
//            } catch (Exception e){
//                System.out.println("Error!");
//                e.printStackTrace();
//            }
//        super.onStop();
//    }

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

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }

        String fileName = "contacts.csv";
        String path = "/sdcard/csv_dir/contacts/" + fileName;
        ArrayList<String[]> csvData = CsvRead(path);
        String contactName = null;
        String contactNumber = null;

        for (int i = 0; i < csvData.size(); i++) {
            String[] line = csvData.get(i);
            contactName = line[0];
            contactNumber = line[1];
            break;
        }

        if (contactName != null || contactNumber != null) {
            textViewContactName.setText(contactName + " - " + contactNumber);
        } else {
            textViewContactName.setText("Empty");
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
        linearLayout_steps_counter = view.findViewById(R.id.linearLayout_steps_counter);
        set_contact_btn = view.findViewById(R.id.btn_set_contact);
        reconnect_btn = view.findViewById(R.id.imageButton);
        progressBar = view.findViewById(R.id.progressBar);
        gifImageView = view.findViewById(R.id.gifImageView);

        textview_number_steps = (TextView) view.findViewById(R.id.textview_number_steps);
        textView_bt_status = view.findViewById(R.id.textview_connected_status);
//        progressBar.getProgressDrawable().setColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN);
        gifImageView.setVisibility(View.INVISIBLE);
//        gif_run = view.findViewById(R.id.gifImageView_run);
//        gif_walk = view.findViewById(R.id.gifImageView_walk);

//        gif_run.setVisibility(View.INVISIBLE);
//        gif_walk.setVisibility(View.INVISIBLE);


        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (textViewContactName.getText() == "Empty") {
                    toast("Please set contact first.");
                } else if (!BTconnected) {
                    toast("Please connect The Device first.");
                } else {
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
            }
        });

        linearLayout_steps_counter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearSteps();
            }
        });

        // todo sms func
        ImageButton btnSendSMS = view.findViewById(R.id.btn_send_sms);

        btnSendSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (textViewContactName.getText() == "Empty") {
                    toast("Please set contact first.");
                } else {
                    showFallDetectedDialog((float) 1.0, EmergencyAlert);
                }
            }
        });

        set_contact_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), ContactActivity.class);
                startActivity(intent);
            }
        });

        reconnect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Objects.requireNonNull(getActivity()).onBackPressed();
                progressBar.setVisibility(View.VISIBLE);
            }
        });


        String fileName = "contacts.csv";
        String path = "/sdcard/csv_dir/contacts/" + fileName;
        ArrayList<String[]> csvData = CsvRead(path);
        String contactName = null;
        String contactNumber = null;

        for (
                int i = 0; i < csvData.size(); i++) {
            String[] line = csvData.get(i);
            contactName = line[0];
            contactNumber = line[1];
            break;
        }

        textViewContactName = view.findViewById(R.id.textViewContactName);
        if (contactName != null || contactNumber != null) {
            textViewContactName.setText(contactName);
        } else {
            textViewContactName.setText("Empty");
        }

        return view;
    }

    private void SendSMS(String msg) {
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
                        lastTimeFallDetected = floatTime;
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

                    double timeStamp = 3;
                    if (currentTime - lastTime > timeStamp) {
                        // send it to python
                        double stepsThreshold = 8.9;
                        PyObject obj = pyobj.callAttr("main", received_chunk_values, stepsThreshold);
                        int numberSteps = obj.toInt();
                        double fallThreshold = 17.0;
                        // check status run or walk
                        CheckStatus(numberSteps);
                        // check if fall
                        PyObject obj2 = pyobj.callAttr("main", received_chunk_values, fallThreshold);
                        int numberPeaks = obj2.toInt();
                        if (numberSteps > 1) {
                            gifImageView.setVisibility(View.VISIBLE);
                        } else {
                            gifImageView.setVisibility(View.INVISIBLE);
                        }
                        if (numberPeaks > 2) {
                            // fall detected
                            showFallDetectedDialog(currentTime, FallDetected);
                        }

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

    private void CheckStatus(int numberSteps) {
//        if (numberSteps > 8) {
//            gif_run.setVisibility(View.VISIBLE);
//            gif_walk.setVisibility(View.INVISIBLE);
//        } else if (numberSteps > 2){
//            gif_run.setVisibility(View.INVISIBLE);
//            gif_walk.setVisibility(View.VISIBLE);
//        } else {
//            gif_run.setVisibility(View.INVISIBLE);
//            gif_walk.setVisibility(View.INVISIBLE);
//        }
    }

    private void showFallDetectedDialog(float detectTime, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Fall Detected");
        builder.setMessage("Are you okay?");
        builder.setCancelable(false);

        // Set positive button
        builder.setPositiveButton("I'm Okay", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Cancel the timer and dismiss the dialog
                cancelFallDetectedTimer();
                dialog.dismiss();
            }
        });

        // Set negative button
        builder.setNegativeButton("Help!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Call the FallHandler function here
                if (detectTime - lastTimeFallDetected > 5) {
                    toast("Fall Detected. Not sent. multy detect.");
                    return;
                } else if (detectTime - lastTimeFallDetected > 60 || detectTime - lastTimeFallDetected < 0) {
                    FallHandler(msg);
                    dialog.dismiss();
                    lastTimeFallDetected = detectTime;
                }
            }
        });


        // Create a TextView for the timer
        TextView timerTextView = new TextView(getActivity());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER;
        timerTextView.setLayoutParams(layoutParams);
        timerTextView.setTextColor(getResources().getColor(R.color.colorPrimary));
        timerTextView.setTextSize(24);
        timerTextView.setPadding(0, 16, 0, 16);
        builder.setView(timerTextView);

        // Create the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Start the timer
        startFallDetectedTimer(dialog, timerTextView, msg);
    }

    private static final int FALL_DETECTED_TIMER_DURATION = 30000; // 30 seconds
    private CountDownTimer fallDetectedTimer;

    private void startFallDetectedTimer(AlertDialog dialog, TextView timerTextView, String msg) {
        fallDetectedTimer = new CountDownTimer(FALL_DETECTED_TIMER_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update the timer text
                long secondsRemaining = millisUntilFinished / 1000;
                String timerText = "\t\t\t\tTimer: " + secondsRemaining + " seconds";
                timerTextView.setTextColor(Color.WHITE);
                timerTextView.setTextSize(12);
                timerTextView.setText(timerText);
            }

            @Override
            public void onFinish() {
                // Call the FallHandler function after the timer ends
                FallHandler(msg);
                dialog.dismiss();
            }
        };
        fallDetectedTimer.start();
    }


    private void cancelFallDetectedTimer() {
        if (fallDetectedTimer != null) {
            fallDetectedTimer.cancel();
        }
    }


    private void FallHandler(String msg) {
        SendSMS(msg);
        toast("Fall detected. Emergency SMS sent.");
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
                    toast("Device " + device.getName() + " Connected!");
                    textView_bt_status.setText("Connected!");
                    reconnect_btn.setVisibility(View.INVISIBLE);
                    reconnect_btn.setClickable(false);
                    progressBar.setVisibility(View.INVISIBLE);
                    BTconnected = true;
                } catch (Exception e) {
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//           ... //Done searching
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
//           ... //Device is about to disconnect
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                try {
                    toast("Device Disconnected!");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error!! Cant Toast!!");
                }
                BTconnected = false;
                textView_bt_status.setText("Disconnected!");
                reconnect_btn.setVisibility(View.VISIBLE);
                reconnect_btn.setClickable(true);
            }

        }
    };

}
