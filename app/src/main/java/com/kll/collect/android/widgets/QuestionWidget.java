/*
 * Copyright (C) 2011 University of Washington
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

package com.kll.collect.android.widgets;

import java.util.ArrayList;
import java.util.List;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import com.kll.collect.android.R;
import com.kll.collect.android.application.Collect;
import com.kll.collect.android.views.MediaLayout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.opengl.Visibility;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public abstract class QuestionWidget extends LinearLayout {

    @SuppressWarnings("unused")
    private final static String t = "QuestionWidget";

	private static int idGenerator = 1211322;

	/**
	 * Generate a unique ID to keep Android UI happy when the screen orientation
	 * changes.
	 *
	 * @return
	 */
	public static int newUniqueId() {
		return ++idGenerator;
	}

    private LinearLayout.LayoutParams mLayout;
    private LinearLayout.LayoutParams mToggleLayout;
    private LinearLayout.LayoutParams mLinearLayout;
    private LinearLayout.LayoutParams mLinearLayoutAsteriks;
    private RelativeLayout.LayoutParams mRelativeLayout;
    protected FormEntryPrompt mPrompt;

    protected final int mQuestionFontsize;
    protected final int mAnswerFontsize;

    private TextView mQuestionText;
    private MediaLayout mediaLayout;
    private TextView mHelpText;
    private Button mHelpButton;
    private Button mCloseButton;
    private ImageView mRequired;

    public QuestionWidget(Context context, FormEntryPrompt p) {
        super(context);

        mQuestionFontsize = Collect.getQuestionFontsize();
        mAnswerFontsize = mQuestionFontsize + 2;
        mRequired = new ImageView(getContext());
        mRequired.setBackgroundResource(android.R.drawable.star_off);
        if(p.isRequired()){
            mRequired.setVisibility(View.VISIBLE);
        }else{
            mRequired.setVisibility(View.GONE);
        }
        mPrompt = p;

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.TOP);
        setPadding(0, 7, 0, 0);

        mLayout =
            new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

        mToggleLayout =
                new LinearLayout.LayoutParams(new ViewGroup.LayoutParams(40,40));

        mRelativeLayout =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        mLinearLayout =
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        //mLinearLayoutAsteriks = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
        // LinearLayout.LayoutParams.WRAP_CONTENT);
       // mLinearLayoutAsteriks.gravity = Gravity.RIGHT;
//        mRequired.setLayoutParams(mLinearLayoutAsteriks);
        mLayout.setMargins(10,20,10,0);
        //addView(mRequired);
        addQuestionText(p);
       // addHelpText(p);

    }

    public void playAudio() {
    	mediaLayout.playAudio();
    }

    public void playVideo() {
    	mediaLayout.playVideo();
    }

    public FormEntryPrompt getPrompt() {
        return mPrompt;
    }

   	// http://code.google.com/p/android/issues/detail?id=8488
    private void recycleDrawablesRecursive(ViewGroup viewGroup, List<ImageView> images) {

        int childCount = viewGroup.getChildCount();
        for(int index = 0; index < childCount; index++)
        {
          View child = viewGroup.getChildAt(index);
          if ( child instanceof ImageView ) {
        	  images.add((ImageView)child);
          } else if ( child instanceof ViewGroup ) {
        	  recycleDrawablesRecursive((ViewGroup) child, images);
          }
        }
        viewGroup.destroyDrawingCache();
    }

   	// http://code.google.com/p/android/issues/detail?id=8488
    public void recycleDrawables() {
    	List<ImageView> images = new ArrayList<ImageView>();
    	// collect all the image views
    	recycleDrawablesRecursive(this, images);
    	for ( ImageView imageView : images ) {
    		imageView.destroyDrawingCache();
    		Drawable d = imageView.getDrawable();
    		if ( d != null && d instanceof BitmapDrawable) {
    			imageView.setImageDrawable(null);
    			BitmapDrawable bd = (BitmapDrawable) d;
    			Bitmap bmp = bd.getBitmap();
    			if ( bmp != null ) {
    				bmp.recycle();
    			}
    		}
    	}
    }

    // Abstract methods
    public abstract IAnswerData getAnswer();


    public abstract void clearAnswer();


    public abstract void setFocus(Context context);


    public abstract void setOnLongClickListener(OnLongClickListener l);

    /**
     * Override this to implement fling gesture suppression (e.g. for embedded WebView treatments).
     * @param e1
     * @param e2
     * @param velocityX
     * @param velocityY
     * @return true if the fling gesture should be suppressed
     */
    public boolean suppressFlingGesture(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    	return false;
    }

    /**
     * Add a Views containing the question text, audio (if applicable), and image (if applicable).
     * To satisfy the RelativeLayout constraints, we add the audio first if it exists, then the
     * TextView to fit the rest of the space, then the image if applicable.
     */
    protected void addQuestionText(FormEntryPrompt p) {
        String imageURI = p.getImageText();
        String audioURI = p.getAudioText();
        String videoURI = p.getSpecialFormQuestionText("video");
        String s = p.getHelpText();
        // shown when image is clicked
        String bigImageURI = p.getSpecialFormQuestionText("big-image");

        String promptText = p.getLongText();

        mQuestionText = new TextView(getContext());
        Boolean readOnly = p.isReadOnly();
        Log.i("need backgroung", Boolean.toString(!readOnly));
        if(!readOnly) {
            mQuestionText.setBackgroundColor(0xFFC0C0C0);
        }

        if (s != null && !s.equals("")) {
            LinearLayout ll1 = new LinearLayout(getContext());
            LinearLayout ll2 = new LinearLayout(getContext());
            RelativeLayout realativeLayout = new RelativeLayout(getContext());



            mQuestionText.setText(promptText == null ? "" : promptText);
            mQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);
            mQuestionText.setTypeface(null, Typeface.BOLD);
            mQuestionText.setPadding(0, 0,0, 7);
            mQuestionText.setId(QuestionWidget.newUniqueId()); // assign random id

            mHelpText = new TextView(getContext());
            mHelpText.setHorizontallyScrolling(false);
            mHelpText.setText(s);
            mHelpText.setTypeface(null, Typeface.ITALIC);

            // Wrap to the size of the parent view
            mQuestionText.setHorizontallyScrolling(false);

            if (promptText == null || promptText.length() == 0) {
                mQuestionText.setVisibility(GONE);
            }
            mHelpButton = new Button(getContext());
            mCloseButton = new Button(getContext());
            // Create the layout for audio, image, text
            mediaLayout = new MediaLayout(getContext());

            mediaLayout.setAVT(p.getIndex(), "", mQuestionText, audioURI, imageURI, videoURI, bigImageURI);

            ll2.setLayoutParams(mLinearLayout);
            ll1.addView(mediaLayout, mLayout);
            ll1.setGravity(Gravity.LEFT);
            ll2.setPadding(0,0,4,0);

            mHelpButton.setText(null);
            mHelpButton.setBackgroundResource(R.drawable.questionmark);
            mHelpButton.setGravity(Gravity.RIGHT);

            mCloseButton.setText(null);
            mCloseButton.setBackgroundResource(R.drawable.cross);
            mCloseButton.setVisibility(View.GONE);
            mCloseButton.setGravity(Gravity.RIGHT);
            ll2.addView(mHelpButton, mToggleLayout);
            ll2.addView(mCloseButton, mToggleLayout);
            ll2.setGravity(Gravity.RIGHT);

            realativeLayout.setLayoutParams(mLinearLayout);
            mHelpText.setVisibility(View.GONE);
            mRelativeLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            realativeLayout.addView(ll1);
            realativeLayout.addView(ll2, mRelativeLayout);
            addView(realativeLayout, mLayout);
            addView(mHelpText,mLayout);
            mHelpButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHint();
                }
            });
            mCloseButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeHint();
                }
            });
        }
        else {
            // Add the text view. Textview always exists, regardless of whether there's text.



            mQuestionText.setText(promptText == null ? "" : promptText);
            mQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize);
            mQuestionText.setTypeface(null, Typeface.BOLD);
            mQuestionText.setPadding(0, 0, 0, 7);
            mQuestionText.setId(QuestionWidget.newUniqueId()); // assign random id

            // Wrap to the size of the parent view
            mQuestionText.setHorizontallyScrolling(false);

            if (promptText == null || promptText.length() == 0) {
                mQuestionText.setVisibility(GONE);
            }

            // Create the layout for audio, image, text
            mediaLayout = new MediaLayout(getContext());
            mediaLayout.setAVT(p.getIndex(), "", mQuestionText, audioURI, imageURI, videoURI, bigImageURI);


            addView(mediaLayout, mLayout);
        }

    }


    /**
     * Add a TextView containing the help text.
     */

    private void addHelpText(FormEntryPrompt p) {

        String s = p.getHelpText();

        if (s != null && !s.equals("")) {
            LinearLayout ll1 = new LinearLayout(getContext());
            LinearLayout ll2 = new LinearLayout(getContext());
            RelativeLayout realativeLayout = new RelativeLayout(getContext());



            mHelpText = new TextView(getContext());

            mHelpButton = new Button(getContext());
            mCloseButton = new Button(getContext());
             mHelpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontsize - 3);
            mHelpText.setPadding(0, 5, 0, 7);
            // wrap to the widget of view
            mHelpText.setHorizontallyScrolling(false);
            mHelpText.setText(s);
            mHelpText.setTypeface(null, Typeface.ITALIC);
            ll2.setLayoutParams(mLinearLayout);
             ll1.addView(mHelpText, mLayout);
            ll1.setGravity(Gravity.LEFT);
            mHelpButton.setText(null);
            mHelpButton.setBackgroundResource(android.R.drawable.ic_menu_help);
            mHelpButton.setGravity(Gravity.RIGHT);

            mCloseButton.setText(null);
            mCloseButton.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
            mCloseButton.setVisibility(View.GONE);
            mCloseButton.setGravity(Gravity.RIGHT);
            ll2.addView(mHelpButton, mToggleLayout);
            ll2.addView(mCloseButton, mToggleLayout);
            ll2.setGravity(Gravity.RIGHT);

            realativeLayout.setLayoutParams(mLinearLayout);
            mHelpText.setVisibility(View.GONE);
            mRelativeLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
           realativeLayout.addView(ll1);
            realativeLayout.addView(ll2,mRelativeLayout);
            addView(realativeLayout, mLayout);
            mHelpButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHint();
                }
            });
            mCloseButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeHint();
                }
            });

        }
    }

    private void removeHint() {
        mHelpText.setVisibility(View.GONE);
        mCloseButton.setVisibility(View.GONE);
        mHelpButton.setVisibility(View.VISIBLE);
       //removeView(mHelpText);
    }

    private void showHint() {
        mHelpText.setVisibility(View.VISIBLE);
        mHelpButton.setVisibility(View.GONE);
        mCloseButton.setVisibility(View.VISIBLE);

    }


    /**
     * Every subclassed widget should override this, adding any views they may contain, and calling
     * super.cancelLongPress()
     */
    public void cancelLongPress() {
        super.cancelLongPress();
        if (mQuestionText != null) {
            mQuestionText.cancelLongPress();
        }
        if (mHelpText != null) {
            mHelpText.cancelLongPress();
        }
    }

}
