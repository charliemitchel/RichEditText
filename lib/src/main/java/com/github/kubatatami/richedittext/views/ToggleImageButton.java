package com.github.kubatatami.richedittext.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.widget.Checkable;

import com.github.kubatatami.richedittext.R;

public class ToggleImageButton extends AppCompatImageButton implements Checkable {

    private static final int[] CHECKED_STATE_SET = {
            android.R.attr.state_checked
    };

    private boolean isToggleOnClick = true;

    private boolean isChecked;

    private boolean isBroadCasting;

    private boolean uncheckable;

    private OnCheckedChangeListener onCheckedChangeListener;

    public ToggleImageButton(Context context) {
        super(context);
    }

    public ToggleImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttr(context, attrs);
    }

    public ToggleImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttr(context, attrs);
    }

    private void initAttr(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ToggleImageButton);
        boolean checked = a.getBoolean(R.styleable.ToggleImageButton_checked, false);
        int checkedTint = a.getColor(R.styleable.ToggleImageButton_checkedTint, 0);
        int defaultTint = a.getColor(R.styleable.ToggleImageButton_defaultTint, 0);
        if (checkedTint != 0 || defaultTint != 0) {
            setImageDrawable(getDrawable(), checkedTint, defaultTint);
        }
        setChecked(checked);
        a.recycle();
    }

    @Override
    public boolean performClick() {
        if (isToggleOnClick && (!uncheckable || !isChecked)) {
            toggle();
        }
        return super.performClick();
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    public boolean isToggleOnClick() {
        return isToggleOnClick;
    }

    public void setToggleOnClick(boolean toggleOnClick) {
        isToggleOnClick = toggleOnClick;
    }

    public void setChecked(boolean checked, boolean invokeListeners) {
        if (isChecked == checked) {
            return;
        }
        isChecked = checked;
        refreshDrawableState();
        if (invokeListeners) {
            if (isBroadCasting) {
                return;
            }
            isBroadCasting = true;
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, isChecked);
            }
            isBroadCasting = false;
        }
    }

    public void setImageDrawable(Drawable drawable, int checkedTint, int defaultTint) {
        if (drawable != null) {
            StateListDrawable stateListDrawable = new StateListDrawable();
            Drawable defaultDrawable = drawable.mutate();
            if (checkedTint != 0) {
                Drawable checkedDrawable = drawable.getConstantState().newDrawable().mutate();
                DrawableCompat.setTint(checkedDrawable, checkedTint);
                stateListDrawable.addState(new int[]{android.R.attr.state_checked}, checkedDrawable);
            }
            if (defaultTint != 0) {
                DrawableCompat.setTint(defaultDrawable, defaultTint);
            }
            stateListDrawable.addState(new int[]{}, defaultDrawable);
            super.setImageDrawable(stateListDrawable);
        } else {
            super.setImageDrawable(drawable);
        }
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener onCheckedChangeListener) {
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked, true);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putBoolean("state", isChecked);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle outState = (Bundle) state;
            setChecked(outState.getBoolean("state"));
            state = outState.getParcelable("instanceState");
        }
        super.onRestoreInstanceState(state);
    }

    public boolean isUncheckable() {
        return uncheckable;
    }

    public void setUncheckable(boolean uncheckable) {
        this.uncheckable = uncheckable;
    }

    public interface OnCheckedChangeListener {

        void onCheckedChanged(ToggleImageButton buttonView, boolean isChecked);
    }
}
