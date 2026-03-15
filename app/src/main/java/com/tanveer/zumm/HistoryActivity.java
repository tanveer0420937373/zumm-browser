package com.tanveer.zumm;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class HistoryActivity extends Activity {

    ListView listView;
    Button btnClear;
    ArrayList<String> historyList;
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);

        listView = (ListView) findViewById(R.id.historyList);
        btnClear = (Button) findViewById(R.id.btnClear);

        loadHistory();

        // लिस्ट पर क्लिक करने पर वेबसाइट खोलना
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					String url = historyList.get(position);
					Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
					intent.putExtra("open_url", url); // URL भेजो
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // पुराना पेज हटाओ
					startActivity(intent);
					finish();
				}
			});

        // हिस्ट्री डिलीट करना
        btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					SharedPreferences pref = getSharedPreferences("ZummData", MODE_PRIVATE);
					pref.edit().remove("historyList").apply();
					historyList.clear();
					adapter.notifyDataSetChanged();
					Toast.makeText(HistoryActivity.this, "Cleared!", Toast.LENGTH_SHORT).show();
				}
			});
    }

    private void loadHistory() {
        SharedPreferences pref = getSharedPreferences("ZummData", MODE_PRIVATE);
        Set<String> set = pref.getStringSet("historyList", new HashSet<String>());
        historyList = new ArrayList<String>(set);

        // लिस्ट को उल्टा करें (ताकि नई हिस्ट्री ऊपर दिखे)
        // Collections.reverse(historyList); 

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, historyList);
        listView.setAdapter(adapter);
    }
}

