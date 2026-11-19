package com.mayak.wordfinder;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileViewerActivity extends Activity {

    private TextView titleView;
    private LinearLayout contentContainer;
    private ImageView btnPlus, btnMinus;

    private String currentFileName;
    private String currentCategory = "Все";
    private ArrayList<String> allSongs = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isTransitioning = false;
    private SharedPreferences prefs;
    private float currentTextSize = 16f;

    private GestureDetector gestureDetector;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        setContentView(R.layout.activity_file_viewer);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Mayak:KeepScreenOn");
        wakeLock.acquire(10 * 60 * 1000L);

        titleView = findViewById(R.id.fileTitle);
        contentContainer = findViewById(R.id.contentContainer);
        btnPlus = findViewById(R.id.btnPlus);
        btnMinus = findViewById(R.id.btnMinus);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (e1.getX() < 150 && Math.abs(diffX) > Math.abs(diffY) && diffX > 150) {
                    finish();
                    return true;
                }
                return false;
            }
        });

        prefs = getSharedPreferences("text_size", MODE_PRIVATE);

        currentFileName = getIntent().getStringExtra("file_name");
        currentCategory = getIntent().getStringExtra("category");
        if (currentCategory == null) currentCategory = "Все";

        currentTextSize = prefs.getFloat("size_" + currentFileName, 16f);

        loadAllSongs();

        if (currentFileName != null) {
            String displayName = currentFileName.replace(".txt", "");
            currentIndex = findIndex(displayName);
        }

        loadSong(currentFileName);

        btnPlus.setOnClickListener(v -> {
            currentTextSize = Math.min(currentTextSize + 2f, 40f);
            applyTextSize();
        });

        btnMinus.setOnClickListener(v -> {
            currentTextSize = Math.max(currentTextSize - 2f, 8f);
            applyTextSize();
        });

        hideNavigation();
    }

    public void setTextSize(int size) {
        currentTextSize = size;
        applyTextSize();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideNavigation();
        }
    }

    private void hideNavigation() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    public void refreshCurrentText() {
        if (currentFileName != null) {
            loadSong(currentFileName);
        }
    }

    private void loadAllSongs() {
        try {
            String path;
            if (currentCategory.equals("Свадебные")) {
                path = "songs/Свадебные/";
            } else {
                path = "songs/";
            }
            String[] files = getAssets().list(path);
            if (files != null && files.length > 0) {
                for (String file : files) {
                    if (file.endsWith(".txt")) {
                        String displayName = file.replace(".txt", "");
                        allSongs.add(displayName);
                    }
                }
                Collections.sort(allSongs, new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        int num1 = extractNumber(s1);
                        int num2 = extractNumber(s2);
                        return Integer.compare(num1, num2);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int extractNumber(String name) {
        try {
            String numStr = "";
            for (char c : name.toCharArray()) {
                if (Character.isDigit(c)) {
                    numStr += c;
                } else {
                    break;
                }
            }
            if (!numStr.isEmpty()) {
                return Integer.parseInt(numStr);
            }
        } catch (Exception e) {
            return 9999;
        }
        return 9999;
    }

    private int findIndex(String displayName) {
        for (int i = 0; i < allSongs.size(); i++) {
            if (allSongs.get(i).equals(displayName)) {
                return i;
            }
        }
        return 0;
    }

    private void loadSong(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String displayName = fileName.replace(".txt", "");
            titleView.setText(displayName);

            String path;
            if (currentCategory.equals("Свадебные")) {
                path = "songs/Свадебные/" + fileName;
            } else {
                path = "songs/" + fileName;
            }

            InputStream is = getAssets().open(path);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder fullText = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                fullText.append(line).append("\n");
            }
            br.close();
            is.close();

            String text = fullText.toString();
            contentContainer.removeAllViews();

            Pattern pattern = Pattern.compile("\\[([^\\]]*)\\]");
            Matcher matcher = pattern.matcher(text);

            int lastEnd = 0;
            while (matcher.find()) {
                String before = text.substring(lastEnd, matcher.start());
                if (!before.trim().isEmpty()) {
                    addNormalText(before);
                }

                String quotedText = matcher.group(1).trim();
                if (!quotedText.isEmpty()) {
                    addGoldText(quotedText);
                }

                lastEnd = matcher.end();
            }

            String after = text.substring(lastEnd);
            if (!after.trim().isEmpty()) {
                addNormalText(after);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка чтения: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addNormalText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(currentTextSize);
        tv.setPadding(0, 4, 0, 4);
        tv.setTypeface(null, android.graphics.Typeface.NORMAL);
        contentContainer.addView(tv);
    }

    private void addGoldText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(android.graphics.Color.parseColor("#FFD700"));
        tv.setTextSize(currentTextSize);
        tv.setPadding(0, 4, 0, 4);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        contentContainer.addView(tv);
    }

    private void applyTextSize() {
        prefs.edit().putFloat("size_" + currentFileName, currentTextSize).apply();
        loadSong(currentFileName);
    }

    private void goToNextSong() {
        if (allSongs.isEmpty() || isTransitioning) return;
        isTransitioning = true;
        currentIndex++;
        if (currentIndex >= allSongs.size()) {
            currentIndex = 0;
        }
        currentFileName = allSongs.get(currentIndex) + ".txt";
        loadSong(currentFileName);
        new Handler().postDelayed(() -> isTransitioning = false, 300);
    }

    private void goToPreviousSong() {
        if (allSongs.isEmpty() || isTransitioning) return;
        isTransitioning = true;
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = allSongs.size() - 1;
        }
        currentFileName = allSongs.get(currentIndex) + ".txt";
        loadSong(currentFileName);
        new Handler().postDelayed(() -> isTransitioning = false, 300);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            goToPreviousSong();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            goToNextSong();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}