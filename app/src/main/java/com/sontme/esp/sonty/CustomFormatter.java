package com.sontme.esp.sonty;

import android.util.Log;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.text.DecimalFormat;

class CustomFormatter extends ValueFormatter {

    private DecimalFormat mFormat;

    public CustomFormatter() {
        //mFormat = new DecimalFormat();
        mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        //mFormat = new DecimalFormat("#");
    }


    @Override
    public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
        Log.d("chart_", "value: " + value);
        return mFormat.format(value) + " asd";
        /*if (value > 0) {
            return mFormat.format(value) + "asd";
        } else {
            return "" + "asd";
        }*/
    }
}
