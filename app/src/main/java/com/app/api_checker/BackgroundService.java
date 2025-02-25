package com.app.api_checker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class BackgroundService extends Service {
    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String KEY_TEST_URL = "testUrl";
    private final IBinder binder = new LocalBinder();


    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }

    private String testUrl;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable apiChecker;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // Récupérer la valeur depuis les SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        testUrl = sharedPreferences.getString(KEY_TEST_URL, null);

        // Initialiser MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.my_sound);

        // Lancer la vérification API toutes les 3 secondes
        apiChecker = new Runnable() {
            @Override
            public void run() {
                checkApi();
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(apiChecker);
    }

    public void setTestUrl(String value) {
        this.testUrl = value;
        // Enregistrer Target dans les SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_TEST_URL, value);
        editor.apply();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("API Checker en cours...")
                .setContentText("Vérification de l'état...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void checkApi() {
        if (testUrl == null || testUrl.isEmpty()) {
            Log.e("API_ERROR", "L'URL de l'API est null ou vide !");
            return;
        }

        String apiUrl = "https://request-alarmy.vercel.app/request-alarmy/request/?target=" + testUrl;
        Log.d("API_URL", apiUrl);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(apiUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("API_ERROR", "Erreur requête : " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    Log.d("API_RESPONSE", responseData);
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        boolean state = jsonResponse.getBoolean("state");
                        if (state) {
                            startSoundLoop();
                        } else {
                            stopSoundLoop();
                        }
                    } catch (Exception e) {
                        Log.e("JSON_ERROR", "Erreur d'analyse JSON", e);
                    }
                }
            }
        });
    }

    private void startSoundLoop() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopSoundLoop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Service de fond",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
