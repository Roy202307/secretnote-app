package com.secretnote.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.gridlayout.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptFragment extends Fragment {

    private static final int PICK_XBB_FILE_REQUEST = 101;
    private static final long LONG_PRESS_THRESHOLD = 500;

    private EditText etDecryptKey;
    private TextView tvSelectedFiles;
    private Button btnDecrypt;
    private GridLayout gridImages;
    private LinearLayout selectionBar;
    private TextView tvSelectionCount;
    private Button btnDeleteSelected;
    private List<Uri> selectedFiles = new ArrayList<>();
    private File cacheDir;
    private List<String> cacheFiles = new ArrayList<>();

    private boolean isSelectionMode = false;
    private Map<ImageView, ImageData> imageDataMap = new HashMap<>();
    private List<ImageView> selectedImages = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_decrypt, container, false);

        etDecryptKey = view.findViewById(R.id.etDecryptKey);
        tvSelectedFiles = view.findViewById(R.id.tvSelectedFiles);
        Button btnSelectFiles = view.findViewById(R.id.btnSelectFiles);
        btnDecrypt = view.findViewById(R.id.btnDecrypt);
        gridImages = view.findViewById(R.id.gridImages);
        selectionBar = view.findViewById(R.id.selectionBar);
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);

        cacheDir = new File(requireContext().getCacheDir(), "secret_images");
        cacheDir.mkdirs();

        btnSelectFiles.setOnClickListener(v -> selectFiles());
        btnDecrypt.setOnClickListener(v -> decryptFiles());
        btnDeleteSelected.setOnClickListener(v -> deleteSelectedFiles());

        updateDecryptButton();

        return view;
    }

    private void selectFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_XBB_FILE_REQUEST);
    }

    private void decryptFiles() {
        String key = etDecryptKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "请输入秘钥", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFiles.isEmpty()) {
            Toast.makeText(getContext(), "请选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            gridImages.post(() -> {
                gridImages.removeAllViews();
                imageDataMap.clear();
                selectedImages.clear();
                isSelectionMode = false;
                selectionBar.setVisibility(View.GONE);
            });
            cacheFiles.clear();

            int successCount = 0;
            int failCount = 0;

            for (Uri fileUri : selectedFiles) {
                try {
                    String xbbContent = readTextFromUri(fileUri);
                    byte[] imageBytes = decryptImage(xbbContent, key);

                    // 保存到缓存
                    File cacheFile = new File(cacheDir, "image_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(imageBytes);
                    }
                    cacheFiles.add(cacheFile.getAbsolutePath());

                    // 显示图片
                    final String cachePath = cacheFile.getAbsolutePath();
                    final Uri sourceUri = fileUri;
                    gridImages.post(() -> addImageToView(cachePath, sourceUri));

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
                    String message = "解密完成!\n成功: " + finalSuccess + " 个\n失败: " + finalFail + " 个";
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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

    private String readTextFromUri(Uri uri) throws IOException {
        Activity activity = getActivity();
        if (activity == null) return "";

        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void addImageToView(String imagePath, Uri sourceUri) {
        CardView cardView = new CardView(getContext());
        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.height = 300;
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(4, 4, 4, 4);
        cardView.setLayoutParams(cardParams);
        cardView.setRadius(8);
        cardView.setCardElevation(4);

        ImageView imageView = new ImageView(getContext());
        android.view.ViewGroup.LayoutParams imageParams = new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        );
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(true);
        imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath));

        // 保存图片数据
        ImageData imageData = new ImageData(imagePath, sourceUri);
        imageDataMap.put(imageView, imageData);

        // 长按事件
        setupLongPress(imageView);

        // 点击事件
        imageView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleImageSelection(imageView);
            } else {
                showFullScreenImage(imagePath);
            }
        });

        cardView.addView(imageView);
        gridImages.addView(cardView);
    }

    private void setupLongPress(ImageView imageView) {
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private long pressStartTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_UP:
                        long pressDuration = System.currentTimeMillis() - pressStartTime;
                        if (pressDuration > LONG_PRESS_THRESHOLD && pressDuration < 1000) {
                            enterSelectionMode(imageView);
                            return true;
                        }
                        // 短按,传递给点击事件
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void enterSelectionMode(ImageView initialImageView) {
        isSelectionMode = true;
        selectionBar.setVisibility(View.VISIBLE);
        toggleImageSelection(initialImageView);
        Toast.makeText(getContext(), "进入选择模式", Toast.LENGTH_SHORT).show();
    }

    private void toggleImageSelection(ImageView imageView) {
        if (selectedImages.contains(imageView)) {
            selectedImages.remove(imageView);
            imageView.setColorFilter(null);
        } else {
            selectedImages.add(imageView);
            imageView.setColorFilter(Color.argb(128, 0, 0, 255)); // 半透明蓝色遮罩
        }
        updateSelectionCount();
    }

    private void updateSelectionCount() {
        tvSelectionCount.setText("已选择: " + selectedImages.size());
        btnDeleteSelected.setEnabled(!selectedImages.isEmpty());
    }

    private void deleteSelectedFiles() {
        if (selectedImages.isEmpty()) {
            return;
        }

        Activity activity = getActivity();
        if (activity == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("删除文件")
                .setMessage("确定要删除选中的 " + selectedImages.size() + " 个文件吗?")
                .setPositiveButton("删除", (dialog, which) -> {
                    int deleteCount = 0;
                    for (ImageView imageView : new ArrayList<>(selectedImages)) {
                        ImageData imageData = imageDataMap.get(imageView);
                        if (imageData != null) {
                            try {
                                activity.getContentResolver().delete(imageData.sourceUri, null, null);
                                deleteCount++;

                                // 从视图中移除
                                View parent = (View) imageView.getParent();
                                if (parent != null) {
                                    View grandParent = (View) parent.getParent();
                                    if (grandParent != null) {
                                        gridImages.removeView(grandParent);
                                    }
                                }

                                imageDataMap.remove(imageView);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    Toast.makeText(getContext(), "已删除 " + deleteCount + " 个文件", Toast.LENGTH_SHORT).show();
                    exitSelectionMode();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedImages.clear();
        selectionBar.setVisibility(View.GONE);

        // 清除所有图片的选择状态
        for (ImageView imageView : imageDataMap.keySet()) {
            imageView.setColorFilter(null);
        }
    }

    private void showFullScreenImage(String imagePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        ImageView imageView = new ImageView(getContext());

        // 加载图片并调整大小
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int scale = 1;
        if (options.outWidth > screenWidth) {
            scale = (int) Math.ceil((double) options.outWidth / screenWidth);
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        imageView.setImageBitmap(bitmap);

        builder.setView(imageView);
        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    private void updateSelectedCount() {
        tvSelectedFiles.setText("已选择: " + selectedFiles.size() + " 个文件");
    }

    private void updateDecryptButton() {
        String key = etDecryptKey.getText().toString().trim();
        boolean hasKey = !key.isEmpty();
        boolean hasFiles = !selectedFiles.isEmpty();
        btnDecrypt.setEnabled(hasKey && hasFiles);
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
    public void onDestroy() {
        super.onDestroy();
        clearCache();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == PICK_XBB_FILE_REQUEST) {
            selectedFiles.clear();

            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    // 只选择 .xbb 文件
                    if (uri.toString().toLowerCase().endsWith(".xbb")) {
                        selectedFiles.add(uri);
                    }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                if (uri.toString().toLowerCase().endsWith(".xbb")) {
                    selectedFiles.add(uri);
                }
            }

            updateSelectedCount();
            updateDecryptButton();
        }
    }

    static class ImageData {
        String cachePath;
        Uri sourceUri;

        ImageData(String cachePath, Uri sourceUri) {
            this.cachePath = cachePath;
            this.sourceUri = sourceUri;
        }
    }
}
