package com.mayak.wordfinder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.PrintWriter;
import java.io.StringWriter;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public GlobalExceptionHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Получаем стек ошибки
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String errorText = sw.toString();

        // Копируем в буфер обмена
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Ошибка приложения", errorText);
        clipboard.setPrimaryClip(clip);

        // Показываем уведомление
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, "❌ Ошибка скопирована в буфер обмена!", Toast.LENGTH_LONG).show()
        );

        // Передаём ошибку дальше (чтобы приложение закрылось)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }
}