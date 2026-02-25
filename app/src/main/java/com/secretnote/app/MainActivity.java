package com.secretnote.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText noteEditor;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "NotePrefs";
    private static final String NOTE_KEY = "saved_note";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteEditor = findViewById(R.id.noteEditor);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 加载保存的笔记
        noteEditor.setText(prefs.getString(NOTE_KEY, ""));

        // 监听输入，检测隐藏入口
        noteEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString().trim();
                if (text.endsWith("字码开门")) {
                    noteEditor.setText("");
                    openSecretActivity();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 自动保存笔记
                prefs.edit().putString(NOTE_KEY, s.toString()).apply();
            }
        });
    }

    private void openSecretActivity() {
        Intent intent = new Intent(this, SecretActivity.class);
        startActivity(intent);
    }
}
