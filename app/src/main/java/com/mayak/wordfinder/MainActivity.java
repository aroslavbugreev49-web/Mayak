package com.mayak.wordfinder;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> allSongs = new ArrayList<>();
    private ArrayList<String> filteredSongs = new ArrayList<>();
    private ArrayList<String> allSongContents = new ArrayList<>();

    private ImageView micButton;
    private LinearLayout micPanel;
    private TextView micStatusText;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private String currentCategory = "Все";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }

        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(android.R.color.transparent);

        micButton = findViewById(R.id.micButton);
        micPanel = findViewById(R.id.micPanel);
        micStatusText = findViewById(R.id.micStatusText);

        loadSongsFromCategory("Все");

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Голосовой ввод недоступен", Toast.LENGTH_LONG).show();
            micButton.setAlpha(0.3f);
            micButton.setEnabled(false);
            return;
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                micStatusText.setText("🎤 Слушаю...");
                micButton.setImageResource(R.drawable.ic_mic_anim);
            }

            @Override
            public void onBeginningOfSpeech() {
                micStatusText.setText("📢 Говорите...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                micStatusText.setText("⏳ Обработка...");
            }

            @Override
            public void onError(int error) {
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        msg = "Не распознано";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "Речь не обнаружена";
                        break;
                    default:
                        msg = "Ошибка распознавания";
                        break;
                }
                micStatusText.setText("❌ " + msg);
                micButton.setImageResource(R.drawable.ic_mic);
                isListening = false;
                micPanel.postDelayed(() -> micPanel.setVisibility(View.GONE), 2000);
            }

            @Override
            public void onResults(Bundle results) {
                micButton.setImageResource(R.drawable.ic_mic);
                isListening = false;

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    micStatusText.setText("🔍 Ищу: " + spokenText);
                    searchSong(spokenText);
                    micPanel.postDelayed(() -> micPanel.setVisibility(View.GONE), 1500);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        micButton.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
                micPanel.setVisibility(View.GONE);
            } else {
                micPanel.setVisibility(View.VISIBLE);
                micStatusText.setText("🎤 Слушаю...");
                startListening();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            micPanel.setVisibility(View.VISIBLE);
            micStatusText.setText("🎤 Слушаю...");
            startListening();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            micPanel.setVisibility(View.GONE);
            stopListening();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ===== НОВЫЙ МЕТОД ПОИСКА (НОРМАЛЬНЫЙ) =====
    private void searchSong(String query) {
        String lowerQuery = query.toLowerCase().trim();
        
        // 1. Ищем по номеру
        Pattern pattern = Pattern.compile("\\d+");
        java.util.regex.Matcher matcher = pattern.matcher(lowerQuery);
        if (matcher.find()) {
            String number = matcher.group();
            for (String name : allSongs) {
                if (name.startsWith(number)) {
                    openSong(name + ".txt");
                    return;
                }
            }
        }

        // 2. Ищем по точному совпадению фразы в тексте
        for (int i = 0; i < allSongs.size(); i++) {
            String content = allSongContents.get(i);
            if (content.contains(lowerQuery)) {
                openSong(allSongs.get(i) + ".txt");
                return;
            }
        }

        // 3. Ищем по частям фразы (слова подряд)
        String[] queryWords = lowerQuery.split(" ");
        if (queryWords.length >= 2) {
            for (int i = 0; i < allSongs.size(); i++) {
                String content = allSongContents.get(i);
                for (int j = 0; j < queryWords.length - 1; j++) {
                    String phrase = queryWords[j] + " " + queryWords[j + 1];
                    if (content.contains(phrase)) {
                        openSong(allSongs.get(i) + ".txt");
                        return;
                    }
                }
            }
        }

        // 4. Ищем по названию песни
        for (String name : allSongs) {
            if (name.toLowerCase().contains(lowerQuery)) {
                openSong(name + ".txt");
                return;
            }
        }

        // 5. Ищем по всем словам
        String[] words = lowerQuery.split(" ");
        int bestMatchIndex = -1;
        int bestMatchCount = 0;

        for (int i = 0; i < allSongs.size(); i++) {
            String content = allSongContents.get(i);
            int matchCount = 0;
            for (String word : words) {
                if (word.length() > 2 && content.contains(word)) {
                    matchCount++;
                }
            }
            if (matchCount > bestMatchCount) {
                bestMatchCount = matchCount;
                bestMatchIndex = i;
            }
        }

        if (bestMatchIndex >= 0 && bestMatchCount >= words.length / 2) {
            openSong(allSongs.get(bestMatchIndex) + ".txt");
            return;
        }

        Toast.makeText(this, "❌ Ничего не найдено. Попробуйте другие слова.", Toast.LENGTH_LONG).show();
    }

    private void openSong(String fileName) {
        Intent intent = new Intent(MainActivity.this, FileViewerActivity.class);
        intent.putExtra("file_name", fileName);
        intent.putExtra("category", currentCategory);
        startActivity(intent);
    }

    private void startListening() {
        if (speechRecognizer == null) return;
        isListening = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите номер или фразу из песни...");
        
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        micButton.setImageResource(R.drawable.ic_mic);
    }

    private void loadSongsFromCategory(String category) {
        allSongs.clear();
        filteredSongs.clear();
        allSongContents.clear();

        try {
            String path = category.equals("Свадебные") ? "songs/Свадебные/" : "songs/";

            String[] files = getAssets().list(path);
            if (files != null && files.length > 0) {
                for (String file : files) {
                    if (file.endsWith(".txt")) {
                        String name = file.replace(".txt", "");
                        allSongs.add(name);
                        String content = readSongFile(path + file);
                        allSongContents.add(content.toLowerCase());
                    }
                }
                Collections.sort(allSongs, (a, b) -> {
                    int numA = extractNumber(a);
                    int numB = extractNumber(b);
                    return Integer.compare(numA, numB);
                });
                filteredSongs.addAll(allSongs);
            }

            if (allSongs.isEmpty()) {
                filteredSongs.add("В этой папке нет песен");
            }

        } catch (Exception e) {
            e.printStackTrace();
            filteredSongs.add("Ошибка загрузки");
        }

        updateListView();
    }

    private void updateListView() {
        if (filteredSongs.isEmpty()) {
            return;
        }

        adapter = new ArrayAdapter<String>(this, R.layout.list_item_song, R.id.songTitle, filteredSongs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                TextView numberView = view.findViewById(R.id.songNumber);
                TextView titleView = view.findViewById(R.id.songTitle);

                String item = filteredSongs.get(position);

                String number = extractNumberString(item);
                numberView.setText(number);

                String title = item.replaceAll("^\\d+[\\.\\s-]*", "");
                titleView.setText(title);

                view.setOnClickListener(v -> {
                    String fileName = item + ".txt";
                    openSong(fileName);
                });

                return view;
            }
        };

        listView.setAdapter(adapter);
    }

    private String readSongFile(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(" ");
            }
            br.close();
            is.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
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

    private String extractNumberString(String name) {
        try {
            String numStr = "";
            for (char c : name.toCharArray()) {
                if (Character.isDigit(c)) {
                    numStr += c;
                } else {
                    break;
                }
            }
            return numStr;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}