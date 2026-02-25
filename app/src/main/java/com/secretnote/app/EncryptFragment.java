package com.secretnote.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptFragment extends Fragment {

    private static final int PICK_IMAGES_REQUEST = 100;
    private EditText etEncryptKey;
    private TextView tvSelectedImages;
    private Button btnEncrypt;
    private List<Uri> selectedImages = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_encrypt, container, false);

        etEncryptKey = view.findViewById(R.id.etEncryptKey);
        tvSelectedImages = view.findViewById(R.id.tvSelectedImages);
        Button btnSelectImages = view.findViewById(R.id.btnSelectImages);
        btnEncrypt = view.findViewById(R.id.btnEncrypt);

        btnSelectImages.setOnClickListener(v -> selectImages());
        btnEncrypt.setOnClickListener(v -> encryptImages());

        updateEncryptButton();

        return view;
    }

    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGES_REQUEST);
    }

    private void encryptImages() {
        String key = etEncryptKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "请输入秘钥", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImages.isEmpty()) {
            Toast.makeText(getContext(), "请选择图片", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;

            for (Uri imageUri : selectedImages) {
                try {
                    byte[] imageBytes = getBytesFromUri(imageUri);
                    String fileName = getFileNameFromUri(imageUri);

                    if (imageBytes == null || fileName == null) continue;

                    String xbbContent = encryptImage(imageBytes, key);
                    saveXbbFile(fileName, xbbContent);

                    successCount++;
                } catch (Exception e) {
                    e.printStackTrace();
                    failCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFail = failCount;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    String message = "加密完成!\n成功: " + finalSuccess + " 个\n失败: " + finalFail + " 个";
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                    selectedImages.clear();
                    updateSelectedCount();
                    updateEncryptButton();
                });
            }
        }).start();
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

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        Activity activity = getActivity();
        if (activity == null) return null;

        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
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

    private String getFileNameFromUri(Uri uri) {
        Activity activity = getActivity();
        if (activity == null) return null;

        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
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

    private void updateSelectedCount() {
        tvSelectedImages.setText("已选择: " + selectedImages.size() + " 张图片");
    }

    private void updateEncryptButton() {
        String key = etEncryptKey.getText().toString().trim();
        boolean hasKey = !key.isEmpty();
        boolean hasImages = !selectedImages.isEmpty();
        btnEncrypt.setEnabled(hasKey && hasImages);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == PICK_IMAGES_REQUEST) {
            selectedImages.clear();

            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    selectedImages.add(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                selectedImages.add(data.getData());
            }

            updateSelectedCount();
            updateEncryptButton();
        }
    }
}
