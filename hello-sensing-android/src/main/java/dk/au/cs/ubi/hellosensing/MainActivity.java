package dk.au.cs.ubi.hellosensing;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register our toggle button
        ToggleButton serviceToggleButton = (ToggleButton) findViewById(R.id.sensingToggleButton);
        serviceToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startService(new Intent(getApplicationContext(), SensingService.class));
                } else {
                    stopService(new Intent(getApplicationContext(), SensingService.class));
                }
            }
        });
    }
}
