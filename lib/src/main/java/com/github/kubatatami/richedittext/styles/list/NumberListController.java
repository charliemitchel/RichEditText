package com.github.kubatatami.richedittext.styles.list;

import android.graphics.Paint;
import android.support.annotation.NonNull;

import com.github.kubatatami.richedittext.styles.base.RichSpan;

public class NumberListController extends ListController<NumberListController.RichNumberIndentSpan> {

    public NumberListController() {
        super(NumberListController.RichNumberIndentSpan.class, "ol");
    }

    public static class RichNumberIndentSpan extends ListItemSpan implements RichSpan {

        @NonNull
        protected String getText(int index) {
            return index + ".";
        }

        @Override
        protected float getMeasureWidth(Paint paint, int index) {
            return paint.measureText(index + ".  ");
        }

    }

}
