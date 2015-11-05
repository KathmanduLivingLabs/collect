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

package com.kll.collect.android.widgets;


import java.util.Vector;


import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.expr.XPathFuncExpr;


import com.kll.collect.android.R;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;



import com.kll.collect.android.external.ExternalDataUtil;

import com.kll.collect.android.views.ClearableAutoCompleteTextView;

/**
 * SpinnerWidget handles select-one fields. Instead of a list of buttons it uses a spinner, wherein
 * the user clicks a button and the choices pop up in a dialogue box. The goal is to be more
 * compact. If images, audio, or video are specified in the select answers they are ignored.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class SpinnerWidget extends QuestionWidget {
    Vector<SelectChoice> mItems;
    ClearableAutoCompleteTextView  autoCompleteTextView;
    String[] choices;
    final ArrayAdapter<String> adapter;


    private static final int BROWN = 0xFF936931;
    int answerPosition = -1;

    public SpinnerWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        // SurveyCTO-added support for dynamic select content (from .csv files)
        XPathFuncExpr xPathFuncExpr = ExternalDataUtil.getSearchXPathExpression(prompt.getAppearanceHint());
        if (xPathFuncExpr != null) {
            mItems = ExternalDataUtil.populateExternalChoices(prompt, xPathFuncExpr);
        } else {
            mItems = prompt.getSelectChoices();
        }

        autoCompleteTextView = new ClearableAutoCompleteTextView(context);
        choices = new String[mItems.size()];

        for (int i = 0; i < mItems.size(); i++) {
            choices[i] = prompt.getSelectChoiceText(mItems.get(i));


        }

        adapter = new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_dropdown_item,choices);

        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.setThreshold(1);
        autoCompleteTextView.enoughToFilter();
        autoCompleteTextView.setCursorVisible(true);
        autoCompleteTextView.hideClearButton();
        autoCompleteTextView.setHint(R.string.select_one);

        autoCompleteTextView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {

                autoCompleteTextView.showDropDown();

            }
        });

        autoCompleteTextView.setOnClearListener(new ClearableAutoCompleteTextView.OnClearListener() {

            @Override
            public void onClear() {
                clearSelection();
            }
        });

        String s = null;
        if (prompt.getAnswerValue() != null) {
            s = ((Selection) prompt.getAnswerValue().getValue()).getValue();
            Log.i("Answer s",s);
        }

        // autoCompleteTextView.setText(R.string.select_one);
        if (s != null) {
            for (int i = 0; i < mItems.size(); ++i) {
                String sMatch = mItems.get(i).getValue();
                if (sMatch.equals(s)) {
                    Log.i("Answer sMatch", sMatch);
                    autoCompleteTextView.setText("");
                    autoCompleteTextView.setText(prompt.getAnswerText(),false);
                    Log.i("Answer selected", prompt.getAnswerText());
                    answerPosition = i;
                    Log.i("Answer pos",Integer.toString(answerPosition));
                    autoCompleteTextView.showClearButton();
                    autoCompleteTextView.setFocusable(false);
                    autoCompleteTextView.setFocusableInTouchMode(false);
                }
            }
        }

        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selection = (String) parent.getItemAtPosition(position);
                int pos = -1;
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i].equals(selection)) {
                        answerPosition = i;
                    }
                }
                autoCompleteTextView.showClearButton();
                autoCompleteTextView.setFocusable(false);
                autoCompleteTextView.setFocusableInTouchMode(false);
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(autoCompleteTextView.getWindowToken(), 0);
                Log.i("answer position", String.valueOf(answerPosition));

            }
        });






        addView(autoCompleteTextView);

    }

    @Override
    public IAnswerData getAnswer() {
        clearFocus();
        int i = answerPosition;
        if (i == -1 || i == mItems.size()) {
            return null;
        } else {
            SelectChoice sc = mItems.elementAt(i);

            return new SelectOneData(new Selection(sc));
        }
    }

    @Override
    public void clearAnswer() {
        autoCompleteTextView.setText(R.string.select_one);
    }

    @Override
    public void setFocus(Context context) {
        InputMethodManager inputManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInputFromInputMethod(this.getWindowToken(), 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        autoCompleteTextView.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        autoCompleteTextView.cancelLongPress();
    }




    protected void clearSelection() {

        answerPosition = -1;
        autoCompleteTextView.hideClearButton();
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.setText("");
        autoCompleteTextView.showDropDown();

        // hide the keyboard
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(autoCompleteTextView.getWindowToken(), 0);

    }
}
