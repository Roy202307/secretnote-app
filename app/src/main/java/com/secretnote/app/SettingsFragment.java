package com.secretnote.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private TextView tvCurrentCode;
    private EditText etNewCode;
    private Button btnSaveCode;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "NotePrefs";
    private static final String ENTRY_CODE_KEY = "entry_code";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        tvCurrentCode = view.findViewById(R.id.tvCurrentCode);
        etNewCode = view.findViewById(R.id.etNewCode);
        btnSaveCode = view.findViewById(R.id.btnSaveCode);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        // 显示当前入口密码
        String currentCode = prefs.getString(ENTRY_CODE_KEY, "字码开门");
        tvCurrentCode.setText(currentCode);

        btnSaveCode.setOnClickListener(v -> saveNewCode());

        etNewCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        updateSaveButton();

        return view;
    }

    private void saveNewCode() {
        String newCode = etNewCode.getText().toString().trim();
        if (newCode.isEmpty()) {
            Toast.makeText(getContext(), "入口密码不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newCode.length() < 2) {
            Toast.makeText(getContext(), "入口密码至少2个字符", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存新密码
        prefs.edit().putString(ENTRY_CODE_KEY, newCode).apply();
        tvCurrentCode.setText(newCode);
        etNewCode.setText("");
        Toast.makeText(getContext(), "入口密码已修改为: " + newCode, Toast.LENGTH_LONG).show();
        updateSaveButton();
    }

    private void updateSaveButton() {
        String newCode = etNewCode.getText().toString().trim();
        boolean isValid = !newCode.isEmpty() && newCode.length() >= 2;
        btnSaveCode.setEnabled(isValid);
    }
}
