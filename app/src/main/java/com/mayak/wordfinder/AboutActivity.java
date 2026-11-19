package com.mayak.wordfinder;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // КНОПКА ЗАКРЫТЬ
        TextView closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish());

        TextView aboutText = findViewById(R.id.aboutText);
        aboutText.setText(getAboutText());
    }

    private String getAboutText() {
        return " Маяк\nВерсия 1.0\n\n" +
               "Название Маяк придумал:\nИванов Петя\n\n" +
               "Идею для Дизайна придумал:\nБугреев Ярослав\n\n" +
               "Набор текста:\nКорниенко Саша\n\n" +
               "Разработка:\nDeepSeek (искусственный интеллект)\n\n" +
               "Особая благодарность:\nВсем, кто верил в этот проект!";
    }
}