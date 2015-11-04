package com.kll.collect.android.activities;

import android.app.Activity;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.telephony.SmsManager;


import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.Spinner;
import android.widget.Toast;


import com.kll.collect.android.R;
import com.kll.collect.android.adapters.ExpandableListAdapter;
import com.kll.collect.android.preferences.PreferencesActivity;
import com.kll.collect.android.provider.FormsProviderAPI;


import com.kll.collect.android.provider.InstanceProviderAPI;
import com.kll.collect.android.provider.InstanceStatProvider;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by pujan on 8/23/15.
 */
public class StatTable extends Activity{

    private String DATEALL;
    private String DATELASTMONTH;
    private String DATELASTWEEK;
    private String DATETODAY;
    private String DATEYESTERDAY;
    ExpandableListView expandableListView;
    private ArrayList<String> formNames;
    private InstanceStatProvider instanceStat;
     ExpandableListAdapter listAdapter;
    HashMap listDataChild;
    List listDataHeader;
    private Spinner spinner;
    private Button send_sms;
    private Button send_email;
    private ArrayList<InstanceStatProvider> instanceStatProviders;

    public StatTable()
    {
        DATETODAY = "Today";
        DATEYESTERDAY = "Yesterday";
        DATELASTWEEK = "Last Week";
        DATELASTMONTH = "Last Month";
        DATEALL = "ALL";
    }

    public void onCreate(Bundle bundle)
    {
        super.onCreate(bundle);
        setContentView(R.layout.stat_table);
        spinner = (Spinner)findViewById(R.id.date);

        ArrayList<String> filter = new ArrayList<String>();
        filter.add(DATETODAY);
        filter.add(DATEYESTERDAY);
        filter.add(DATELASTWEEK);
        filter.add(DATELASTMONTH);
        filter.add(DATEALL);
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,filter);
        spinner.setAdapter(filterAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                int i = spinner.getSelectedItemPosition();
                generateStat(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }

        });
        send_sms = (Button) findViewById(R.id.send_sms);
        send_sms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                smsStat(instanceStatProviders);
            }
        });

    }

    private void smsStat(ArrayList<InstanceStatProvider> instanceStatProviders) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String sms_receiver = mSharedPreferences.getString(PreferencesActivity.KEY_SMS_RECEIVER, null);
            String surveyor_id = mSharedPreferences.getString(PreferencesActivity.KEY_SURVEYOR_ID, null);
            String smsBody = "upd ";
            String seperator = ",";
            String formID = "A";
            for (int i = 0; i < instanceStatProviders.size(); i++) {
                if (i == 0)
                    smsBody = smsBody + formID + seperator + Integer.toString(instanceStatProviders.get(i).getCompleted()) + seperator + Integer.toString(instanceStatProviders.get(i).getSent()) + seperator + Integer.toString(instanceStatProviders.get(i).getNo_attachment()) + seperator + Integer.toString(instanceStatProviders.get(i).getNot_sent());
                else if ((i == instanceStatProviders.size() - 1)&& !(surveyor_id.equals("")))
                    smsBody = smsBody + seperator + formID + seperator + Integer.toString(instanceStatProviders.get(i).getCompleted()) + seperator + Integer.toString(instanceStatProviders.get(i).getSent()) + seperator + Integer.toString(instanceStatProviders.get(i).getNo_attachment()) + seperator + Integer.toString(instanceStatProviders.get(i).getNot_sent()) + seperator + surveyor_id;
                else
                    smsBody = smsBody + seperator + formID + seperator + Integer.toString(instanceStatProviders.get(i).getCompleted()) + seperator + Integer.toString(instanceStatProviders.get(i).getSent()) + seperator + Integer.toString(instanceStatProviders.get(i).getNo_attachment()) + seperator + Integer.toString(instanceStatProviders.get(i).getNot_sent());
                char temp = (char) (((int) formID.charAt(0))+1);
                formID = Character.toString(temp);
            }
            Log.i("Message", smsBody);
            smsManager.sendTextMessage(sms_receiver, null, smsBody, null, null);
            Toast.makeText(getApplicationContext(), "Your sms has successfully sent!",Toast.LENGTH_LONG).show();

        }catch (Exception ex) {
            Toast.makeText(getApplicationContext(),"Your sms has failed...!",Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }


    }

    private void generateStat(int i) {
        ArrayList<String> formNames = new ArrayList<String>();

        String sortOrder = FormsProviderAPI.FormsColumns.DISPLAY_NAME + " ASC, " + FormsProviderAPI.FormsColumns.JR_VERSION + " DESC";
        Cursor c = managedQuery(FormsProviderAPI.FormsColumns.CONTENT_URI, null, null, null, sortOrder);
        c.moveToFirst();
        for(int j = 0;j < c.getCount();j++){
            formNames.add(c.getString(c.getColumnIndex("displayName")));
                    c.moveToNext();

        }
        Log.i("Total form",Integer.toString(formNames.size()));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String curDate = sdf.format(new Date());
        Log.i("Current Date",curDate);
        sdf = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
        String temp = sdf.format(new Date());

        Log.i("Temp Date", temp);

        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(sdf.parse(temp));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        switch (i){
           case 0:

               calendar.add(Calendar.DATE,0);
               break;

            case 1:
                calendar.add(Calendar.DATE,-1);
                break;

            case 2:
                calendar.add(Calendar.DATE,-7);
                break;
            case 3:
                calendar.add(Calendar.MONTH,-1);
                break;
            case 4:
                calendar.add(Calendar.YEAR,-25);
                break;


       }
        String finalDate = sdf.format(calendar.getTimeInMillis());
        Log.i("Final Date",finalDate);
        instanceStatProviders = new ArrayList<InstanceStatProvider>();
        for(int j = 0;j<formNames.size();j++) {
            instanceStat = new InstanceStatProvider();
            instanceStat.setFormName(formNames.get(j));
            Log.i("Form name",formNames.get(j));
            instanceStat.setCompleted((getCompletedCursor(formNames.get(j), finalDate, curDate)).getCount());
            instanceStat.setSent((getSentCursor(formNames.get(j), finalDate, curDate)).getCount());
            instanceStat.setNo_attachment((getNo_attachmentCursor(formNames.get(j), finalDate, curDate)).getCount());
            instanceStat.setNot_sent((getNot_sentCursor(formNames.get(j), finalDate, curDate)).getCount());
            instanceStatProviders.add(instanceStat);
        }

        populateStat(instanceStatProviders);

    }

    private void populateStat(ArrayList<InstanceStatProvider> instanceStat) {
        expandableListView = (ExpandableListView) findViewById(R.id.stat_exp_lv);
        prepareListData(instanceStat);
        listAdapter = new com.kll.collect.android.adapters.ExpandableListAdapter(this,listDataHeader,listDataChild);
        listAdapter.notifyDataSetChanged();
        expandableListView.setAdapter(listAdapter);

    }

    private void prepareListData(ArrayList<InstanceStatProvider> instanceStat) {
        listDataHeader = new ArrayList();
        listDataChild = new HashMap();
        for(int i = 0;i<instanceStat.size();i++){
            listDataHeader.add(instanceStat.get(i).getFormName());
        }
        for (int i = 0;i<instanceStat.size();i++){
            ArrayList<String> child = new ArrayList<String>();
            child.add("Completed: " + instanceStat.get(i).getCompleted());
            child.add("Sent: " + instanceStat.get(i).getSent());
            child.add("Attachment not Sent: " + instanceStat.get(i).getNo_attachment());
            child.add("Not Sent: " + instanceStat.get(i).getNot_sent());
            listDataChild.put(listDataHeader.get(i), child);
        }
    }

    private Cursor getNot_sentCursor(String formName, String finaldate, String curDate) {
        String selection =  "(" + InstanceProviderAPI.InstanceColumns.STATUS + "=? or "+ InstanceProviderAPI.InstanceColumns.STATUS + "=? ) and " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + "=? and  ("+ InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE + " BETWEEN '"+finaldate +"' and '"+curDate+"')";
        Log.i("Selectiion",selection);
        String[] selectionArgs = {InstanceProviderAPI.STATUS_COMPLETE, InstanceProviderAPI.STATUS_SUBMISSION_FAILED,formName};
        Cursor c = managedQuery(InstanceProviderAPI.InstanceColumns.CONTENT_URI, null, selection, selectionArgs,null);
        return c;

    }

    private Cursor getNo_attachmentCursor(String formName, String finaldate, String curDate) {
        String selection =  "(" + InstanceProviderAPI.InstanceColumns.STATUS + "=? ) and " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + "=? and  ("+ InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE + " BETWEEN '"+finaldate +"' and '"+curDate+"')";
        Log.i("Selectiion",selection);
        String[] selectionArgs = {InstanceProviderAPI.STATUS_ATTACHMENT_NOT_SENT ,formName};
        Cursor c = managedQuery(InstanceProviderAPI.InstanceColumns.CONTENT_URI, null, selection, selectionArgs,null);
        return c;
    }

    private Cursor getSentCursor(String formName, String finaldate, String curDate) {

        String selection =  "(" + InstanceProviderAPI.InstanceColumns.STATUS + "=? or "+ InstanceProviderAPI.InstanceColumns.STATUS + "=? ) and " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + "=? and  ("+ InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE + " BETWEEN '"+finaldate +"' and '"+curDate+"')";
        Log.i("Selectiion",selection);
        String[] selectionArgs = {InstanceProviderAPI.STATUS_SUBMITTED, InstanceProviderAPI.STATUS_ATTACHMENT_NOT_SENT ,formName};
        Cursor c = managedQuery(InstanceProviderAPI.InstanceColumns.CONTENT_URI, null, selection, selectionArgs,null);
            return c;

    }


    private Cursor getCompletedCursor(String formName, String finaldate, String curDate) {


        String selection =  "(" + InstanceProviderAPI.InstanceColumns.STATUS + "=? or " + InstanceProviderAPI.InstanceColumns.STATUS + "=? or " + InstanceProviderAPI.InstanceColumns.STATUS + "=? or "+ InstanceProviderAPI.InstanceColumns.STATUS + "=? ) and " + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + "=? and  ("+ InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE + " BETWEEN '"+finaldate +"' and '"+curDate+"')";
        Log.i("Selectiion",selection);
        String[] selectionArgs = {InstanceProviderAPI.STATUS_COMPLETE,InstanceProviderAPI.STATUS_SUBMITTED, InstanceProviderAPI.STATUS_ATTACHMENT_NOT_SENT , InstanceProviderAPI.STATUS_SUBMISSION_FAILED,formName};
        Cursor c = managedQuery(InstanceProviderAPI.InstanceColumns.CONTENT_URI, null, selection, selectionArgs,null);
            return c;

    }

}
