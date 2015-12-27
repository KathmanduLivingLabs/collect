/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kll.collect.android.activities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.parse.XFormParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.kll.collect.android.R;

import com.kll.collect.android.application.Collect;
import com.kll.collect.android.exception.JavaRosaException;
import com.kll.collect.android.listeners.AdvanceToNextListener;
import com.kll.collect.android.listeners.FormLoaderListener;
import com.kll.collect.android.listeners.FormSavedListener;
import com.kll.collect.android.listeners.SavePointListener;
import com.kll.collect.android.logic.FormController;
import com.kll.collect.android.logic.PropertyManager;
import com.kll.collect.android.logic.FormController.FailedConstraint;
import com.kll.collect.android.preferences.AdminPreferencesActivity;
import com.kll.collect.android.preferences.PreferencesActivity;
import com.kll.collect.android.provider.InstanceProviderAPI;
import com.kll.collect.android.provider.FormsProviderAPI.FormsColumns;
import com.kll.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import com.kll.collect.android.tasks.FormLoaderTask;
import com.kll.collect.android.tasks.SavePointTask;
import com.kll.collect.android.tasks.SaveResult;
import com.kll.collect.android.tasks.SaveToDiskTask;
import com.kll.collect.android.utilities.CompatibilityUtils;
import com.kll.collect.android.utilities.FileUtils;
import com.kll.collect.android.utilities.InfoLogger;
import com.kll.collect.android.utilities.MediaUtils;
import com.kll.collect.android.views.ODKView;
import com.kll.collect.android.widgets.GeoPointWidget;
import com.kll.collect.android.widgets.QuestionWidget;
import com.kll.collect.android.utilities.ScalingUtilities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.renderscript.Sampler;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * FormEntryActivity is responsible for displaying questions, animating
 * transitions between questions, and allowing the user to enter data.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Thomas Smyth, Sassafras Tech Collective (tom@sassafrastech.com; constraint behavior option)
 */
public class FormEntryActivity extends Activity implements AnimationListener,
		FormLoaderListener, FormSavedListener, AdvanceToNextListener,
		OnGestureListener, SavePointListener,LocationListener {
	private static final String t = "FormEntryActivity";

	// save with every swipe forward or back. Timings indicate this takes .25
	// seconds.
	// if it ever becomes an issue, this value can be changed to save every n'th
	// screen.
	private static final int SAVEPOINT_INTERVAL = 1;

	// Defines for FormEntryActivity
	private static final boolean EXIT = true;
	private static final boolean DO_NOT_EXIT = false;
	private static final boolean EVALUATE_CONSTRAINTS = true;
	private static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

	// Request codes for returning data from specified intent.
	public static final int IMAGE_CAPTURE = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int AUDIO_CAPTURE = 3;
	public static final int VIDEO_CAPTURE = 4;
	public static final int LOCATION_CAPTURE = 5;
	public static final int HIERARCHY_ACTIVITY = 6;
	public static final int IMAGE_CHOOSER = 7;
	public static final int AUDIO_CHOOSER = 8;
	public static final int VIDEO_CHOOSER = 9;
	public static final int EX_STRING_CAPTURE = 10;
	public static final int EX_INT_CAPTURE = 11;
	public static final int EX_DECIMAL_CAPTURE = 12;
	public static final int DRAW_IMAGE = 13;
	public static final int SIGNATURE_CAPTURE = 14;
	public static final int ANNOTATE_IMAGE = 15;
	public static final int ALIGNED_IMAGE = 16;
	public static final int BEARING_CAPTURE = 17;
    public static final int EX_GROUP_CAPTURE = 18;
    public static final int GEOSHAPE_CAPTURE = 19;
    public static final int GEOTRACE_CAPTURE = 20;

	// Extra returned from gp activity
	public static final String LOCATION_RESULT = "LOCATION_RESULT";
	public static final String BEARING_RESULT = "BEARING_RESULT";
	public static final String GEOSHAPE_RESULTS ="GEOSHAPE_RESULTS";
	public static final String GEOTRACE_RESULTS ="GEOTRACE_RESULTS";
	
	//public static final String LOCATION_SHAPE_RESULT = "LOCATION_SHAPE_RESULT";

	public static final String KEY_INSTANCES = "instances";
	public static final String KEY_SUCCESS = "success";
	public static final String KEY_ERROR = "error";

	// Identifies the gp of the form used to launch form entry
	public static final String KEY_FORMPATH = "formpath";

	// Identifies whether this is a new form, or reloading a form after a screen
	// rotation (or similar)
	private static final String NEWFORM = "newform";
	// these are only processed if we shut down and are restoring after an
	// external intent fires

	public static final String KEY_INSTANCEPATH = "instancepath";
	public static final String KEY_XPATH = "xpath";
	public static final String KEY_XPATH_WAITING_FOR_DATA = "xpathwaiting";

	private static final int MENU_LANGUAGES = Menu.FIRST;
	private static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 1;
	private static final int MENU_SAVE = Menu.FIRST + 2;
	private static final int MENU_PREFERENCES = Menu.FIRST + 3;

	private static final int PROGRESS_DIALOG = 1;
	private static final int SAVING_DIALOG = 2;

	private static final int NEXT  = 1;
	private static final int PREVIOUS  = 2;

	// Random ID
	private static final int DELETE_REPEAT = 654321;


	private String mFormPath;
	private GestureDetector mGestureDetector;

	private Animation mInAnimation;
	private Animation mOutAnimation;
	private View mStaleView = null;

	//private ProgressBar progressBar;
	private LinearLayout mQuestionHolder;
	private View mCurrentView;

	private AlertDialog mAlertDialog;
	private ProgressDialog mProgressDialog;
	private String mErrorMessage;

	// used to limit forward/backward swipes to one per question
	private boolean mBeenSwiped = false;

    private final Object saveDialogLock = new Object();
	private int viewCount = 0;

	private FormLoaderTask mFormLoaderTask;
	private SaveToDiskTask mSaveToDiskTask;

	private ImageButton mNextButton;
	private ImageButton mBackButton;

	//Presistent Data from Previous Form
	String mDistrict = null;
	String mVdc = null;
	String mWard = null;
	String mEnum_Area = null;
	Integer mRecord_Id = null;
    String mSurveyorId = null;

    public FormIndex districtIndex = null;
    public FormIndex vdcIndex = null;
    public FormIndex wardIndex = null;
    public FormIndex enumAreaIndex = null;
    public FormIndex recordIdIndex = null;
    public FormIndex surveyorIdIndex = null;

    public IAnswerData initialDistrictAnswer = null;
    public IAnswerData initialVdcAnswer = null;
    public IAnswerData initialWardAnswer = null;
    public IAnswerData initialEnumAreaAnswer = null;
    public IAnswerData initialRecordIdAnswer = null;
    public IAnswerData initialSurveyorIdAnswer = null;




    private String stepMessage = "";

	private double progressValue = 0.0;
	private String FIELD_LIST = "field-list";


	enum AnimationType {
		LEFT, RIGHT, FADE
	}

	public static LocationListener mLocationListner;
	private static LocationManager mLocationManager;
	private Location mLocation;
	private boolean mGPSOn = false;
	private boolean mNetworkOn = false;
	private double mLocationAccuracy;
	private int mLocationCount = 0;

	public boolean needLocation = false;
	private boolean compressImage = false;

	private SharedPreferences mAdminPreferences;
	private SharedPreferences mSharedPreferences;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// must be at the beginning of any activity that can be called from an
		// external intent
		try {
			Collect.createODKDirs();
		} catch (RuntimeException e) {
			createErrorDialog(e.getMessage(), EXIT);
			return;
		}
		Log.i("Activity","Created");
		 mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				compressImage = mSharedPreferences.getBoolean(PreferencesActivity.KEY_ENABLE_IMAGE_COMPRESSION, false);
			}
		});
		setContentView(R.layout.form_entry);
/*		setTitle(getString(R.string.app_name) + " > "
				+ getString(R.string.loading_form));*/
		setTitle(getString(R.string.app_name));

		Log.i("Entry", "Form");
		mErrorMessage = null;
		//progressBar = (ProgressBar) findViewById(R.id.progress);
		//progressBar.setVisibility(ProgressBar.VISIBLE);
		//progressBar.setProgress(0);



        mBeenSwiped = false;
		mAlertDialog = null;
		mCurrentView = null;
		mInAnimation = null;
		mOutAnimation = null;
		mGestureDetector = new GestureDetector(this, this);
		mQuestionHolder = (LinearLayout) findViewById(R.id.questionholder);

		// get admin preference settings
		mAdminPreferences = getSharedPreferences(
				AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

		mNextButton = (ImageButton) findViewById(R.id.form_forward_button);
		mNextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBeenSwiped = true;
				showNextView();
							}
		});

		mBackButton = (ImageButton) findViewById(R.id.form_back_button);
		mBackButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBeenSwiped = true;
				showPreviousView();
			}
		});



		needLocation = mSharedPreferences.getBoolean(PreferencesActivity.KEY_GPS_FIX, false);
		if(needLocation){

			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this);
		}


		// Load JavaRosa modules. needed to restore forms.
		new XFormsModule().registerModule();

		// needed to override rms property manager
		org.javarosa.core.services.PropertyManager
				.setPropertyManager(new PropertyManager(getApplicationContext()));

		String startingXPath = null;
		String waitingXPath = null;
		String instancePath = null;
		Boolean newForm = true;
		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey(KEY_FORMPATH)) {
				mFormPath = savedInstanceState.getString(KEY_FORMPATH);
			}
			if (savedInstanceState.containsKey(KEY_INSTANCEPATH)) {
				instancePath = savedInstanceState.getString(KEY_INSTANCEPATH);
			}
			if (savedInstanceState.containsKey(KEY_XPATH)) {
				startingXPath = savedInstanceState.getString(KEY_XPATH);
				Log.i(t, "startingXPath is: " + startingXPath);
			}
			if (savedInstanceState.containsKey(KEY_XPATH_WAITING_FOR_DATA)) {
				waitingXPath = savedInstanceState
						.getString(KEY_XPATH_WAITING_FOR_DATA);
				Log.i(t, "waitingXPath is: " + waitingXPath);
			}
			if (savedInstanceState.containsKey(NEWFORM)) {
				newForm = savedInstanceState.getBoolean(NEWFORM, true);
			}
			if (savedInstanceState.containsKey(KEY_ERROR)) {
				mErrorMessage = savedInstanceState.getString(KEY_ERROR);
			}
		}

		// If a parse error message is showing then nothing else is loaded
		// Dialogs mid form just disappear on rotation.
		if (mErrorMessage != null) {
			createErrorDialog(mErrorMessage, EXIT);
			return;
		}

		// Check to see if this is a screen flip or a new form load.
		Object data = getLastNonConfigurationInstance();
		if (data instanceof FormLoaderTask) {
			mFormLoaderTask = (FormLoaderTask) data;
		} else if (data instanceof SaveToDiskTask) {
			mSaveToDiskTask = (SaveToDiskTask) data;
		} else if (data == null) {
			if (!newForm) {
				if (Collect.getInstance().getFormController() != null) {
					refreshCurrentView();
				} else {
					Log.w(t, "Reloading form and restoring state.");
					// we need to launch the form loader to load the form
					// controller...
					mFormLoaderTask = new FormLoaderTask(instancePath,
							startingXPath, waitingXPath);
					Collect.getInstance().getActivityLogger()
							.logAction(this, "formReloaded", mFormPath);
					// TODO: this doesn' work (dialog does not get removed):
					// showDialog(PROGRESS_DIALOG);
					// show dialog before we execute...
					Log.i("Loader","Executing");
					mFormLoaderTask.execute(mFormPath);
				}
				return;
			}

			// Not a restart from a screen orientation change (or other).
			Collect.getInstance().setFormController(null);
			CompatibilityUtils.invalidateOptionsMenu(this);

			Intent intent = getIntent();
			if (intent != null) {
				Uri uri = intent.getData();

				if (getContentResolver().getType(uri).equals(InstanceColumns.CONTENT_ITEM_TYPE)) {
					// get the formId and version for this instance...
					Log.wtf("Intent Recived ", "From Edit Data");
					String jrFormId = null;
					String jrVersion = null;
					{
						Cursor instanceCursor = null;
						try {
							instanceCursor = getContentResolver().query(uri,
									null, null, null, null);
							if (instanceCursor.getCount() != 1) {
								this.createErrorDialog("Bad URI: " + uri, EXIT);
								return;
							} else {
								instanceCursor.moveToFirst();
								instancePath = instanceCursor
										.getString(instanceCursor
												.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
								Collect.getInstance()
										.getActivityLogger()
										.logAction(this, "instanceLoaded",
												instancePath);

								jrFormId = instanceCursor
										.getString(instanceCursor
												.getColumnIndex(InstanceColumns.JR_FORM_ID));
								int idxJrVersion = instanceCursor
										.getColumnIndex(InstanceColumns.JR_VERSION);

								jrVersion = instanceCursor.isNull(idxJrVersion) ? null
										: instanceCursor
												.getString(idxJrVersion);
							}
						} finally {
							if (instanceCursor != null) {
								instanceCursor.close();
							}
						}
					}

					String[] selectionArgs;
					String selection;

					if (jrVersion == null) {
						selectionArgs = new String[] { jrFormId };
						selection = FormsColumns.JR_FORM_ID + "=? AND "
								+ FormsColumns.JR_VERSION + " IS NULL";
					} else {
						selectionArgs = new String[] { jrFormId, jrVersion };
						selection = FormsColumns.JR_FORM_ID + "=? AND "
								+ FormsColumns.JR_VERSION + "=?";
					}

					{
						Cursor formCursor = null;
						try {
							formCursor = getContentResolver().query(
									FormsColumns.CONTENT_URI, null, selection,
									selectionArgs, null);
							if (formCursor.getCount() == 1) {
								formCursor.moveToFirst();
								mFormPath = formCursor
										.getString(formCursor
												.getColumnIndex(FormsColumns.FORM_FILE_PATH));
							} else if (formCursor.getCount() < 1) {
								this.createErrorDialog(
										getString(
												R.string.parent_form_not_present,
												jrFormId)
												+ ((jrVersion == null) ? ""
														: "\n"
																+ getString(R.string.version)
																+ " "
																+ jrVersion),
										EXIT);
								return;
							} else if (formCursor.getCount() > 1) {
								// still take the first entry, but warn that
								// there are multiple rows.
								// user will need to hand-edit the SQLite
								// database to fix it.
								formCursor.moveToFirst();
								mFormPath = formCursor.getString(formCursor.getColumnIndex(FormsColumns.FORM_FILE_PATH));
								this.createErrorDialog(getString(R.string.survey_multiple_forms_error),	EXIT);
                                return;
							}
						} finally {
							if (formCursor != null) {
								formCursor.close();
							}
						}
					}
				} else if (getContentResolver().getType(uri).equals(FormsColumns.CONTENT_ITEM_TYPE)) {
					Log.wtf("Intent Recived ", "From Collect Data");
					Cursor c = null;
					boolean defaultCachePresent = false;
					try {
						c = getContentResolver().query(uri, null, null, null,
								null);
						if (c.getCount() != 1) {
							this.createErrorDialog("Bad URI: " + uri, EXIT);
							return;
						} else {
							c.moveToFirst();
							mFormPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
							// This is the fill-blank-form code path.
							// See if there is a savepoint for this form that
							// has never been
							// explicitly saved
							// by the user. If there is, open this savepoint
							// (resume this filled-in
							// form).
							// Savepoints for forms that were explicitly saved
							// will be recovered
							// when that
							// explicitly saved instance is edited via
							// edit-saved-form.
							final String filePrefix = mFormPath.substring(
									mFormPath.lastIndexOf('/') + 1,
									mFormPath.lastIndexOf('.'))
									+ "_";
							final String fileSuffix = ".xml.save";
							File cacheDir = new File(Collect.CACHE_PATH);
							File[] files = cacheDir.listFiles(new FileFilter() {
								@Override
								public boolean accept(File pathname) {
									String name = pathname.getName();
									return name.startsWith(filePrefix)
											&& name.endsWith(fileSuffix);
								}
							});
							// see if any of these savepoints are for a
							// filled-in form that has never been
							// explicitly saved by the user...
							for (int i = 0; i < files.length; ++i) {
								File candidate = files[i];
								String instanceDirName = candidate.getName()
										.substring(
												0,
												candidate.getName().length()
														- fileSuffix.length());
								File instanceDir = new File(
										Collect.INSTANCES_PATH + File.separator
												+ instanceDirName);
								File instanceFile = new File(instanceDir,
										instanceDirName + ".xml");
								if (instanceDir.exists()
										&& instanceDir.isDirectory()
										&& !instanceFile.exists()) {
									// yes! -- use this savepoint file
									instancePath = instanceFile
											.getAbsolutePath();
									defaultCachePresent = true;
									break;
								}else{
								}

							}
							if (!defaultCachePresent) {
								String jrFormId = c.getString(c.getColumnIndex(FormsColumns.JR_FORM_ID));
								String jrVersion = c.getString(c.getColumnIndex(FormsColumns.JR_VERSION));
								String[] selectionArgs = new String[]{jrFormId, jrVersion};
								String selection = FormsColumns.JR_FORM_ID + "=? AND "
										+ FormsColumns.JR_VERSION + "=?";
								String sortOrder = InstanceColumns.LAST_STATUS_CHANGE_DATE + " DESC";
								Cursor latestInstance = managedQuery(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, sortOrder);
								Log.wtf("WTF",String.valueOf(latestInstance.getCount()));
								if (latestInstance.getCount() >= 1) {
									latestInstance.moveToFirst();
									mDistrict = latestInstance.getString(latestInstance.getColumnIndex(InstanceColumns.DISTRICT));
									Log.wtf("District ", mDistrict);
									mVdc = latestInstance.getString(latestInstance.getColumnIndex(InstanceColumns.VDC));
									Log.wtf("VDC ", mVdc);
									mWard = latestInstance.getString(latestInstance.getColumnIndex(InstanceColumns.WARD));
									Log.wtf("Ward ", mWard);
									mEnum_Area = latestInstance.getString(latestInstance.getColumnIndex(InstanceColumns.ENUMAREA));
									Log.wtf("Enumeration Area ", mEnum_Area);
                                    mSurveyorId = latestInstance.getString(latestInstance.getColumnIndex(InstanceColumns.SURVEYORID));
                                    Log.wtf("Surveyor ID ", mSurveyorId);
									mRecord_Id = getLatestUniqueIdFromDb(mDistrict, mVdc, mWard, mEnum_Area);
									Log.wtf("Record ID", String.valueOf(mRecord_Id));
								} else{
									mRecord_Id = 1;
								}



							}
						}
					} finally {
						if (c != null) {
							c.close();
						}
					}
				} else {
					Log.e(t, "unrecognized URI");
					this.createErrorDialog("Unrecognized URI: " + uri, EXIT);
					return;
				}

				mFormLoaderTask = new FormLoaderTask(instancePath, null, null);
				Collect.getInstance().getActivityLogger()
						.logAction(this, "formLoaded", mFormPath);
				showDialog(PROGRESS_DIALOG);
				// show dialog before we execute...
				Log.i("Loader","Executing");
				mFormLoaderTask.execute(mFormPath);
			}
		}


	}



    /**
     * Create save-points asynchronously in order to not affect swiping performance
     * on larger forms.
     */
    private void nonblockingCreateSavePointData() {
        try {
            SavePointTask savePointTask = new SavePointTask(this);
            savePointTask.execute();
        } catch (Exception e) {
            Log.e(t, "Could not schedule SavePointTask. Perhaps a lot of swiping is taking place?");
        }
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(KEY_FORMPATH, mFormPath);
		FormController formController = Collect.getInstance()
				.getFormController();
		if (formController != null) {
			outState.putString(KEY_INSTANCEPATH, formController
					.getInstancePath().getAbsolutePath());
			outState.putString(KEY_XPATH,
					formController.getXPath(formController.getFormIndex()));
			FormIndex waiting = formController.getIndexWaitingForData();
			if (waiting != null) {
				outState.putString(KEY_XPATH_WAITING_FOR_DATA,
                        formController.getXPath(waiting));
			}
			// save the instance to a temp path...
			nonblockingCreateSavePointData();
		}
		outState.putBoolean(NEWFORM, false);
		outState.putString(KEY_ERROR, mErrorMessage);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		Log.i("Request Code", Integer.toString(requestCode));
		Log.i("Result Code", Integer.toString(resultCode));
		FormController formController = Collect.getInstance()
				.getFormController();
		if (formController == null) {
			// we must be in the midst of a reload of the FormController.
			// try to save this callback data to the FormLoaderTask
			if (mFormLoaderTask != null
					&& mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED) {
				mFormLoaderTask.setActivityResult(requestCode, resultCode,
						intent);
			} else {
				Log.e(t,
						"Got an activityResult without any pending form loader");
			}
			return;
		}
		compressImage = mSharedPreferences.getBoolean(PreferencesActivity.KEY_ENABLE_IMAGE_COMPRESSION,false);

		if (resultCode == RESULT_CANCELED) {
			// request was canceled...
			if (requestCode != HIERARCHY_ACTIVITY) {
				((ODKView) mCurrentView).cancelWaitingForBinaryData();
			}
			return;
		}
		Log.i("Request Code ", Integer.toString(requestCode));
		switch (requestCode) {

		case BARCODE_CAPTURE:
			String sb = intent.getStringExtra("SCAN_RESULT");
			((ODKView) mCurrentView).setBinaryData(sb);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case EX_STRING_CAPTURE:
		case EX_INT_CAPTURE:
		case EX_DECIMAL_CAPTURE:
            String key = "value";
            boolean exists = intent.getExtras().containsKey(key);
            if (exists) {
                Object externalValue = intent.getExtras().get(key);
                ((ODKView) mCurrentView).setBinaryData(externalValue);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }
            break;
        case EX_GROUP_CAPTURE:
            try {
                Bundle extras = intent.getExtras();
                ((ODKView) mCurrentView).setDataForFields(extras);
            } catch (JavaRosaException e) {
                Log.e(t, e.getMessage(), e);
                createErrorDialog(e.getCause().getMessage(), DO_NOT_EXIT);
            }
            break;
		case DRAW_IMAGE:
		case ANNOTATE_IMAGE:
		case SIGNATURE_CAPTURE:
		case IMAGE_CAPTURE:
			/*
			 * We saved the image to the tempfile_path, but we really want it to
			 * be in: /sdcard/odk/instances/[current instnace]/something.jpg so
			 * we move it there before inserting it into the content provider.
			 * Once the android image capture bug gets fixed, (read, we move on
			 * from Android 1.6) we want to handle images the audio and video
			 */
			// The intent is empty, but we know we saved the image to the temp
			// file
			Log.i("Request Code ",Integer.toString(requestCode));
			File fi = new File(Collect.TMPFILE_PATH);
			String mInstanceFolder = formController.getInstancePath()
					.getParent();
			String s = mInstanceFolder + File.separator
					+ System.currentTimeMillis() + ".jpg";

			File nf = new File(s);
			if (!fi.renameTo(nf)) {
				Log.e(t, "Failed to rename " + fi.getAbsolutePath());
			} else {
				Log.i(t,
						"renamed " + fi.getAbsolutePath() + " to "
								+ nf.getAbsolutePath());
			}
			Log.i("Filename of image",String.valueOf(nf));
			if(compressImage)
				compreesImage(s);


					((ODKView) mCurrentView).setBinaryData(nf);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case ALIGNED_IMAGE:
			/*
			 * We saved the image to the tempfile_path; the app returns the full
			 * path to the saved file in the EXTRA_OUTPUT extra. Take that file
			 * and move it into the instance folder.
			 */
			String path = intent
					.getStringExtra(android.provider.MediaStore.EXTRA_OUTPUT);
			fi = new File(path);
			mInstanceFolder = formController.getInstancePath().getParent();
			s = mInstanceFolder + File.separator + System.currentTimeMillis()
					+ ".jpg";

			nf = new File(s);
			if (!fi.renameTo(nf)) {
				Log.e(t, "Failed to rename " + fi.getAbsolutePath());
			} else {
				Log.i(t,
						"renamed " + fi.getAbsolutePath() + " to "
								+ nf.getAbsolutePath());
			}
			if(compressImage)
				compreesImage(s);

			((ODKView) mCurrentView).setBinaryData(nf);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case IMAGE_CHOOSER:
			/*
			 * We have a saved image somewhere, but we really want it to be in:
			 * /sdcard/odk/instances/[current instnace]/something.jpg so we move
			 * it there before inserting it into the content provider. Once the
			 * android image capture bug gets fixed, (read, we move on from
			 * Android 1.6) we want to handle images the audio and video
			 */

			// get gp of chosen file
			Uri selectedImage = intent.getData();
			String sourceImagePath = MediaUtils.getPathFromUri(this, selectedImage, Images.Media.DATA);

			// Copy file to sdcard
			String mInstanceFolder1 = formController.getInstancePath()
					.getParent();
			String destImagePath = mInstanceFolder1 + File.separator
					+ System.currentTimeMillis() + ".jpg";

			File source = new File(sourceImagePath);
			File newImage = new File(destImagePath);
			FileUtils.copyFile(source, newImage);

			if(compressImage)
				compreesImage(destImagePath);

			((ODKView) mCurrentView).setBinaryData(newImage);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);

			break;
		case AUDIO_CAPTURE:
		case VIDEO_CAPTURE:
		case AUDIO_CHOOSER:
		case VIDEO_CHOOSER:
			// For audio/video capture/chooser, we get the URI from the content
			// provider
			// then the widget copies the file and makes a new entry in the
			// content provider.
			Uri media = intent.getData();
			((ODKView) mCurrentView).setBinaryData(media);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case LOCATION_CAPTURE:
			Log.i("Request Code ",Integer.toString(requestCode));
			String sl = intent.getStringExtra(LOCATION_RESULT);
			((ODKView) mCurrentView).setBinaryData(sl);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case GEOSHAPE_CAPTURE:
			//String ls = intent.getStringExtra(GEOSHAPE_RESULTS);
			String gshr = intent.getStringExtra(GEOSHAPE_RESULTS);
			((ODKView) mCurrentView).setBinaryData(gshr);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case GEOTRACE_CAPTURE:
			String traceExtra = intent.getStringExtra(GEOTRACE_RESULTS);
			((ODKView) mCurrentView).setBinaryData(traceExtra);
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			break;
		case BEARING_CAPTURE:
            String bearing = intent.getStringExtra(BEARING_RESULT);
            ((ODKView) mCurrentView).setBinaryData(bearing);
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
		case HIERARCHY_ACTIVITY:
			// We may have jumped to a new index in hierarchy activity, so
			// refresh
			break;

		}
		refreshCurrentView();
	}

	private void compreesImage(String path) {

		Bitmap scaledBitmap = null;
		try {
			// Part 1: Decode image
			Bitmap unscaledBitmap = ScalingUtilities.decodeFile(path, ScalingUtilities.DESIREDWIDTH, ScalingUtilities.DESIREDHEIGHT, ScalingUtilities.ScalingLogic.FIT);

			if (!(unscaledBitmap.getWidth() <= 800 && unscaledBitmap.getHeight() <= 800)) {
				// Part 2: Scale image
				scaledBitmap = ScalingUtilities.createScaledBitmap(unscaledBitmap, ScalingUtilities.DESIREDWIDTH, ScalingUtilities.DESIREDHEIGHT, ScalingUtilities.ScalingLogic.FIT);
			} else {
				unscaledBitmap.recycle();

			}

			// Store to tmp file






			File f = new File(path);


			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(f);
				scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
				fos.flush();
				fos.close();
			} catch (FileNotFoundException e) {

				e.printStackTrace();
			} catch (Exception e) {

				e.printStackTrace();
			}

			scaledBitmap.recycle();
		} catch (Throwable e) {
		}


	}

	/**
	 * Refreshes the current view. the controller and the displayed view can get
	 * out of sync due to dialogs and restarts caused by screen orientation
	 * changes, so they're resynchronized here.
	 */
	public void refreshCurrentView() {
		FormController formController = Collect.getInstance()
				.getFormController();
		int event = formController.getEvent();

		// When we refresh, repeat dialog state isn't maintained, so step back
		// to the previous
		// question.
		// Also, if we're within a group labeled 'field list', step back to the
		// beginning of that
		// group.
		// That is, skip backwards over repeat prompts, groups that are not
		// field-lists,
		// repeat events, and indexes in field-lists that is not the containing
		// group.
		if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
			createRepeatDialog();
		} else {
			View current = createView(event, false,0);
			showView(current, AnimationType.FADE);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "onCreateOptionsMenu", "show");
		super.onCreateOptionsMenu(menu);

		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_SAVE, 0, R.string.save_all_answers).setIcon(
						android.R.drawable.ic_menu_save),
				MenuItem.SHOW_AS_ACTION_IF_ROOM);

		CompatibilityUtils.setShowAsAction(
                menu.add(0, MENU_HIERARCHY_VIEW, 0, R.string.view_hierarchy)
                        .setIcon(R.drawable.ic_menu_goto),
                MenuItem.SHOW_AS_ACTION_IF_ROOM);

		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_LANGUAGES, 0, R.string.change_language)
						.setIcon(R.drawable.ic_menu_start_conversation),
				MenuItem.SHOW_AS_ACTION_NEVER);

		CompatibilityUtils.setShowAsAction(
                menu.add(0, MENU_PREFERENCES, 0, R.string.general_preferences)
                        .setIcon(R.drawable.ic_menu_preferences),
                MenuItem.SHOW_AS_ACTION_NEVER);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		FormController formController = Collect.getInstance()
				.getFormController();

		boolean useability;
		useability = mAdminPreferences.getBoolean(
				AdminPreferencesActivity.KEY_SAVE_MID, true);

		menu.findItem(MENU_SAVE).setVisible(useability).setEnabled(useability);

		useability = mAdminPreferences.getBoolean(
				AdminPreferencesActivity.KEY_JUMP_TO, true);

		menu.findItem(MENU_HIERARCHY_VIEW).setVisible(useability)
				.setEnabled(useability);

		useability = mAdminPreferences.getBoolean(
				AdminPreferencesActivity.KEY_CHANGE_LANGUAGE, true)
				&& (formController != null)
				&& formController.getLanguages() != null
				&& formController.getLanguages().length > 1;

		menu.findItem(MENU_LANGUAGES).setVisible(useability)
				.setEnabled(useability);

		useability = mAdminPreferences.getBoolean(
				AdminPreferencesActivity.KEY_ACCESS_SETTINGS, true);

		menu.findItem(MENU_PREFERENCES).setVisible(useability)
				.setEnabled(useability);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		FormController formController = Collect.getInstance()
				.getFormController();
		switch (item.getItemId()) {
		case MENU_LANGUAGES:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onOptionsItemSelected",
							"MENU_LANGUAGES");
			createLanguageDialog();
			return true;
		case MENU_SAVE:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onOptionsItemSelected",
							"MENU_SAVE");
			// don't exit
			saveDataToDisk(DO_NOT_EXIT, isInstanceComplete(false), null);
			return true;
		case MENU_HIERARCHY_VIEW:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onOptionsItemSelected",
							"MENU_HIERARCHY_VIEW");
			if (formController.currentPromptIsQuestion()) {
				saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			}
			Intent i = new Intent(this, FormHierarchyActivity.class);
			startActivityForResult(i, HIERARCHY_ACTIVITY);
			return true;
		case MENU_PREFERENCES:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onOptionsItemSelected",
							"MENU_PREFERENCES");
			Intent pref = new Intent(this, PreferencesActivity.class);
			startActivity(pref);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Attempt to save the answer(s) in the current screen to into the data
	 * model.
	 *
	 * @param evaluateConstraints
	 * @return false if any error occurs while saving (constraint violated,
	 *         etc...), true otherwise.
	 */
	private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints) {
		FormController formController = Collect.getInstance()
				.getFormController();
		// only try to save if the current event is a question or a field-list
		// group

		if (formController.currentPromptIsQuestion()) {
			LinkedHashMap<FormIndex, IAnswerData> answers = ((ODKView) mCurrentView)
					.getAnswers();

            try {
                FailedConstraint constraint = formController.saveAllScreenAnswers(answers, evaluateConstraints);
                if (constraint != null) {
                    createConstraintToast(constraint.index, constraint.status);
                    return false;
                }
            } catch (JavaRosaException e) {
                Log.e(t, e.getMessage(), e);
                createErrorDialog(e.getCause().getMessage(), DO_NOT_EXIT);
                return false;
            }
		}
		return true;
	}

	/**
	 * Clears the answer on the screen.
	 */
	private void clearAnswer(QuestionWidget qw) {
		if (qw.getAnswer() != null) {
			qw.clearAnswer();
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "onCreateContextMenu", "show");
		FormController formController = Collect.getInstance()
				.getFormController();

		menu.add(0, v.getId(), 0, getString(R.string.clear_answer));
		if (formController.indexContainsRepeatableGroup()) {
			menu.add(0, DELETE_REPEAT, 0, getString(R.string.delete_repeat));
		}
		menu.setHeaderTitle(getString(R.string.edit_prompt));
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		/*
		 * We don't have the right view here, so we store the View's ID as the
		 * item ID and loop through the possible views to find the one the user
		 * clicked on.
		 */
		for (QuestionWidget qw : ((ODKView) mCurrentView).getWidgets()) {
			if (item.getItemId() == qw.getId()) {
				Collect.getInstance()
						.getActivityLogger()
						.logInstanceAction(this, "onContextItemSelected",
								"createClearDialog", qw.getPrompt().getIndex());
				createClearDialog(qw);
			}
		}
		if (item.getItemId() == DELETE_REPEAT) {
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onContextItemSelected",
							"createDeleteRepeatConfirmDialog");
			createDeleteRepeatConfirmDialog();
		}

		return super.onContextItemSelected(item);
	}

	/**
	 * If we're loading, then we pass the loading thread to our next instance.
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		FormController formController = Collect.getInstance()
				.getFormController();
		// if a form is loading, pass the loader task
		if (mFormLoaderTask != null
				&& mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
			return mFormLoaderTask;

		// if a form is writing to disk, pass the save to disk task
		if (mSaveToDiskTask != null
				&& mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)
			return mSaveToDiskTask;

		// mFormEntryController is static so we don't need to pass it.
		if (formController != null && formController.currentPromptIsQuestion()) {
			saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
		}
		return null;
	}
	@Override
	public void onLocationChanged(Location location) {
		mLocation = location;
		if (mLocation != null) {
			// Bug report: cached GeoPoint is being returned as the first value.
			// Wait for the 2nd value to be returned, which is hopefully not cached?
			++mLocationCount;
			InfoLogger.geolog("GeoPointActivity: " + System.currentTimeMillis() +
					" onLocationChanged(" + mLocationCount + ") lat: " +
					mLocation.getLatitude() + " long: " +
					mLocation.getLongitude() + " acc: " +
					mLocation.getAccuracy());
			Log.i("lat=", Double.toString(mLocation.getLatitude()));
			Log.i("lat=", Double.toString(mLocation.getLongitude()));
			Log.i("lat=", Double.toString(mLocation.getAccuracy()));

		} else {
			InfoLogger.geolog("GeoPointActivity: " + System.currentTimeMillis() +
                    " onLocationChanged(" + mLocationCount + ") null location");
		}
	}




	@Override
	public void onProviderDisabled(String provider) {

	}


	@Override
	public void onProviderEnabled(String provider) {

	}


	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		switch (status) {
			case LocationProvider.AVAILABLE:

				break;
			case LocationProvider.OUT_OF_SERVICE:
				break;
			case LocationProvider.TEMPORARILY_UNAVAILABLE:
				break;
		}
	}

	/**
	 * Creates a view given the View type and an event
	 *
	 * @param event
	 * @param advancingPage
	 *            -- true if this results from advancing through the form
	 * @return newly created View
	 */
	private View createView(int event, boolean advancingPage,int swipeCase) {
		FormController formController = Collect.getInstance()
				.getFormController();
	/*	setTitle(getString(R.string.app_name) + " > "
				+ formController.getFormTitle());*/
		int questioncount = formController.getFormDef().getDeepChildCount();
        if(formController.currentPromptIsQuestion()){
            if(initialDistrictAnswer != null){
                if (initialDistrictAnswer.equals(formController.getQuestionPrompt(districtIndex).getAnswerValue())) {
                    Log.wtf("District", "Same");
                }else{
                    Log.wtf("District", "Changed");
                    initialDistrictAnswer = formController.getQuestionPrompt(districtIndex).getAnswerValue();
                    mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                    IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                    try{
                        formController.answerQuestion(recordIdIndex,record_ID);
                    } catch (JavaRosaException e){
                        e.printStackTrace();
                    }
                }
            }else{
                Log.wtf("District","Empty");
                initialDistrictAnswer = formController.getQuestionPrompt(districtIndex).getAnswerValue();
                mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                try{
                    formController.answerQuestion(recordIdIndex,record_ID);
                } catch (JavaRosaException e){
                    e.printStackTrace();
                }
            }



            if(initialVdcAnswer != null){
                if (initialVdcAnswer.equals(formController.getQuestionPrompt(vdcIndex).getAnswerValue())) {
                    Log.wtf("VDC", "Same");
                }else{
                    Log.wtf("VDC", "Changed");
                    initialVdcAnswer = formController.getQuestionPrompt(vdcIndex).getAnswerValue();
                    mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                    IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                    try{
                        formController.answerQuestion(recordIdIndex,record_ID);
                    } catch (JavaRosaException e){
                        e.printStackTrace();
                    }
                }
            }else{
                Log.wtf("VDC","Empty");
                initialVdcAnswer = formController.getQuestionPrompt(vdcIndex).getAnswerValue();
                mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                try{
                    formController.answerQuestion(recordIdIndex,record_ID);
                } catch (JavaRosaException e){
                    e.printStackTrace();
                }
            }




            if(initialWardAnswer != null){
                if (initialWardAnswer.equals(formController.getQuestionPrompt(wardIndex).getAnswerValue())) {
                    Log.wtf("Ward", "Same");
                }else{
                    Log.wtf("Ward", "Changed");
                    initialWardAnswer = formController.getQuestionPrompt(wardIndex).getAnswerValue();
                    mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                    IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                    try{
                        formController.answerQuestion(recordIdIndex,record_ID);
                    } catch (JavaRosaException e){
                        e.printStackTrace();
                    }
                }
            }else{
                Log.wtf("Ward","Empty");
                initialWardAnswer = formController.getQuestionPrompt(wardIndex).getAnswerValue();
                mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                try{
                    formController.answerQuestion(recordIdIndex,record_ID);
                } catch (JavaRosaException e){
                    e.printStackTrace();
                }
            }



            if(initialEnumAreaAnswer != null){
                if (initialEnumAreaAnswer.equals(formController.getQuestionPrompt(enumAreaIndex).getAnswerValue())) {
                    Log.wtf("Enumeration Area", "Same");
                }else{
                    Log.wtf("Enumeration Area", "Changed");
                    initialEnumAreaAnswer = formController.getQuestionPrompt(enumAreaIndex).getAnswerValue();
                    mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                    IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                    try{
                        formController.answerQuestion(recordIdIndex,record_ID);
                    } catch (JavaRosaException e){
                        e.printStackTrace();
                    }
                }
            }else{
                Log.wtf("Enumeration Area","Empty");
                initialEnumAreaAnswer = formController.getQuestionPrompt(enumAreaIndex).getAnswerValue();
                mRecord_Id = getLatestUniqueIdFromDb(getValueFromUserInput("district"),getValueFromUserInput("vdc"), getValueFromUserInput("ward"),getValueFromUserInput("enumeration_area"));
                IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
                try{
                    formController.answerQuestion(recordIdIndex,record_ID);
                } catch (JavaRosaException e){
                    e.printStackTrace();
                }
            }











        }


		switch (event) {
		case FormEntryController.EVENT_BEGINNING_OF_FORM:
			progressValue = 0.0;
			progressUpdate(progressValue,questioncount);
			View startView = View
					.inflate(this, R.layout.form_entry_start, null);
			/*setTitle(getString(R.string.app_name) + " > "
					+ formController.getFormTitle());*/

			Drawable image = null;
			File mediaFolder = formController.getMediaFolder();
			String mediaDir = mediaFolder.getAbsolutePath();
			BitmapDrawable bitImage = null;
			// attempt to load the form-specific logo...
			// this is arbitrarily silly
			bitImage = new BitmapDrawable(getResources(), mediaDir + File.separator
					+ "form_logo.png");

			if (bitImage != null && bitImage.getBitmap() != null
					&& bitImage.getIntrinsicHeight() > 0
					&& bitImage.getIntrinsicWidth() > 0) {
				image = bitImage;
			}

			if (image == null) {
				// show the opendatakit zig...
				// image =
				// getResources().getDrawable(R.drawable.opendatakit_zig);
				((ImageView) startView.findViewById(R.id.form_start_bling))
						.setVisibility(View.GONE);
			} else {
				ImageView v = ((ImageView) startView
						.findViewById(R.id.form_start_bling));
				v.setImageDrawable(image);
				v.setContentDescription(formController.getFormTitle());
			}

			// change start screen based on navigation prefs
			String navigationChoice = PreferenceManager
					.getDefaultSharedPreferences(this).getString(
							PreferencesActivity.KEY_NAVIGATION,
							PreferencesActivity.KEY_NAVIGATION);
			Boolean useSwipe = false;
			Boolean useButtons = false;
			ImageView ia = ((ImageView) startView
					.findViewById(R.id.image_advance));
			ImageView ib = ((ImageView) startView
					.findViewById(R.id.image_backup));
			TextView ta = ((TextView) startView.findViewById(R.id.text_advance));
			TextView tb = ((TextView) startView.findViewById(R.id.text_backup));
			TextView d = ((TextView) startView.findViewById(R.id.description));

			if (navigationChoice != null) {
				if (navigationChoice
						.contains(PreferencesActivity.NAVIGATION_SWIPE)) {
					useSwipe = true;
				}
				if (navigationChoice
						.contains(PreferencesActivity.NAVIGATION_BUTTONS)) {
					useButtons = true;
				}
			}
			if (useSwipe && !useButtons) {
				d.setText(getString(R.string.swipe_instructions,
						formController.getFormTitle()));
			} else if (useButtons && !useSwipe) {
				ia.setVisibility(View.GONE);
				ib.setVisibility(View.GONE);
				ta.setVisibility(View.GONE);
				tb.setVisibility(View.GONE);
				d.setText(getString(R.string.buttons_instructions,
						formController.getFormTitle()));
			} else {
				d.setText(getString(R.string.swipe_buttons_instructions,
						formController.getFormTitle()));
			}

			if (mBackButton.isShown()) {
				mBackButton.setEnabled(false);
			}
			if (mNextButton.isShown()) {
				mNextButton.setEnabled(true);
			}

			return startView;
		case FormEntryController.EVENT_END_OF_FORM:
			progressValue = questioncount;
			progressUpdate(progressValue,questioncount);
			View endView = View.inflate(this, R.layout.form_entry_end, null);

			((ImageView) endView.findViewById(R.id.completed)).setImageDrawable(getResources().getDrawable(R.drawable.complete_tick));
			((TextView) endView.findViewById(R.id.description))
					.setText(getString(R.string.save_enter_data_description,
							formController.getFormTitle()));

			// checkbox for if finished or ready to send
			final CheckBox instanceComplete = ((CheckBox) endView
					.findViewById(R.id.mark_finished));
			instanceComplete.setChecked(isInstanceComplete(true));

			if (!mAdminPreferences.getBoolean(
					AdminPreferencesActivity.KEY_MARK_AS_FINALIZED, true)) {
				instanceComplete.setVisibility(View.GONE);
			}




			// edittext to change the displayed name of the instance
			final EditText saveAs = (EditText) endView
					.findViewById(R.id.save_name);

			// disallow carriage returns in the name
			InputFilter returnFilter = new InputFilter() {
				public CharSequence filter(CharSequence source, int start,
						int end, Spanned dest, int dstart, int dend) {
					for (int i = start; i < end; i++) {
						if (Character.getType((source.charAt(i))) == Character.CONTROL) {
							return "";
						}
					}
					return null;
				}
			};
			saveAs.setFilters(new InputFilter[]{returnFilter});
            String saveName = getSaveName();

			if (saveName == null) {
				// no meta/instanceName field in the form -- see if we have a
				// name for this instance from a previous save attempt...
				if (getContentResolver().getType(getIntent().getData()) == InstanceColumns.CONTENT_ITEM_TYPE) {
					Uri instanceUri = getIntent().getData();
					Cursor instance = null;
					try {
						instance = getContentResolver().query(instanceUri,
								null, null, null, null);
						if (instance.getCount() == 1) {
							instance.moveToFirst();
							saveName = instance
									.getString(instance
											.getColumnIndex(InstanceColumns.DISPLAY_NAME));
						}
					} finally {
						if (instance != null) {
							instance.close();
						}
					}
				}
				if (saveName == null) {
					// last resort, default to the form title
					saveName = formController.getFormTitle();
				}
				// present the prompt to allow user to name the form
				TextView sa = (TextView) endView
						.findViewById(R.id.save_form_as);
				sa.setVisibility(View.VISIBLE);
				saveAs.setText(saveName);
				saveAs.setEnabled(true);
				saveAs.setVisibility(View.VISIBLE);
			} else {
				// if instanceName is defined in form, this is the name -- no
				// revisions
				// display only the name, not the prompt, and disable edits
				TextView sa = (TextView) endView
						.findViewById(R.id.save_form_as);
				sa.setVisibility(View.GONE);
				saveAs.setText(saveName);
				saveAs.setEnabled(false);
				saveAs.setBackgroundColor(Color.WHITE);
				saveAs.setVisibility(View.VISIBLE);
			}

			// override the visibility settings based upon admin preferences
			if (!mAdminPreferences.getBoolean(
					AdminPreferencesActivity.KEY_SAVE_AS, true)) {
				saveAs.setVisibility(View.GONE);
				TextView sa = (TextView) endView
						.findViewById(R.id.save_form_as);
				sa.setVisibility(View.GONE);
			}

			// Create 'save' button
			((Button) endView.findViewById(R.id.save_exit_button))
					.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Collect.getInstance()
									.getActivityLogger()
									.logInstanceAction(
											this,
											"createView.saveAndExit",
											instanceComplete.isChecked() ? "saveAsComplete"
													: "saveIncomplete");
							// Form is marked as 'saved' here.
							if (saveAs.getText().length() < 1) {
								Toast.makeText(FormEntryActivity.this,
										R.string.save_as_error,
										Toast.LENGTH_SHORT).show();
							} else {
								saveDataToDisk(EXIT, instanceComplete
										.isChecked(), saveAs.getText()
										.toString());
							}
						}
					});

			if (mBackButton.isShown()) {
				mBackButton.setEnabled(true);
			}
			if (mNextButton.isShown()) {
				mNextButton.setEnabled(false);
			}

			return endView;
		case FormEntryController.EVENT_QUESTION:
		case FormEntryController.EVENT_GROUP:
		case FormEntryController.EVENT_REPEAT:
			ODKView odkv = null;
			// should only be a group here if the event_group is a field-list
			try {
				double depth = (double) formController.getFormIndex().getDepth();
				double local_index = (double) formController.getFormIndex().getLocalIndex();
				double instance_index = (double) formController.getFormIndex().getInstanceIndex();
				Log.i("Question coint",Integer.toString(questioncount));

				Log.i("Depth",Integer.toString(formController.getFormIndex().getDepth()));
				Log.i("Local Index", Integer.toString(formController.getFormIndex().getLocalIndex()));
				Log.i("Instance Index",Integer.toString(formController.getFormIndex().getInstanceIndex()));



				if(swipeCase == NEXT){

					Log.wtf("progressValue", Double.toString(progressValue));

				}
				if (swipeCase == PREVIOUS){
					progressValue = progressValue-(1-(instance_index*local_index/questioncount*depth));

					Log.i("progressValue", Double.toString(progressValue));
				}
				Log.i("progressValue", Double.toString(progressValue));
				progressUpdate(progressValue,questioncount);
				FormEntryPrompt[] prompts = formController.getQuestionPrompts();
				FormEntryCaption[] groups = formController
						.getGroupsForCurrentIndex();
				odkv = new ODKView(this, formController.getQuestionPrompts(),
						groups, advancingPage);
				Log.i(t,
						"created view for group "
								+ (groups.length > 0 ? groups[groups.length - 1]
										.getLongText() : "[top]")
								+ " "
								+ (prompts.length > 0 ? prompts[0]
										.getQuestionText() : "[no question]"));
			} catch (RuntimeException e) {
				Log.e(t, e.getMessage(), e);
				// this is badness to avoid a crash.
                try {
                    event = formController.stepToNextScreenEvent();
                    createErrorDialog(e.getMessage(), DO_NOT_EXIT);
                } catch (JavaRosaException e1) {
                    Log.e(t, e1.getMessage(), e1);
                    createErrorDialog(e.getMessage() + "\n\n" + e1.getCause().getMessage(), DO_NOT_EXIT);
                }
                return createView(event, advancingPage,swipeCase);
            }

			// Makes a "clear answer" menu pop up on long-click
			for (QuestionWidget qw : odkv.getWidgets()) {
				if (!qw.getPrompt().isReadOnly()) {
					registerForContextMenu(qw);
				}
			}

			if (mBackButton.isShown() && mNextButton.isShown()) {
				mBackButton.setEnabled(true);
				mNextButton.setEnabled(true);
			}
			return odkv;
		default:
			Log.e(t, "Attempted to create a view that does not exist.");
			// this is badness to avoid a crash.
            try {
                event = formController.stepToNextScreenEvent();
                createErrorDialog(getString(R.string.survey_internal_error), EXIT);
            } catch (JavaRosaException e) {
                Log.e(t, e.getMessage(), e);
                createErrorDialog(e.getCause().getMessage(), EXIT);
            }
			return createView(event, advancingPage,swipeCase);
		}

	}

	private void progressUpdate(double progressValue, int questioncount) {
		double progress =  ( progressValue / (double) questioncount) * 100;
		Log.i("Progess", Double.toString(progress));
		//progressBar.setProgress((int) progress);
		Log.i("Index", Integer.toString(questioncount));
	}


	public String getSaveName() {
        //TODO
        FormController formController = Collect.getInstance()
                .getFormController();
        String saveName = formController.getSubmissionMetadata().instanceName;
		Log.i("Save name",Boolean.toString(mAdminPreferences.getBoolean("savename_from_input", true)));
        if(mAdminPreferences.getBoolean("savename_from_input", true)) {
			Log.i("Save name", mAdminPreferences.getString("defaultsave_field", getString(R.string.default_save_name)));
			if(!mAdminPreferences.getString("defaultsave_field",getString(R.string.default_save_name)).equals("")) {


				try {
                    ByteArrayPayload submissionXml = formController.getSubmissionXml();
                    InputStream submissionInput = submissionXml.getPayloadStream();
                    XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
                    XmlPullParser myparser = xmlFactoryObject.newPullParser();
                    myparser.setInput(new StringReader(IOUtils.toString(submissionInput, "UTF-8")));
                    Log.i("SAVENAME_SUBMISSION", IOUtils.toString(submissionInput, "UTF-8"));
                    saveName = parseSaveName(mAdminPreferences.getString("defaultsave_field", getString(R.string.default_save_name)), myparser, formController.getSubmissionMetadata().instanceName);

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (XmlPullParserException e){
                    e.printStackTrace();
                }
            }
        }
        return saveName;
    }

    public String parseSaveName(String valuesToGet, XmlPullParser xmlToParse, String defaultValue){
        String saveName = "";
        String[] fields = valuesToGet.split("\\s+");
        System.out.println(xmlToParse.getAttributeCount());
        for (int i=0; i < fields.length; i++){
            String userinput = getValueFromXML(xmlToParse,fields[i]);
            Log.i("PARSINGKEY",fields[i]);
            if (userinput != null) {
                saveName = saveName + userinput + " ";
            }

        }
        if (saveName.equals("")){
            saveName = defaultValue;
        }


        return saveName;
    }



    public String getValueFromUserInput(String key) {
        //TODO
        FormController formController = Collect.getInstance()
                .getFormController();
        String answer = null;


        try {
            ByteArrayPayload submissionXml = formController.getSubmissionXml();
            InputStream submissionInput = submissionXml.getPayloadStream();
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser myparser = xmlFactoryObject.newPullParser();
            myparser.setInput(new StringReader(IOUtils.toString(submissionInput, "UTF-8")));
            Log.wtf("WTF", IOUtils.toString(submissionInput, "UTF-8"));
            answer = parseXML(key, myparser);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e){
            e.printStackTrace();
        }

        return answer;
    }

    public String parseXML(String valuesToGet, XmlPullParser xmlToParse){
        String saveName = "";
        String[] fields = valuesToGet.split("\\s+");
        System.out.println(xmlToParse.getAttributeCount());
        for (int i=0; i < fields.length; i++){
            String userinput = getValueFromXML(xmlToParse,fields[i]);
            Log.i("PARSINGKEY",fields[i]);
            if (userinput != null) {
                saveName = saveName + userinput + " ";
            }

        }


        return saveName;
    }


    public String getValueFromXML(XmlPullParser xml, String key){
        String result = null;
        try {
            int event = xml.getEventType();
            String text = null;

            while (event != XmlPullParser.END_DOCUMENT)
            {
                String tag = xml.getName();
                if (tag != null) {
                    System.out.println(tag);
                }else{
                    System.out.println("Null Tag");
                }

                switch (event){
                    case XmlPullParser.START_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        text = xml.getText();
                        break;
                    case XmlPullParser.END_TAG:
                        if(tag.equals(key)){
                            System.out.println("Match Found");
                            result = text;
                            System.out.println(result);
                        }
                        break;
                }
                event = xml.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

	@Override
	public boolean dispatchTouchEvent(MotionEvent mv) {
		boolean handled = mGestureDetector.onTouchEvent(mv);
		if (!handled) {
			return super.dispatchTouchEvent(mv);
		}

		return handled; // this is always true
	}

	/**
	 * Determines what should be displayed on the screen. Possible options are:
	 * a question, an ask repeat dialog, or the submit screen. Also saves
	 * answers to the data model after checking constraints.
	 */
	private void showNextView() {
		try {
            FormController formController = Collect.getInstance()
                    .getFormController();

            // get constraint behavior preference value with appropriate default
            String constraint_behavior = PreferenceManager.getDefaultSharedPreferences(this)
					.getString(PreferencesActivity.KEY_CONSTRAINT_BEHAVIOR,
							PreferencesActivity.CONSTRAINT_BEHAVIOR_DEFAULT);


			            if (formController.currentPromptIsQuestion()) {

                // if constraint behavior says we should validate on swipe, do so
                if (constraint_behavior.equals(PreferencesActivity.CONSTRAINT_BEHAVIOR_ON_SWIPE)) {
                    if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS)) {
                        // A constraint was violated so a dialog should be showing.
                        mBeenSwiped = false;
                        return;
                    }

                    // otherwise, just save without validating (constraints will be validated on finalize)
                } else
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }

            View next;
            int event = formController.stepToNextScreenEvent();


            switch (event) {
                case FormEntryController.EVENT_QUESTION:
				case FormEntryController.EVENT_GROUP:
                    // create a savepoint
                    if ((++viewCount) % SAVEPOINT_INTERVAL == 0) {
                        nonblockingCreateSavePointData();

                    }
                    next = createView(event, true,NEXT);
                    showView(next, AnimationType.RIGHT);
                    break;
                case FormEntryController.EVENT_END_OF_FORM:
				case FormEntryController.EVENT_REPEAT:
                    next = createView(event, true,NEXT);
                    showView(next, AnimationType.RIGHT);
                    break;
                case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                    createRepeatDialog();
                    break;
                case FormEntryController.EVENT_REPEAT_JUNCTURE:
                    Log.i(t, "repeat juncture: "
                            + formController.getFormIndex().getReference());
                    // skip repeat junctures until we implement them
                    break;
                default:
                    Log.w(t,
                            "JavaRosa added a new EVENT type and didn't tell us... shame on them.");
                    break;
            }

        } catch (JavaRosaException e) {
            Log.e(t, e.getMessage(), e);
            createErrorDialog(e.getCause().getMessage(), DO_NOT_EXIT);
        }

    }

	/**
	 * Determines what should be displayed between a question, or the start
	 * screen and displays the appropriate view. Also saves answers to the data
	 * model without checking constraints.
	 */
	private void showPreviousView() {
        try {
            FormController formController = Collect.getInstance()
                    .getFormController();
            // The answer is saved on a back swipe, but question constraints are
            // ignored.

            if (formController.currentPromptIsQuestion()) {
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }

            if (formController.getEvent() != FormEntryController.EVENT_BEGINNING_OF_FORM) {
                int event = formController.stepToPreviousScreenEvent();

                if (event == FormEntryController.EVENT_BEGINNING_OF_FORM
                        || event == FormEntryController.EVENT_GROUP
                        || event == FormEntryController.EVENT_QUESTION) {
                    // create savepoint
                    if ((++viewCount) % SAVEPOINT_INTERVAL == 0) {
                        nonblockingCreateSavePointData();
                    }
                }
                View next = createView(event, false,PREVIOUS);
                showView(next, AnimationType.LEFT);
            } else {
                mBeenSwiped = false;
            }

        } catch (JavaRosaException e) {
            Log.e(t, e.getMessage(), e);
            createErrorDialog(e.getCause().getMessage(), DO_NOT_EXIT);
        }

    }

	/**
	 * Displays the View specified by the parameter 'next', animating both the
	 * current view and next appropriately given the AnimationType. Also updates
	 * the progress bar.
	 */
	public void showView(View next, AnimationType from) {

		// disable notifications...
		if (mInAnimation != null) {
			mInAnimation.setAnimationListener(null);
		}
		if (mOutAnimation != null) {
			mOutAnimation.setAnimationListener(null);

		}

		// logging of the view being shown is already done, as this was handled
		// by createView()
		switch (from) {
		case RIGHT:
			mInAnimation = AnimationUtils.loadAnimation(this,
					R.anim.push_left_in);
			mOutAnimation = AnimationUtils.loadAnimation(this,
					R.anim.push_left_out);
			break;
		case LEFT:
			mInAnimation = AnimationUtils.loadAnimation(this,
					R.anim.push_right_in);
			mOutAnimation = AnimationUtils.loadAnimation(this,
					R.anim.push_right_out);
			break;
		case FADE:
			mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
			mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
			break;
		}

		// complete setup for animations...
		mInAnimation.setAnimationListener(this);
		mOutAnimation.setAnimationListener(this);

		// drop keyboard before transition...
		if (mCurrentView != null) {
			InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(mCurrentView.getWindowToken(),
					0);
		}

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

		// adjust which view is in the layout container...
		mStaleView = mCurrentView;
		mCurrentView = next;
		mQuestionHolder.addView(mCurrentView, lp);
		mAnimationCompletionSet = 0;

		if (mStaleView != null) {
			// start OutAnimation for transition...
			mStaleView.startAnimation(mOutAnimation);
			// and remove the old view (MUST occur after start of animation!!!)
			mQuestionHolder.removeView(mStaleView);
		} else {
			mAnimationCompletionSet = 2;
		}
		// start InAnimation for transition...
		mCurrentView.startAnimation(mInAnimation);

		String logString = "";
		switch (from) {
		case RIGHT:
			logString = "next";
			break;
		case LEFT:
			logString = "previous";
			break;
		case FADE:
			logString = "refresh";
			break;
		}

		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "showView", logString);
	}

	// Hopefully someday we can use managed dialogs when the bugs are fixed
	/*
	 * Ideally, we'd like to use Android to manage dialogs with onCreateDialog()
	 * and onPrepareDialog(), but dialogs with dynamic content are broken in 1.5
	 * (cupcake). We do use managed dialogs for our static loading
	 * ProgressDialog. The main issue we noticed and are waiting to see fixed
	 * is: onPrepareDialog() is not called after a screen orientation change.
	 * http://code.google.com/p/android/issues/detail?id=1639
	 */

	//
	/**
	 * Creates and displays a dialog displaying the violated constraint.
	 */
	private void createConstraintToast(FormIndex index, int saveStatus) {
		FormController formController = Collect.getInstance()
				.getFormController();
		String constraintText;
		switch (saveStatus) {
		case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this,
							"createConstraintToast.ANSWER_CONSTRAINT_VIOLATED",
							"show", index);
			constraintText = formController
					.getQuestionPromptConstraintText(index);
			if (constraintText == null) {
				constraintText = formController.getQuestionPrompt(index)
						.getSpecialFormQuestionText("constraintMsg");
				if (constraintText == null) {
					constraintText = getString(R.string.invalid_answer_error);
				}
			}
			break;
		case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this,
							"createConstraintToast.ANSWER_REQUIRED_BUT_EMPTY",
							"show", index);
			constraintText = formController
					.getQuestionPromptRequiredText(index);
			if (constraintText == null) {
				constraintText = formController.getQuestionPrompt(index)
						.getSpecialFormQuestionText("requiredMsg");
				if (constraintText == null) {
					constraintText = getString(R.string.required_answer_error);
				}
			}
			break;
		default:
			return;
		}

		showCustomToast(constraintText, Toast.LENGTH_SHORT);
	}

	/**
	 * Creates a toast with the specified message.
	 *
	 * @param message
	 */
	private void showCustomToast(String message, int duration) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(duration);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}

	/**
	 * Creates and displays a dialog asking the user if they'd like to create a
	 * repeat of the current group.
	 */
	private void createRepeatDialog() {
		FormController formController = Collect.getInstance()
				.getFormController();
		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "createRepeatDialog", "show");
		mAlertDialog = new AlertDialog.Builder(this).create();
		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
		DialogInterface.OnClickListener repeatListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				FormController formController = Collect.getInstance()
						.getFormController();
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // yes, repeat
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this, "createRepeatDialog",
									"addRepeat");
					try {
						formController.newRepeat();
					} catch (Exception e) {
						FormEntryActivity.this.createErrorDialog(
								e.getMessage(), DO_NOT_EXIT);
						return;
					}
					if (!formController.indexIsInFieldList()) {
						// we are at a REPEAT event that does not have a
						// field-list appearance
						// step to the next visible field...
						// which could be the start of a new repeat group...
						showNextView();
					} else {
						// we are at a REPEAT event that has a field-list
						// appearance
						// just display this REPEAT event's group.
						refreshCurrentView();
					}
					break;
				case DialogInterface. BUTTON_NEGATIVE: // no, no repeat
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this, "createRepeatDialog",
									"showNext");

                    //
                    // Make sure the error dialog will not disappear.
                    //
                    // When showNextView() popups an error dialog (because of a JavaRosaException)
                    // the issue is that the "add new repeat dialog" is referenced by mAlertDialog
                    // like the error dialog. When the "no repeat" is clicked, the error dialog
                    // is shown. Android by default dismisses the dialogs when a button is clicked,
                    // so instead of closing the first dialog, it closes the second.
                    new Thread() {

                        @Override
                        public void run() {
                            FormEntryActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    showNextView();
                                }
                            });
                        }
                    }.start();

					break;
				}
			}
		};
		if (formController.getLastRepeatCount() > 0) {
			mAlertDialog.setTitle(getString(R.string.leaving_repeat_ask));
			mAlertDialog.setMessage(getString(R.string.add_another_repeat,
					formController.getLastGroupText()));
			mAlertDialog.setButton(getString(R.string.add_another),
					repeatListener);
			mAlertDialog.setButton2(getString(R.string.leave_repeat_yes),
					repeatListener);

		} else {
			mAlertDialog.setTitle(getString(R.string.entering_repeat_ask));
			mAlertDialog.setMessage(getString(R.string.add_repeat,
					formController.getLastGroupText()));
			mAlertDialog.setButton(getString(R.string.entering_repeat),
					repeatListener);
			mAlertDialog.setButton2(getString(R.string.add_repeat_no),
					repeatListener);
		}
		mAlertDialog.setCancelable(false);
		mBeenSwiped = false;
		mAlertDialog.show();
	}

	/**
	 * Creates and displays dialog with the given errorMsg.
	 */
	private void createErrorDialog(String errorMsg, final boolean shouldExit) {
		Collect.getInstance()
				.getActivityLogger()
				.logInstanceAction(this, "createErrorDialog",
						"show." + Boolean.toString(shouldExit));

        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            errorMsg = mErrorMessage + "\n\n" + errorMsg;
            mErrorMessage = errorMsg;
        } else {
            mAlertDialog = new AlertDialog.Builder(this).create();
            mErrorMessage = errorMsg;
        }

		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
		mAlertDialog.setTitle(getString(R.string.error_occured));
		mAlertDialog.setMessage(errorMsg);
		DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE:
					Collect.getInstance().getActivityLogger()
							.logInstanceAction(this, "createErrorDialog", "OK");
					if (shouldExit) {
                        mErrorMessage = null;
						finish();
					}
					break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog.setButton(getString(R.string.ok), errorListener);
		mBeenSwiped = false;
		mAlertDialog.show();
	}

	/**
	 * Creates a confirm/cancel dialog for deleting repeats.
	 */
	private void createDeleteRepeatConfirmDialog() {
		Collect.getInstance()
				.getActivityLogger()
				.logInstanceAction(this, "createDeleteRepeatConfirmDialog",
						"show");
		FormController formController = Collect.getInstance()
				.getFormController();
		mAlertDialog = new AlertDialog.Builder(this).create();
		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
		String name = formController.getLastRepeatedGroupName();
		int repeatcount = formController.getLastRepeatedGroupRepeatCount();
		if (repeatcount != -1) {
			name += " (" + (repeatcount + 1) + ")";
		}
		mAlertDialog.setTitle(getString(R.string.delete_repeat_ask));
		mAlertDialog
				.setMessage(getString(R.string.delete_repeat_confirm, name));
		DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				FormController formController = Collect.getInstance()
						.getFormController();
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // yes
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this,
									"createDeleteRepeatConfirmDialog", "OK");
					formController.deleteRepeat();
					showPreviousView();
					break;
				case DialogInterface. BUTTON_NEGATIVE: // no
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this,
									"createDeleteRepeatConfirmDialog", "cancel");
					break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog.setButton(getString(R.string.discard_group), quitListener);
		mAlertDialog.setButton2(getString(R.string.delete_repeat_no),
                quitListener);
		mAlertDialog.show();
	}

	/**
	 * Saves data and writes it to disk. If exit is set, program will exit after
	 * save completes. Complete indicates whether the user has marked the
	 * isntancs as complete. If updatedSaveName is non-null, the instances
	 * content provider is updated with the new name
	 */
	private boolean saveDataToDisk(boolean exit, boolean complete,
			String updatedSaveName) {
		// save current answer
		if (!saveAnswersForCurrentScreen(complete)) {
			Toast.makeText(this, getString(R.string.data_saved_error),
					Toast.LENGTH_SHORT).show();
			return false;
		}

        synchronized (saveDialogLock) {
		    mSaveToDiskTask = new SaveToDiskTask(getIntent().getData(), exit,
				complete, updatedSaveName);
			Log.wtf("URI",getIntent().getDataString());
	    	mSaveToDiskTask.setFormSavedListener(this);
		    showDialog(SAVING_DIALOG);
            // show dialog before we execute...
		    mSaveToDiskTask.execute();
        }

		return true;
	}

	/**
	 * Create a dialog with options to save and exit, save, or quit without
	 * saving
	 */
	private void createQuitDialog() {
		FormController formController = Collect.getInstance()
				.getFormController();
		String[] items;
		if (mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_SAVE_MID,
				true)) {
			String[] two = { getString(R.string.keep_changes),
					getString(R.string.do_not_save) };
			items = two;
		} else {
			String[] one = { getString(R.string.do_not_save) };
			items = one;
		}

		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "createQuitDialog", "show");
		mAlertDialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(
						getString(R.string.quit_application,
								formController.getFormTitle()))
				.setNeutralButton(getString(R.string.do_not_exit),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {

								Collect.getInstance()
										.getActivityLogger()
										.logInstanceAction(this,
												"createQuitDialog", "cancel");
								dialog.cancel();

							}
						})
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {

						case 0: // save and exit
							// this is slightly complicated because if the
							// option is disabled in
							// the admin menu, then case 0 actually becomes
							// 'discard and exit'
							// whereas if it's enabled it's 'save and exit'
							if (mAdminPreferences
									.getBoolean(
											AdminPreferencesActivity.KEY_SAVE_MID,
											true)) {
								Collect.getInstance()
										.getActivityLogger()
										.logInstanceAction(this,
												"createQuitDialog",
												"saveAndExit");
								saveDataToDisk(EXIT, isInstanceComplete(false),
										null);
							} else {
								Collect.getInstance()
										.getActivityLogger()
										.logInstanceAction(this,
												"createQuitDialog",
												"discardAndExit");
								removeTempInstance();
								finishReturnInstance();
							}
							break;

						case 1: // discard changes and exit
							Collect.getInstance()
									.getActivityLogger()
									.logInstanceAction(this,
											"createQuitDialog",
											"discardAndExit");

                            // close all open databases of external data.
                            Collect.getInstance().getExternalDataManager().close();

							removeTempInstance();
							finishReturnInstance();
							break;

						case 2:// do nothing
							Collect.getInstance()
									.getActivityLogger()
									.logInstanceAction(this,
											"createQuitDialog", "cancel");
							break;
						}
					}
				}).create();
		mAlertDialog.show();
	}

	/**
	 * this method cleans up unneeded files when the user selects 'discard and
	 * exit'
	 */
	private void removeTempInstance() {
		FormController formController = Collect.getInstance()
				.getFormController();

		// attempt to remove any scratch file
		File temp = SaveToDiskTask.savepointFile(formController
                .getInstancePath());
		if (temp.exists()) {
			temp.delete();
		}

		String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
		String[] selectionArgs = { formController.getInstancePath()
				.getAbsolutePath() };

		boolean erase = false;
		{
			Cursor c = null;
			try {
				c = getContentResolver().query(InstanceColumns.CONTENT_URI,
						null, selection, selectionArgs, null);
				erase = (c.getCount() < 1);
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}

		// if it's not already saved, erase everything
		if (erase) {
			// delete media first
			String instanceFolder = formController.getInstancePath()
					.getParent();
			Log.i(t, "attempting to delete: " + instanceFolder);
			int images = MediaUtils
					.deleteImagesInFolderFromMediaProvider(formController
							.getInstancePath().getParentFile());
			int audio = MediaUtils
					.deleteAudioInFolderFromMediaProvider(formController
							.getInstancePath().getParentFile());
			int video = MediaUtils
					.deleteVideoInFolderFromMediaProvider(formController
							.getInstancePath().getParentFile());

			Log.i(t, "removed from content providers: " + images
					+ " image files, " + audio + " audio files," + " and "
					+ video + " video files.");
			File f = new File(instanceFolder);
			if (f.exists() && f.isDirectory()) {
				for (File del : f.listFiles()) {
					Log.i(t, "deleting file: " + del.getAbsolutePath());
					del.delete();
				}
				f.delete();
			}
		}
	}

	/**
	 * Confirm clear answer dialog
	 */
	private void createClearDialog(final QuestionWidget qw) {
		Collect.getInstance()
				.getActivityLogger()
				.logInstanceAction(this, "createClearDialog", "show",
						qw.getPrompt().getIndex());
		mAlertDialog = new AlertDialog.Builder(this).create();
		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);

		mAlertDialog.setTitle(getString(R.string.clear_answer_ask));

		String question = qw.getPrompt().getLongText();
		if (question == null) {
			question = "";
		}
		if (question.length() > 50) {
			question = question.substring(0, 50) + "...";
		}

		mAlertDialog.setMessage(getString(R.string.clearanswer_confirm,
				question));

		DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // yes
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this, "createClearDialog",
									"clearAnswer", qw.getPrompt().getIndex());
					clearAnswer(qw);
					saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
					break;
				case DialogInterface. BUTTON_NEGATIVE: // no
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this, "createClearDialog",
									"cancel", qw.getPrompt().getIndex());
					break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog
				.setButton(getString(R.string.discard_answer), quitListener);
		mAlertDialog.setButton2(getString(R.string.clear_answer_no),
				quitListener);
		mAlertDialog.show();
	}

	/**
	 * Creates and displays a dialog allowing the user to set the language for
	 * the form.
	 */
	private void createLanguageDialog() {
		Collect.getInstance().getActivityLogger()
				.logInstanceAction(this, "createLanguageDialog", "show");
		FormController formController = Collect.getInstance()
				.getFormController();
		final String[] languages = formController.getLanguages();
		int selected = -1;
		if (languages != null) {
			String language = formController.getLanguage();
			for (int i = 0; i < languages.length; i++) {
				if (language.equals(languages[i])) {
					selected = i;
				}
			}
		}
		mAlertDialog = new AlertDialog.Builder(this)
				.setSingleChoiceItems(languages, selected,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                FormController formController = Collect
                                        .getInstance().getFormController();
                                // Update the language in the content provider
                                // when selecting a new
                                // language
                                ContentValues values = new ContentValues();
                                values.put(FormsColumns.LANGUAGE,
                                        languages[whichButton]);
                                String selection = FormsColumns.FORM_FILE_PATH
                                        + "=?";
                                String selectArgs[] = {mFormPath};
                                int updated = getContentResolver().update(
                                        FormsColumns.CONTENT_URI, values,
                                        selection, selectArgs);
                                Log.i(t, "Updated language to: "
                                        + languages[whichButton] + " in "
                                        + updated + " rows");

                                Collect.getInstance()
                                        .getActivityLogger()
                                        .logInstanceAction(
                                                this,
                                                "createLanguageDialog",
                                                "changeLanguage."
                                                        + languages[whichButton]);
                                formController
                                        .setLanguage(languages[whichButton]);
                                dialog.dismiss();
                                if (formController.currentPromptIsQuestion()) {
                                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                                }
                                refreshCurrentView();
                            }
                        })
				.setTitle(getString(R.string.change_language))
				.setNegativeButton(getString(R.string.do_not_change),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                Collect.getInstance()
                                        .getActivityLogger()
                                        .logInstanceAction(this,
                                                "createLanguageDialog",
                                                "cancel");
                            }
                        }).create();
		mAlertDialog.show();
	}

	/**
	 * We use Android's dialog management for loading/saving progress dialogs
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESS_DIALOG:
			Log.e(t, "Creating PROGRESS_DIALOG");
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onCreateDialog.PROGRESS_DIALOG",
							"show");
			mProgressDialog = new ProgressDialog(this);
			DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this,
									"onCreateDialog.PROGRESS_DIALOG", "cancel");
					dialog.dismiss();
					mFormLoaderTask.setFormLoaderListener(null);
					FormLoaderTask t = mFormLoaderTask;
					mFormLoaderTask = null;
					t.cancel(true);
					t.destroy();
					finish();
				}
			};
			mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
			mProgressDialog.setTitle(getString(R.string.loading_form));
			mProgressDialog.setMessage(getString(R.string.please_wait));
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(false);
			mProgressDialog.setButton(getString(R.string.cancel_loading_form),
					loadingButtonListener);
			return mProgressDialog;
		case SAVING_DIALOG:
            Log.e(t, "Creating SAVING_DIALOG");
			Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this, "onCreateDialog.SAVING_DIALOG",
							"show");
			mProgressDialog = new ProgressDialog(this);
			DialogInterface.OnClickListener cancelSavingButtonListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Collect.getInstance()
							.getActivityLogger()
							.logInstanceAction(this,
									"onCreateDialog.SAVING_DIALOG", "cancel");
					dialog.dismiss();
                    cancelSaveToDiskTask();
				}
			};
			mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
			mProgressDialog.setTitle(getString(R.string.saving_form));
			mProgressDialog.setMessage(getString(R.string.please_wait));
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(false);
			mProgressDialog.setButton(getString(R.string.cancel),
					cancelSavingButtonListener);
			mProgressDialog.setButton(getString(R.string.cancel_saving_form),
					cancelSavingButtonListener);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
					Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this,
							"onCreateDialog.SAVING_DIALOG", "OnCancelListener");
                    cancelSaveToDiskTask();
                }
            });
            mProgressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
					Collect.getInstance()
					.getActivityLogger()
					.logInstanceAction(this,
							"onCreateDialog.SAVING_DIALOG", "OnDismissListener");
                    cancelSaveToDiskTask();
                }
            });
			return mProgressDialog;
		}
		return null;
	}


    private void cancelSaveToDiskTask() {
        synchronized (saveDialogLock) {
            mSaveToDiskTask.setFormSavedListener(null);
            boolean cancelled = mSaveToDiskTask.cancel(true);
            Log.w(t, "Cancelled SaveToDiskTask! (" + cancelled + ")");
            mSaveToDiskTask = null;
        }
    }
	/**
	 * Dismiss any showing dialogs that we manually manage.
	 */
	private void dismissDialogs() {
		Log.e(t, "Dismiss dialogs");
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
	}

	@Override
	protected void onPause() {
		FormController formController = Collect.getInstance()
				.getFormController();
		dismissDialogs();
		// make sure we're not already saving to disk. if we are, currentPrompt
		// is getting constantly updated
		if (mSaveToDiskTask == null
				|| mSaveToDiskTask.getStatus() == AsyncTask.Status.FINISHED) {
			if (mCurrentView != null && formController != null
					&& formController.currentPromptIsQuestion()) {
				saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
			}
		}

		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("Activity","Resume");



		if (needLocation) {
			Log.i("It is starting","gps");
			if (mLocationManager != null) {
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this);
			}
			if (mErrorMessage != null) {
				if (mAlertDialog != null && !mAlertDialog.isShowing()) {
					createErrorDialog(mErrorMessage, EXIT);
				} else {
					return;
				}
			}
		}


        FormController formController = Collect.getInstance().getFormController();
        Collect.getInstance().getActivityLogger().open();

		if (mFormLoaderTask != null) {
			mFormLoaderTask.setFormLoaderListener(this);
			if (formController == null
					&& mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
				FormController fec = mFormLoaderTask.getFormController();
				if (fec != null) {
					loadingComplete(mFormLoaderTask);
				} else {
					dismissDialog(PROGRESS_DIALOG);
					FormLoaderTask t = mFormLoaderTask;
					mFormLoaderTask = null;
					t.cancel(true);
					t.destroy();
					// there is no formController -- fire MainMenu activity?
					startActivity(new Intent(this, MainMenuActivity.class));
				}
			}
		} else {
            if (formController == null) {
                // there is no formController -- fire MainMenu activity?
                startActivity(new Intent(this, MainMenuActivity.class));
                return;
            } else {
                refreshCurrentView();
            }
		}

		if (mSaveToDiskTask != null) {
			mSaveToDiskTask.setFormSavedListener(this);
		}

		// only check the buttons if it's enabled in preferences
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		String navigation = sharedPreferences.getString(
				PreferencesActivity.KEY_NAVIGATION,
				PreferencesActivity.KEY_NAVIGATION);
		Boolean showButtons = false;
		if (navigation.contains(PreferencesActivity.NAVIGATION_BUTTONS)) {
			showButtons = true;
		}

		if (showButtons) {
			mBackButton.setVisibility(View.VISIBLE);
			mNextButton.setVisibility(View.VISIBLE);
		} else {
			mBackButton.setVisibility(View.GONE);
			mNextButton.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Collect.getInstance().getActivityLogger()
					.logInstanceAction(this, "onKeyDown.KEYCODE_BACK", "quit");
			createQuitDialog();
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			if (event.isAltPressed() && !mBeenSwiped) {
				mBeenSwiped = true;
				Collect.getInstance()
						.getActivityLogger()
						.logInstanceAction(this,
								"onKeyDown.KEYCODE_DPAD_RIGHT", "showNext");
				showNextView();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			if (event.isAltPressed() && !mBeenSwiped) {
				mBeenSwiped = true;
				Collect.getInstance()
						.getActivityLogger()
						.logInstanceAction(this, "onKeyDown.KEYCODE_DPAD_LEFT",
								"showPrevious");
				showPreviousView();
				return true;
			}
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onDestroy() {
		Log.i("Activity", "Destroyed");
		if(mLocationManager!=null) {
			mLocationManager.removeUpdates(this);
		}
		if (mFormLoaderTask != null) {
			mFormLoaderTask.setFormLoaderListener(null);
			// We have to call cancel to terminate the thread, otherwise it
			// lives on and retains the FEC in memory.
			// but only if it's done, otherwise the thread never returns
			if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
				FormLoaderTask t = mFormLoaderTask;
				mFormLoaderTask = null;
				t.cancel(true);
				t.destroy();
			}
		}
		if (mSaveToDiskTask != null) {
			mSaveToDiskTask.setFormSavedListener(null);
			// We have to call cancel to terminate the thread, otherwise it
			// lives on and retains the FEC in memory.
			if (mSaveToDiskTask.getStatus() == AsyncTask.Status.FINISHED) {
				mSaveToDiskTask.cancel(true);
				mSaveToDiskTask = null;
			}
		}

		super.onDestroy();

	}

	private int mAnimationCompletionSet = 0;

	private void afterAllAnimations() {
		Log.i(t, "afterAllAnimations");
		if (mStaleView != null) {
			if (mStaleView instanceof ODKView) {
				// http://code.google.com/p/android/issues/detail?id=8488
				((ODKView) mStaleView).recycleDrawables();
			}
			mStaleView = null;
		}

		if (mCurrentView instanceof ODKView) {
			((ODKView) mCurrentView).setFocus(this);
		}
		mBeenSwiped = false;
	}

	@Override
	public void onAnimationEnd(Animation animation) {
		Log.i(t, "onAnimationEnd "
				+ ((animation == mInAnimation) ? "in"
						: ((animation == mOutAnimation) ? "out" : "other")));
		if (mInAnimation == animation) {
			mAnimationCompletionSet |= 1;
		} else if (mOutAnimation == animation) {
			mAnimationCompletionSet |= 2;
		} else {
			Log.e(t, "Unexpected animation");
		}

		if (mAnimationCompletionSet == 3) {
			this.afterAllAnimations();
		}
	}

	@Override
	public void onAnimationRepeat(Animation animation) {
		// Added by AnimationListener interface.
		Log.i(t, "onAnimationRepeat "
                + ((animation == mInAnimation) ? "in"
                : ((animation == mOutAnimation) ? "out" : "other")));
	}

	@Override
	public void onAnimationStart(Animation animation) {
		// Added by AnimationListener interface.
		Log.i(t, "onAnimationStart "
				+ ((animation == mInAnimation) ? "in"
						: ((animation == mOutAnimation) ? "out" : "other")));
	}

	/**
	 * loadingComplete() is called by FormLoaderTask once it has finished
	 * loading a form.
	 */
	@Override
	public void loadingComplete(FormLoaderTask task) {
		dismissDialog(PROGRESS_DIALOG);
		Log.i("Form Loading", "Complete");
		FormController formController = task.getFormController();
		boolean pendingActivityResult = task.hasPendingActivityResult();
		boolean hasUsedSavepoint = task.hasUsedSavepoint();
		int requestCode = task.getRequestCode();

		// these are bogus if
		Log.i("Reqest Code",Integer.toString(requestCode));// pendingActivityResult is
													// false
		int resultCode = task.getResultCode();
		Intent intent = task.getIntent();

		mFormLoaderTask.setFormLoaderListener(null);
		FormLoaderTask t = mFormLoaderTask;
		mFormLoaderTask = null;
		t.cancel(true);
		t.destroy();
		Collect.getInstance().setFormController(formController);
		CompatibilityUtils.invalidateOptionsMenu(this);

        Collect.getInstance().setExternalDataManager(task.getExternalDataManager());
        setQuestionIndex(formController);

		if ( mWard != null){
			IntegerData ward = new IntegerData(Integer.valueOf(mWard));
			try {
				formController.answerQuestion(wardIndex, ward);
			} catch (JavaRosaException e) {
				e.printStackTrace();
			}
		}

		if ( mEnum_Area != null){
			IntegerData eno_area = new IntegerData(Integer.valueOf(mEnum_Area));
			try {
				formController.answerQuestion(enumAreaIndex, eno_area);
			} catch (JavaRosaException e) {
				e.printStackTrace();
			}
		}

		if (mRecord_Id != null){
			IntegerData record_ID = new IntegerData(Integer.valueOf(mRecord_Id));
			try{
				formController.answerQuestion(recordIdIndex,record_ID);
			} catch (JavaRosaException e){
				e.printStackTrace();
			}
		}

		if ( mDistrict != null){
            FormEntryPrompt prompt = formController.getQuestionPrompt(districtIndex);
			SelectChoice district_choice = getSelectChoiceFromValue(prompt, mDistrict);
			SelectOneData district = new SelectOneData(new Selection(district_choice));
						try {
							formController.answerQuestion(districtIndex, district );
						} catch (JavaRosaException e) {
							e.printStackTrace();
						}
			}

        if ( mVdc != null) {
            FormEntryPrompt prompt = formController.getQuestionPrompt(vdcIndex);
            SelectChoice vdc_choice = getSelectChoiceFromValue(prompt, mVdc);
            SelectOneData vdc = new SelectOneData(new Selection(vdc_choice));
            try {
                formController.answerQuestion(vdcIndex, vdc );
            } catch (JavaRosaException e) {
                e.printStackTrace();
            }
        }

        if (mSurveyorId != null){
            StringData surveyor_id = new StringData(mSurveyorId);
            try {
                formController.answerQuestion(surveyorIdIndex, surveyor_id);
            } catch (JavaRosaException e) {
                e.printStackTrace();
            }

            setInitialValuesForSix(formController);



        }




		// Set the language if one has already been set in the past
		String[] languageTest = formController.getLanguages();
		if (languageTest != null) {
			String defaultLanguage = formController.getLanguage();
			String newLanguage = "";
			String selection = FormsColumns.FORM_FILE_PATH + "=?";
			String selectArgs[] = { mFormPath };
			Cursor c = null;
			try {
				c = getContentResolver().query(FormsColumns.CONTENT_URI, null,
						selection, selectArgs, null);
				if (c.getCount() == 1) {
					c.moveToFirst();
					newLanguage = c.getString(c
							.getColumnIndex(FormsColumns.LANGUAGE));
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}

			// if somehow we end up with a bad language, set it to the default
			try {
				formController.setLanguage(newLanguage);
			} catch (Exception e) {
				formController.setLanguage(defaultLanguage);
			}
		}

		if (pendingActivityResult) {
			// set the current view to whatever group we were at...
			refreshCurrentView();
			// process the pending activity request...
			onActivityResult(requestCode, resultCode, intent);
			return;
		}

		// it can be a normal flow for a pending activity result to restore from
		// a savepoint
		// (the call flow handled by the above if statement). For all other use
		// cases, the
		// user should be notified, as it means they wandered off doing other
		// things then
		// returned to ODK Collect and chose Edit Saved Form, but that the
		// savepoint for that
		// form is newer than the last saved version of their form data.
		if (hasUsedSavepoint) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(FormEntryActivity.this,
							getString(R.string.savepoint_used),
							Toast.LENGTH_LONG).show();
				}
			});
		}

		// Set saved answer path
		if (formController.getInstancePath() == null) {

			// Create new answer folder.
			String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
					Locale.ENGLISH).format(Calendar.getInstance().getTime());
			String file = mFormPath.substring(mFormPath.lastIndexOf('/') + 1,
					mFormPath.lastIndexOf('.'));
			String path = Collect.INSTANCES_PATH + File.separator + file + "_"
					+ time;
			if (FileUtils.createFolder(path)) {
				formController.setInstancePath(new File(path + File.separator
						+ file + "_" + time + ".xml"));
			}
		} else {
			Intent reqIntent = getIntent();
			boolean showFirst = reqIntent.getBooleanExtra("start", false);

			if (!showFirst) {
				// we've just loaded a saved form, so start in the hierarchy
				// view
				Intent i = new Intent(this, FormHierarchyActivity.class);
				startActivity(i);
				return; // so we don't show the intro screen before jumping to
						// the hierarchy
			}
		}

		refreshCurrentView();
	}




    public SelectChoice getSelectChoiceFromValue(FormEntryPrompt prompt, String value){

        Vector<SelectChoice> items = prompt.getSelectChoices();
        String[] choices = new String[items.size()];


        for (int i = 0; i < items.size(); i++) {
            choices[i] = items.get(i).getValue();

        }

        int pos = -1;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(value)) {
                pos = i;
            }
        }

        Log.wtf("Secect Coices From Value", String.valueOf(pos) );

        if (pos == -1){
            return null;
        }



        return items.get(pos);
    }


    public void setQuestionIndex(FormController formController){
        districtIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_district[1]/district[1]");
        if(districtIndex == null){
            districtIndex = formController.getIndexFromXPath("question./Training_form_dec21/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_district[1]/district[1]");
            Log.wtf("District","Index Still Null");
        }
        vdcIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_vdc[1]/vdc[1]");
        if (vdcIndex == null){
            vdcIndex = formController.getIndexFromXPath("question./Training_form_dec21/building_damage_assessment[1]/hh_data[1]/hh_address[1]/vdc[1]");
            Log.wtf("VDC","Index Still Null");
        }
        wardIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/ward[1]");
        if (wardIndex == null){
            wardIndex = formController.getIndexFromXPath("question./Training_form_dec21/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/ward[1]");
            Log.wtf("Ward", "Index Still Null");
        }
        enumAreaIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/enumeration_area[1]");
        if (enumAreaIndex== null){
            enumAreaIndex = formController.getIndexFromXPath("question./Training_form_dec21/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/enumeration_area[1]");
            Log.wtf("Enumeration Area","Index Still Null");
        }
        recordIdIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/house_no[1]");
        if (recordIdIndex == null){
            recordIdIndex = formController.getIndexFromXPath("question./Training_form_dec21/building_damage_assessment[1]/hh_data[1]/hh_address[1]/hh_address_ward[1]/house_no[1]");
            Log.wtf("Record ID", "Index Still Null");

        }
        surveyorIdIndex = formController.getIndexFromXPath("question./NHRP_dec_4/building_damage_assessment[1]/start_page[1]/enumerator_id[1]");
        if (surveyorIdIndex == null){
            surveyorIdIndex = formController.getIndexFromXPath("question./Training_form_dec21/enumerator_id[1]");
            Log.wtf("Surveyor ID", "Index Still Null");
        }
    }



    public int getLatestUniqueIdFromDb(String distrct, String vdc, String ward, String enum_area){
        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            c.moveToFirst();
            String formid = c.getString(c.getColumnIndex(FormsColumns.JR_FORM_ID));
            String formversion = c.getString(c.getColumnIndex(FormsColumns.JR_VERSION));
            String[] selectionArgs = new String[]{formid, formversion, distrct, vdc, ward, enum_area};
            String selection = InstanceColumns.JR_FORM_ID + "=? AND "
                    + InstanceColumns.JR_VERSION + "=? AND " + InstanceColumns.DISTRICT + "=? AND " + InstanceColumns.VDC + "=? AND " + InstanceColumns.WARD + "=? AND " + InstanceColumns.ENUMAREA + "=?";
            String sortOrder = InstanceColumns.RECORDID + " DESC";
            Cursor compositeInstance = managedQuery(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, sortOrder);
            if (compositeInstance.getCount() >= 1) {
                compositeInstance.moveToFirst();
                return compositeInstance.getInt(compositeInstance.getColumnIndex(InstanceColumns.RECORDID)) + 1;
            }
            c.close();

        }


        return 1;
    }

    private void setInitialValuesForSix(FormController fc){
        initialDistrictAnswer = fc.getQuestionPrompt(districtIndex).getAnswerValue();
        if (initialDistrictAnswer != null){
            Log.wtf("District", initialDistrictAnswer.toString());
        }
        initialVdcAnswer = fc.getQuestionPrompt(vdcIndex).getAnswerValue();
        initialWardAnswer = fc.getQuestionPrompt(wardIndex).getAnswerValue();
        initialEnumAreaAnswer = fc.getQuestionPrompt(enumAreaIndex).getAnswerValue();
    }

    /**
	 * called by the FormLoaderTask if something goes wrong.
	 */
	@Override
	public void loadingError(String errorMsg) {
		dismissDialog(PROGRESS_DIALOG);
		if (errorMsg != null) {
			createErrorDialog(errorMsg, EXIT);
		} else {
			createErrorDialog(getString(R.string.parse_error), EXIT);
		}
	}

	/**
	 * Called by SavetoDiskTask if everything saves correctly.
	 */
	@Override
	public void savingComplete(SaveResult saveResult) {
		dismissDialog(SAVING_DIALOG);

        int saveStatus = saveResult.getSaveResult();
        switch (saveStatus) {
		case SaveToDiskTask.SAVED:
			Toast.makeText(this, getString(R.string.data_saved_ok),
					Toast.LENGTH_SHORT).show();
			sendSavedBroadcast();
			break;
		case SaveToDiskTask.SAVED_AND_EXIT:
			Toast.makeText(this, getString(R.string.data_saved_ok),
					Toast.LENGTH_SHORT).show();
			sendSavedBroadcast();
			finishReturnInstance();
			break;
		case SaveToDiskTask.SAVE_ERROR:
            String message;
            if (saveResult.getSaveErrorMessage() != null) {
                message = getString(R.string.data_saved_error) + ": " + saveResult.getSaveErrorMessage();
            } else {
                message = getString(R.string.data_saved_error);
            }
            Toast.makeText(this, message,
                    Toast.LENGTH_LONG).show();
			break;
		case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
		case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
			refreshCurrentView();

			// get constraint behavior preference value with appropriate default
			String constraint_behavior = PreferenceManager.getDefaultSharedPreferences(this)
				.getString(PreferencesActivity.KEY_CONSTRAINT_BEHAVIOR,
					PreferencesActivity.CONSTRAINT_BEHAVIOR_DEFAULT);

			// an answer constraint was violated, so we need to display the proper toast(s)
			// if constraint behavior is on_swipe, this will happen if we do a 'swipe' to the next question
			if (constraint_behavior.equals(PreferencesActivity.CONSTRAINT_BEHAVIOR_ON_SWIPE))
				next();
			// otherwise, we can get the proper toast(s) by saving with constraint check
			else
				saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS);

			break;
		}
	}

    @Override
    public void onProgressStep(String stepMessage) {
        this.stepMessage = stepMessage;
        if (mProgressDialog != null) {
            mProgressDialog.setMessage(getString(R.string.please_wait) + "\n\n" + stepMessage);
        }
    }

	/**
	 * Attempts to save an answer to the specified index.
	 *
	 * @param answer
	 * @param index
	 * @param evaluateConstraints
	 * @return status as determined in FormEntryController
	 */
	public int saveAnswer(IAnswerData answer, FormIndex index,
			boolean evaluateConstraints) throws JavaRosaException {
		FormController formController = Collect.getInstance()
				.getFormController();
		if (evaluateConstraints) {
			return formController.answerQuestion(index, answer);
		} else {
			formController.saveAnswer(index, answer);
			return FormEntryController.ANSWER_OK;
		}
	}

	/**
	 * Checks the database to determine if the current instance being edited has
	 * already been 'marked completed'. A form can be 'unmarked' complete and
	 * then resaved.
	 *
	 * @return true if form has been marked completed, false otherwise.
	 */
	private boolean isInstanceComplete(boolean end) {
		FormController formController = Collect.getInstance()
				.getFormController();
		// default to false if we're mid form
		boolean complete = false;

		// if we're at the end of the form, then check the preferences
		if (end) {
			// First get the value from the preferences
			SharedPreferences sharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(this);
			complete = sharedPreferences.getBoolean(
					PreferencesActivity.KEY_COMPLETED_DEFAULT, true);
		}

		// Then see if we've already marked this form as complete before
		String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
		String[] selectionArgs = { formController.getInstancePath()
				.getAbsolutePath() };
		Cursor c = null;
		try {
			c = getContentResolver().query(InstanceColumns.CONTENT_URI, null,
					selection, selectionArgs, null);
			if (c != null && c.getCount() > 0) {
				c.moveToFirst();
				String status = c.getString(c
						.getColumnIndex(InstanceColumns.STATUS));
				if (InstanceProviderAPI.STATUS_COMPLETE.compareTo(status) == 0) {
					complete = true;
				}
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return complete;
	}

	public void next() {
		if (!mBeenSwiped) {
			mBeenSwiped = true;
			showNextView();
		}
	}

	/**
	 * Returns the instance that was just filled out to the calling activity, if
	 * requested.
	 */
	private void finishReturnInstance() {
		FormController formController = Collect.getInstance()
				.getFormController();
		String action = getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action)
				|| Intent.ACTION_EDIT.equals(action)) {
			// caller is waiting on a picked form
			String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String[] selectionArgs = { formController.getInstancePath()
					.getAbsolutePath() };
			Cursor c = null;
			try {
				c = getContentResolver().query(InstanceColumns.CONTENT_URI,
						null, selection, selectionArgs, null);
				if (c.getCount() > 0) {
					// should only be one...
					c.moveToFirst();
					String id = c.getString(c
							.getColumnIndex(InstanceColumns._ID));
					Uri instance = Uri.withAppendedPath(
							InstanceColumns.CONTENT_URI, id);
					setResult(RESULT_OK, new Intent().setData(instance));
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}
		finish();
	}

	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		// only check the swipe if it's enabled in preferences
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		String navigation = sharedPreferences.getString(
				PreferencesActivity.KEY_NAVIGATION,
				PreferencesActivity.NAVIGATION_SWIPE);
		Boolean doSwipe = false;
		if (navigation.contains(PreferencesActivity.NAVIGATION_SWIPE)) {
			doSwipe = true;
		}
		if (doSwipe) {
			// Looks for user swipes. If the user has swiped, move to the
			// appropriate screen.

			// for all screens a swipe is left/right of at least
			// .25" and up/down of less than .25"
			// OR left/right of > .5"
			DisplayMetrics dm = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(dm);
			int xPixelLimit = (int) (dm.xdpi * .25);
			int yPixelLimit = (int) (dm.ydpi * .25);

			if (mCurrentView instanceof ODKView) {
				if (((ODKView) mCurrentView).suppressFlingGesture(e1, e2,
						velocityX, velocityY)) {
					return false;
				}
			}

			if (mBeenSwiped) {
				return false;
			}

			if ((Math.abs(e1.getX() - e2.getX()) > xPixelLimit && Math.abs(e1
					.getY() - e2.getY()) < yPixelLimit)
					|| Math.abs(e1.getX() - e2.getX()) > xPixelLimit * 2) {
				mBeenSwiped = true;
				if (velocityX > 0) {
					if (e1.getX() > e2.getX()) {
						Log.e(t,
								"showNextView VelocityX is bogus! " + e1.getX()
										+ " > " + e2.getX());
						Collect.getInstance().getActivityLogger()
								.logInstanceAction(this, "onFling", "showNext");
						showNextView();
					} else {
						Collect.getInstance()
								.getActivityLogger()
								.logInstanceAction(this, "onFling",
										"showPrevious");
						showPreviousView();
					}
				} else {
					if (e1.getX() < e2.getX()) {
						Log.e(t,
								"showPreviousView VelocityX is bogus! "
										+ e1.getX() + " < " + e2.getX());
						Collect.getInstance()
								.getActivityLogger()
								.logInstanceAction(this, "onFling",
										"showPrevious");
						showPreviousView();
					} else {
						Collect.getInstance().getActivityLogger()
								.logInstanceAction(this, "onFling", "showNext");
						showNextView();
					}
				}
				return true;
			}
		}

		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		// The onFling() captures the 'up' event so our view thinks it gets long
		// pressed.
		// We don't wnat that, so cancel it.
        if (mCurrentView != null) {
            mCurrentView.cancelLongPress();
        }
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public void advance() {
		next();
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i("Activity","Start");



		if(mLocationManager!=null) {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this);
		}
		Collect.getInstance().getActivityLogger().logOnStart(this);
	}

	@Override
	protected void onStop() {
		Log.i("Activity","Stop");
		Collect.getInstance().getActivityLogger().logOnStop(this);
		if(mLocationManager!=null) {
			mLocationManager.removeUpdates(this);
		}
		super.onStop();
	}

	private void sendSavedBroadcast() {
		Intent i = new Intent();
		i.setAction("com.geoodk.collect.android.FormSaved");
		this.sendBroadcast(i);
	}

    @Override
    public void onSavePointError(String errorMessage) {
        if (errorMessage != null && errorMessage.trim().length() > 0) {
            Toast.makeText(this, getString(R.string.save_point_error, errorMessage), Toast.LENGTH_LONG).show();
        }
    }
}
