package com.kll.collect.android.activities;

import com.kll.collect.android.R;
import com.kll.collect.android.application.Collect;
import com.kll.collect.android.preferences.AdminPreferencesActivity;
import com.kll.collect.android.preferences.MapSettings;
import com.kll.collect.android.preferences.PreferencesActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

public class MainSettingsActivity extends Activity implements OnItemClickListener  {
	ListView listView;
	public static String ADMIN_PREFERENCES = "admin_preference";
	final static int form_settings_id = 0;
	final static int general_settings_id = 1;
	final static int admin_settings_id = 2;
	final static int map_settings_id = 3;
	final static int gps_setting_id = 4;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_settings_layout);
		setTitle(getString(R.string.app_name) + " > Settings"); // Setting title of the action
		listView = (ListView) findViewById(R.id.mapSettingsList);
		listView.setOnItemClickListener(this);


	}



	@Override
	public void onItemClick(AdapterView<?> adpater, View view, int position,
							long id) {
		launchSettingActivity(position);

	}

	private void launchSettingActivity(int position) {
		// TODO Auto-generated method stub
		Collect.getInstance().getActivityLogger().logAction(this, "SettingsClicked", "click");
		Intent i = null;
		//Toast.makeText(this,position +" ", Toast.LENGTH_SHORT).show();
		if (position == 0){
			i = new Intent(getApplicationContext(),FormDownloadList.class);
			startActivity(i);
		}else if(position == 1){
			i = new Intent(getApplicationContext(),PreferencesActivity.class);
			startActivity(i);
		}else if (position ==2){
			i = new Intent(getApplicationContext(),AdminPreferencesActivity.class);
			startActivity(i);
		}else if (position == 3){
			// It is 3
			i = new Intent(getApplicationContext(),MapSettings.class);
			startActivity(i);
		}else {
			resetSetting();
		}


	}

	private void resetSetting() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle("Confirm!");
		builder.setMessage("Are you sure want to revert the setting");
		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				SharedPreferences.Editor editor = preferences.edit();
				editor.clear();
				editor.commit();
				SharedPreferences adminPreferences = getBaseContext().getSharedPreferences(
						AdminPreferencesActivity.ADMIN_PREFERENCES, 0);
				SharedPreferences.Editor admineditor = adminPreferences.edit();
				admineditor.clear();
				admineditor.commit();

			}
		});

		builder.setNegativeButton("No", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		});
		builder.show();

	}
}