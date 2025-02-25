package com.app.api_checker;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private BackgroundService backgroundService;
    private boolean isBound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service;
            backgroundService = binder.getService();
            isBound = true;
            // Now you can call methods on backgroundService to pass data
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };
    public static String sharedVariable = null;
    private static final String PREF_NAME1 = "MyPreferences"; // SharedPreferences file name
    public static final String KEY_TEXT1= "savedText"; // Key for saving the text
    public EditText editText;
    private MediaPlayer mediaPlayer;
    private Runnable apiChecker;
    public static SharedPreferences sharedPreferences22;
    private Handler handler = new Handler();
    private Intent serviceIntent;
    private TextView apiResponseText; // Pour afficher la réponse de l’API
    private String target;
    private Handler soundHandler = new Handler();
    private Runnable soundRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        }

        startApp(); // Lancer la logique de l’application

        apiChecker = new Runnable() {        // Lancer la vérification API toutes les 3 sec

            @Override
            public void run() {

                fetchApiResponse();

                handler.postDelayed(this, 3000); // Exécuter toutes les 3 secondes
            }
        };
        handler.post(apiChecker);
    }



    private void startApp() {
        // Récupérer target
        editText = findViewById(R.id.editTextText5);
        Button submitButton = findViewById(R.id.button);
        Button Button2= findViewById(R.id.Button1);

        apiResponseText = findViewById(R.id.TXTREP);

        // Initialiser SharedPreferences
        sharedPreferences22 = getSharedPreferences(PREF_NAME1, MODE_MULTI_PROCESS);
        editText.setText(sharedPreferences22.getString(KEY_TEXT1, "")); // Charger le texte sauvegardé
        fetchApiResponse();

        // Charger le son
        mediaPlayer = MediaPlayer.create(this, R.raw.my_sound);

        // sauvegarder target
        submitButton.setOnClickListener(v -> {
            target = editText.getText().toString();
            if (isBound) {
                backgroundService.setTestUrl(target);
            }
            // Sauvegarder Target dans SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences22.edit();
            editor.putString(KEY_TEXT1, target);
            editor.apply();
            editText.setText(sharedPreferences22.getString(KEY_TEXT1, "")); // Charger le texte sauvegardé
            fetchApiResponse();
            // Afficher une boîte de dialogue
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Saved Target")
                    .setMessage("Your saved Target: " + target)
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        Button2.setOnClickListener(v -> {
            mediaPlayer.stop();
            editText.setText(" ");
            SharedPreferences.Editor editor = sharedPreferences22.edit();
            editor.putString(KEY_TEXT1, " ");
            editor.apply();
            editText.setText(sharedPreferences22.getString(KEY_TEXT1, ""));
            backgroundService.setTestUrl(null);
            apiResponseText.setText("Reponse Json");

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Target")
                    .setMessage("Your Target Rest ")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
            stopService(serviceIntent);
        });

    }




    // Fonction pour appeler l’API et afficher la réponse
    public void fetchApiResponse() {
        String savedText = sharedPreferences22.getString(KEY_TEXT1, "Default Value");
        Log.d("Saved Text", savedText);

        sharedVariable = "https://request-alarmy.vercel.app/request-alarmy/request/?target="+savedText;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(sharedVariable)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("API_ERROR", "Request failed: " + e.getMessage());
                handler.post(() -> apiResponseText.setText("Failed to fetch data"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    final String responseData = response.body().string();
                    Log.d("API_RESPONSE", responseData);

                    handler.post(() -> {
                        apiResponseText.setText(responseData); // Mettre à jour l'UI

                        // Vérification du JSON et déclenchement du son
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String state = jsonResponse.toString();

                            if (state.equals("{\"state\":true}")) {
                                startSoundLoop();
                            }else{
                                stopSoundLoop();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("JSON_ERROR", "Erreur d'analyse JSON");
                        }

                    });
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




}
