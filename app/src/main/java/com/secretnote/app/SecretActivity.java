package com.secretnote.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecretActivity extends AppCompatActivity {

    private static final int PICK_IMAGES_REQUEST = 100;
    private static final int PICK_XBB_FILE_REQUEST = 101;
    private static final int PERMISSION_REQUEST_CODE = 200;

    private LinearLayout imageContainer;
    private File cacheDir;
    private List<String> cacheFiles = new ArrayList<>();
    private static final String PREFS_NAME = "NotePrefs";
    private static final String ENTRY_CODE_KEY = "entry_code";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secret);

        imageContainer = findViewById(R.id.imageContainer);
        Button btnEncrypt = findViewById(R.id.btnEncrypt);
        Button btnDecrypt = findViewById(R.id.btnDecrypt);
        Button btnSettings = findViewById(R.id.btnSettings);

        cacheDir = new File(getCacheDir(), "secret_images");
        cacheDir.mkdirs();

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnEncrypt.setOnClickListener(v -> pickImages());
        btnDecrypt.setOnClickListener(v -> pickXbbFiles());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void pickImages() {
        showEncryptDialog();
    }

    private void showEncryptDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_encrypt, null);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        CheckBox cbDeleteOriginal = dialogView.findViewById(R.id.cbDeleteOriginal);

        builder.setTitle("加密图片")
                .setView(dialogView)
                .setPositiveButton("选择图片", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    launchImagePicker(password, cbDeleteOriginal.isChecked());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void launchImagePicker(String password, boolean deleteOriginal) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择图片"), PICK_IMAGES_REQUEST);
    }

    private void pickXbbFiles() {
        showDecryptDialog();
    }

    private void showDecryptDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_decrypt, null);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);

        builder.setTitle("解密图片")
                .setView(dialogView)
                .setPositiveButton("选择文件", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    launchXbbPicker(password);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void launchXbbPicker(String password) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择.xbb文件"), PICK_XBB_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_IMAGES_REQUEST) {
            handleSelectedImages(data);
        } else if (requestCode == PICK_XBB_FILE_REQUEST) {
            handleSelectedXbbFiles(data);
        }
    }

    private void handleSelectedImages(Intent data) {
        List<Uri> imageUris = new ArrayList<>();

        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                imageUris.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            imageUris.add(data.getData());
        }

        if (imageUris.isEmpty()) return;

        // 显示密码对话框并开始加密
        showPasswordAndEncryptDialog(imageUris);
    }

    private void showPasswordAndEncryptDialog(List<Uri> imageUris) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_encrypt, null);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        CheckBox cbDeleteOriginal = dialogView.findViewById(R.id.cbDeleteOriginal);

        builder.setTitle("加密 " + imageUris.size() + " 张图片")
                .setView(dialogView)
                .setPositiveButton("开始加密", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(() -> encryptImages(imageUris, password, cbDeleteOriginal.isChecked())).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void encryptImages(List<Uri> imageUris, String password, boolean deleteOriginal) {
        int successCount = 0;
        int failCount = 0;

        for (Uri imageUri : imageUris) {
            try {
                byte[] imageBytes = getBytesFromUri(imageUri);
                String fileName = getFileNameFromUri(imageUri);

                if (imageBytes == null || fileName == null) continue;

                String xbbContent = encryptImage(imageBytes, password);
                saveXbbFile(fileName, xbbContent);

                if (deleteOriginal) {
                    deleteFileFromUri(imageUri);
                }

                successCount++;
            } catch (Exception e) {
                e.printStackTrace();
                failCount++;
            }
        }

        final int finalSuccess = successCount;
        final int finalFail = failCount;

        runOnUiThread(() -> {
            String message = "加密完成!\n成功: " + finalSuccess + " 个\n失败: " + finalFail + " 个";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String encryptImage(byte[] imageBytes, String password) throws Exception {
        SecretKeySpec keySpec = generateKey(password);
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

        byte[] encrypted = cipher.doFinal(imageBytes);
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        String encoded = Base64.getEncoder().encodeToString(combined);
        return "XBB_ENC_V1\n" + encoded;
    }

    private byte[] decryptImage(String xbbContent, String password) throws Exception {
        String[] lines = xbbContent.split("\n");
        if (lines.length < 2 || !lines[0].equals("XBB_ENC_V1")) {
            throw new Exception("Invalid file format");
        }

        String base64 = lines[1];
        byte[] combined = Base64.getDecoder().decode(base64);

        byte[] iv = new byte[12];
        byte[] encrypted = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = generateKey(password);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

        return cipher.doFinal(encrypted);
    }

    private SecretKeySpec generateKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    private void saveXbbFile(String originalFileName, String xbbContent) throws IOException {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String xbbFileName = baseName + ".xbb";

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File xbbFile = new File(downloadDir, xbbFileName);

        try (FileOutputStream fos = new FileOutputStream(xbbFile)) {
            fos.write(xbbContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleSelectedXbbFiles(Intent data) {
        List<Uri> xbbUris = new ArrayList<>();

        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                xbbUris.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            xbbUris.add(data.getData());
        }

        if (xbbUris.isEmpty()) return;

        showPasswordAndDecryptDialog(xbbUris);
    }

    private void showPasswordAndDecryptDialog(List<Uri> xbbUris) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_decrypt, null);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);

        builder.setTitle("解密 " + xbbUris.size() + " 个文件")
                .setView(dialogView)
                .setPositiveButton("开始解密", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(() -> decryptAndDisplayImages(xbbUris, password)).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void decryptAndDisplayImages(List<Uri> xbbUris, String password) {
        imageContainer.post(() -> imageContainer.removeAllViews());

        int successCount = 0;
        int failCount = 0;

        for (Uri xbbUri : xbbUris) {
            try {
                String xbbContent = readTextFromUri(xbbUri);
                byte[] imageBytes = decryptImage(xbbContent, password);

                // 保存到缓存
                File cacheFile = new File(cacheDir, "image_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(imageBytes);
                }
                cacheFiles.add(cacheFile.getAbsolutePath());

                // 显示图片
                final String cachePath = cacheFile.getAbsolutePath();
                runOnUiThread(() -> addImageToView(cachePath));

                successCount++;
            } catch (Exception e) {
                e.printStackTrace();
                failCount++;
            }
        }

        final int finalSuccess = successCount;
        final int finalFail = failCount;

        runOnUiThread(() -> {
            String message = "解密完成!\n成功: " + finalSuccess + " 个\n失败: " + finalFail + " 个";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void addImageToView(String imagePath) {
        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
        );
        params.setMargins(0, 0, 0, 16);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        imageContainer.addView(imageView);
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) return null;

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void deleteFileFromUri(Uri uri) {
        try {
            getContentResolver().delete(uri, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        TextView tvCurrentCode = dialogView.findViewById(R.id.tvCurrentCode);
        EditText etNewCode = dialogView.findViewById(R.id.etNewCode);

        // 显示当前入口密码
        String currentCode = prefs.getString(ENTRY_CODE_KEY, "字码开门");
        tvCurrentCode.setText(currentCode);

        builder.setTitle("设置入口密码")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newCode = etNewCode.getText().toString().trim();
                    if (newCode.isEmpty()) {
                        Toast.makeText(this, "入口密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newCode.length() < 2) {
                        Toast.makeText(this, "入口密码至少2个字符", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 保存新密码
                    prefs.edit().putString(ENTRY_CODE_KEY, newCode).apply();
                    Toast.makeText(this, "入口密码已修改为: " + newCode, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearCache();
    }

    private void clearCache() {
        for (String filePath : cacheFiles) {
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        cacheFiles.clear();

        // 清理缓存目录
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能使用功能", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
