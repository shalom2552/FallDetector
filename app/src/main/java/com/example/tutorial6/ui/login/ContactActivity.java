package com.example.tutorial6.ui.login;

import static com.example.tutorial6.NavigationActivity.CsvRead;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tutorial6.R;
import com.example.tutorial6.databinding.ActivityContactBinding;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

public class ContactActivity extends AppCompatActivity {

    private LoginViewModel loginViewModel;
    private ActivityContactBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        binding = ActivityContactBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = binding.username;
        final EditText phoneNumberEditText = binding.textviewNumber;
        final Button loginButton = binding.login;
        final ProgressBar loadingProgressBar = binding.loading;
        ImageButton imageButton_reset_contact = binding.imageButtonResetContact;

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    phoneNumberEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                loadingProgressBar.setVisibility(View.GONE);
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                if (loginResult.getSuccess() != null) {
                    updateUiWithUser(loginResult.getSuccess());
                }
                setResult(Activity.RESULT_OK);

                //Complete and destroy login activity once successful
                finish();
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        phoneNumberEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        phoneNumberEditText.addTextChangedListener(afterTextChangedListener);
        phoneNumberEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(usernameEditText.getText().toString(),
                            phoneNumberEditText.getText().toString());
                }
                return false;
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadingProgressBar.setVisibility(View.VISIBLE);
//                loginViewModel.login(usernameEditText.getText().toString(),
//                        phoneNumberEditText.getText().toString());
                saveToCSV(usernameEditText.getText().toString(), phoneNumberEditText.getText().toString());
                finish();
            }
        });

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

        TextView textViewContactName = binding.textViewContactName;
        TextView textViewContactNumber = binding.textViewContactNumber;

        if (contactName != null || contactNumber != null) {
            textViewContactName.setText(contactName);
            textViewContactNumber.setText(contactNumber);
        } else {
            textViewContactName.setText("Empty");
            textViewContactNumber.setText("Empty");
        }

        imageButton_reset_contact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String path = "/sdcard/csv_dir/contacts/";
                String fileName = "contacts.csv";
                File file = new File(path + fileName);
                if(file.exists()) {
                    File file2 = new File(file.getAbsolutePath());
                    file2.delete();
                    toast("File deleted.");
                    finish();
                }else
                {
                    toast("File not exists");
                }
            }
        });


    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void updateUiWithUser(LoggedInUserView model) {
        String welcome = getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        Toast.makeText(getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
    }


    private void saveToCSV(String name, String number) {
        try {

            @SuppressLint("SdCardPath") String path = "/sdcard/csv_dir/contacts/";
            String file_name = "contacts";
            file_name = file_name + ".csv";
            String csv = path + file_name;

            File file = new File(path);
            file.mkdirs();
            File file2 = new File(csv);
            if (!file2.exists()) {
                System.out.println("ASDASD");
                file2.createNewFile();
            }

            CSVWriter csvWriter = new CSVWriter(new FileWriter(csv));
            csvWriter.writeNext(new String[]{name, number});
            csvWriter.close();
            Toast.makeText(ContactActivity.this, "Contact Saved!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Not Saved!", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

}