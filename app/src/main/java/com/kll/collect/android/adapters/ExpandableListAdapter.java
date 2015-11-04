package com.kll.collect.android.adapters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kll.collect.android.R;

import android.app.Activity;
import android.content.Context;

import android.graphics.Typeface;

import android.view.LayoutInflater;
import android.view.View;

import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;

import android.widget.TextView;

public class ExpandableListAdapter extends BaseExpandableListAdapter {

    private Activity context;
    private HashMap<String, List<String>> listDataHeader;
    private List<String> listDataChild;

    public ExpandableListAdapter()
    {
    }

    public ExpandableListAdapter(Activity context, List<String> listDataChild,
                                 HashMap<String, List<String>> listDataHeader) {
        this.context = context;
        this.listDataHeader = listDataHeader;
        this.listDataChild = listDataChild;
    }

    public Object getChild(int groupPosition, int childPosition) {
        return listDataHeader.get(listDataChild.get(groupPosition)).get(childPosition);
    }

    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }


    public View getChildView(final int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        final String stat = (String) getChild(groupPosition, childPosition);
        LayoutInflater inflater = context.getLayoutInflater();
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.stat_child, null);
        }

        TextView item = (TextView) convertView.findViewById(R.id.status_stat);


        item.setText(stat);
        return convertView;
    }

    public int getChildrenCount(int groupPosition) {
        return listDataHeader.get(listDataChild.get(groupPosition)).size();
    }

    public Object getGroup(int groupPosition) {
        return listDataChild.get(groupPosition);
    }

    public int getGroupCount() {
        return listDataChild.size();
    }

    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        String groupName = (String) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater infalInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = infalInflater.inflate(R.layout.stat_group,
                    null);
        }
        TextView item = (TextView) convertView.findViewById(R.id.form_name);
        item.setTypeface(null, Typeface.BOLD);
        item.setText(groupName);
        return convertView;
    }

    public boolean hasStableIds() {
        return true;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}