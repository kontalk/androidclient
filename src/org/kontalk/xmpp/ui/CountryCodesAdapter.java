/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmpp.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.client.NumberValidator;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.PhoneNumberUtil;


public class CountryCodesAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final List<CountryCode> mData;
    private final int mViewId;
    private final int mDropdownViewId;
    private int mSelected;

    public static final class CountryCode implements Comparable<String> {
        public String regionCode;
        public int countryCode;
        public String regionName;

        @Override
        public int compareTo(String another) {
            return regionCode != null && another != null ? regionCode.compareTo(another) : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o != null && o instanceof CountryCode) {
                CountryCode other = (CountryCode) o;

                if (regionCode != null && regionCode.equals(other.regionCode))
                    return true;

                return (countryCode == other.countryCode);
            }

            return false;
        }

        @Override
        public String toString() {
            return regionCode;
        }
    }

    public CountryCodesAdapter(Context context, int viewId, int dropdownViewId) {
        this(context, new ArrayList<CountryCode>(), viewId, dropdownViewId);
    }

    public CountryCodesAdapter(Context context, List<CountryCode> data, int viewId, int dropdownViewId) {
        mInflater = LayoutInflater.from(context);
        mData = data;
        mViewId = viewId;
        mDropdownViewId = dropdownViewId;
    }

    public void add(CountryCode entry) {
        mData.add(entry);
    }

    public void add(String regionCode) {
        CountryCode cc = new CountryCode();
        cc.regionCode = regionCode;
        cc.countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode);
        cc.regionName = NumberValidator.getRegionDisplayName(regionCode, Locale.getDefault());
        mData.add(cc);
    }

    public void clear() {
        mData.clear();
    }

    public void sort(Comparator<? super CountryCode> comparator) {
        Collections.sort(mData, comparator);
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        CountryCode e = mData.get(position);
        return (e != null) ? e.countryCode : -1;
    }

    public int getPositionForId(CountryCode cc) {
        Log.d("CC", "looking for region " + cc);
        return cc != null ? mData.indexOf(cc) : -1;
    }

    public void setSelected(int position) {
        mSelected = position;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        DropDownViewHolder holder;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mDropdownViewId, null, false);
            holder = new DropDownViewHolder();
            holder.icon = (ImageView) view.findViewById(android.R.id.icon);
            holder.description = (CheckedTextView) view.findViewById(android.R.id.text1);
            view.setTag(holder);
        }
        else {
            view = convertView;
            holder = (DropDownViewHolder) view.getTag();
        }

        CountryCode e = mData.get(position);

        StringBuilder text = new StringBuilder(5)
            .append(e.regionName)
            .append(" (+")
            .append(e.countryCode)
            .append(')');

        holder.description.setText(text);
        holder.description.setChecked((mSelected == position));
        // TEST
        holder.icon.setImageResource(R.drawable.flag_it);

        return view;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mViewId, null, false);
            holder = new ViewHolder();
            holder.description = (TextView) view.findViewById(android.R.id.text1);
            view.setTag(holder);
        }
        else {
            view = convertView;
            holder = (ViewHolder) view.getTag();
        }

        CountryCode e = mData.get(position);

        StringBuilder text = new StringBuilder(3)
            .append('+')
            .append(e.countryCode)
            .append(" (")
            .append(e.regionName)
            .append(')');

        holder.description.setText(text);

        return view;
    }

    private final static class ViewHolder {
        TextView description;
    }

    private final static class DropDownViewHolder {
        CheckedTextView description;
        ImageView icon;
    }
}
