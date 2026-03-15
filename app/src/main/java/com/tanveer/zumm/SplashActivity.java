package com.tanveer.zumm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        // 2 सेकंड (2000 ms) का टाइमर
        new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					Intent intent = new Intent(SplashActivity.this, MainActivity.class);
					startActivity(intent);
					finish(); // Splash screen बंद कर देंगे
				}
			}, 2000);
    }
}

