package org.mccurley.first.ledbadge;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class InputActivity extends AppCompatActivity  {
    private EditText textField;
    private Spinner speedSpinner, modeSpinner;
    private static final int SPEECH_INPUT_REQUEST = 5522;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> speechResults;

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
                startActivityForResult(intent, SPEECH_INPUT_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input);
        ImageButton speechButton = (ImageButton) findViewById(R.id.voice_button);
        if (speechButton != null) {
            speechButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptSpeechInput();
                }
            });
        }
        speechResults = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(InputActivity.this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1, speechResults);
        final ListView speechList = (ListView) findViewById(R.id.speech_results);
        if (speechList != null) {
            speechList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v,
                                        int position, long id) {
                    String value = (String) speechList.getItemAtPosition(position);
                    textField.setText(value);
                }
            });
            speechList.setAdapter(adapter);
        }
        textField = (EditText) findViewById(R.id.text);
        textField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    if (textField.getText() != null &&
                        textField.getText().toString().length() > 0) {
                        Intent data = new Intent();
                        data.putExtra(MainActivity.TEXT, textField.getText().toString());
                        setResult(RESULT_OK, data);
                        finish();
                    }
                }
                return false;
            }
        });
        speedSpinner = (Spinner) findViewById(R.id.speed);
        modeSpinner = (Spinner) findViewById(R.id.display_mode);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_INPUT_REQUEST &&
                resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && results.size() > 0) {
                Log.d("InputActivity", "Got " + results.size() + " results");
                textField.setText(results.get(0));
                results.remove(0);
                speechResults.clear();
                speechResults.addAll(results);
                adapter.notifyDataSetChanged();
                textField.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }
}
