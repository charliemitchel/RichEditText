package com.github.kubatatami.richedittext;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.github.kubatatami.richedittext.modules.HistoryModule;
import com.github.kubatatami.richedittext.modules.HtmlExportModule;
import com.github.kubatatami.richedittext.modules.HtmlImportModule;
import com.github.kubatatami.richedittext.modules.InseparableModule;
import com.github.kubatatami.richedittext.modules.StyleSelectionInfo;
import com.github.kubatatami.richedittext.other.CompatUtils;
import com.github.kubatatami.richedittext.other.SpanUtil;
import com.github.kubatatami.richedittext.other.TextWatcherAdapter;
import com.github.kubatatami.richedittext.styles.base.BinarySpanController;
import com.github.kubatatami.richedittext.styles.base.MultiSpanController;
import com.github.kubatatami.richedittext.styles.base.SpanController;
import com.github.kubatatami.richedittext.styles.base.StartStyleProperty;
import com.github.kubatatami.richedittext.utils.ComposingSpanFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseRichEditText extends AppCompatEditText {

    private static final boolean DEBUG = false;

    private static final long DEFAULT_TEXT_CHANGE_MS = 1000;

    private static Context appContext;

    private final HistoryModule historyModule = new HistoryModule(this);

    private final HtmlExportModule htmlExportModule = new HtmlExportModule();

    private final Map<Class<?>, SpanController<?, ?>> spanControllerMap = new HashMap<>();

    private final List<StartStyleProperty> properties = new ArrayList<>();

    private final List<OnFocusChangeListener> onFocusChangeListeners = new ArrayList<>();

    private final List<OnValueChangeListener<Editable>> onTextChangeListeners = new ArrayList<>();

    private final List<OnValueChangeListener<Editable>> onTextChangeDelayedListeners = new ArrayList<>();

    private boolean inflateFinished;

    private boolean oneStyleMode;

    private Handler handler;

    private boolean passiveStatus;

    private boolean ignoreWindowFocusChange;

    private long onTextChangeDelayMs = DEFAULT_TEXT_CHANGE_MS;

    private OnFocusChangeListener onFocusChangeListener;

    private Runnable textChangeRunnable = new Runnable() {
        @Override
        public void run() {
            invokeTextChangeDelayedListeners();
        }
    };

    private ComposingSpanFactory composingSpanFactory = new ComposingSpanFactory(historyModule);

    private TextWatcher mainTextChangedListener = new TextWatcherAdapter() {

        private boolean removed;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            historyModule.saveHistory();
            checkBeforeChange(after > 0);
            removed = SpanUtil.removeUnusedSpans(BaseRichEditText.this, spanControllerMap.values(), start, count, after);
        }

        @Override
        public void afterTextChanged(Editable s) {
            super.afterTextChanged(s);
            if (removed) {
                SpanUtil.inclusiveSpans(BaseRichEditText.this, spanControllerMap.values());
            }
        }
    };

    public BaseRichEditText(Context context) {
        super(context);
        init(context);
    }

    public BaseRichEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BaseRichEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public static Context getAppContext() {
        return appContext;
    }

    private void init(Context context) {
        appContext = context.getApplicationContext();
        super.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                for (OnFocusChangeListener listener : onFocusChangeListeners) {
                    listener.onFocusChange(v, hasFocus);
                }
            }
        });
        composingSpanFactory.setOnSpanChangeListeners(new OnValueChangeListener<Editable>() {
            @Override
            public void onValueChange(Editable value) {
                invokeTextListeners();
            }
        });
        setEditableFactory(composingSpanFactory);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addTextChangedListener(mainTextChangedListener);
        inflateFinished = true;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (handler == null) {
            handler = new Handler();
        }
        invokeTextListeners();
        if (onTextChangeDelayMs >= 0) {
            handler.postDelayed(textChangeRunnable, onTextChangeDelayMs);
        }
    }

    private void invokeTextListeners() {
        invokeTextChangeListeners();
        handler.removeCallbacks(textChangeRunnable);
    }

    @Override
    public boolean isSuggestionsEnabled() {
        return false;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        checkAfterChange(passiveStatus);
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        checkAfterChange(passiveStatus);
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        checkAfterChange(passiveStatus);
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        super.setTextColor(colors);
        checkAfterChange(passiveStatus);
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener listener) {
        if (listener == null) {
            onFocusChangeListeners.remove(onFocusChangeListener);
        } else {
            onFocusChangeListener = listener;
            onFocusChangeListeners.add(onFocusChangeListener);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!ignoreWindowFocusChange || hasWindowFocus) {
            super.onWindowFocusChanged(true);
        }
    }

    public void addOnFocusChangeListener(OnFocusChangeListener listener) {
        onFocusChangeListeners.add(listener);
    }

    public void removeOnFocusChangeListener(OnFocusChangeListener listener) {
        onFocusChangeListeners.remove(listener);
    }

    public void isValidHtml(String html) throws IOException {
        isValidHtml(html, "");
    }

    public void isValidHtml(String html, String style) throws IOException {
        HtmlImportModule.fromHtml(this, html, spanControllerMap.values(), properties, style, true);
    }

    public void setHtml(String html, String style) {
        try {
            passiveStatus = true;
            setText(HtmlImportModule.fromHtml(this, html, spanControllerMap.values(), properties, style, false));
            passiveStatus = false;
            checkAfterChange(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public <T extends SpanController<?, ?>> void registerController(Class<T> clazz, T controller) {
        if (spanControllerMap.containsKey(clazz)) {
            controller.addListenersFromController(spanControllerMap.get(clazz));
        }
        spanControllerMap.put(clazz, controller);
    }

    public void registerProperty(StartStyleProperty property) {
        properties.add(property);
    }

    @SuppressWarnings("unchecked")
    public <T extends SpanController<?, ?>> T getModule(Class<T> clazz) {
        return (T) spanControllerMap.get(clazz);
    }

    private void checkBeforeChange(boolean added) {
        if (inflateFinished) {
            StyleSelectionInfo styleSelectionInfo = StyleSelectionInfo.getStyleSelectionInfo(this);
            for (SpanController<?, ?> controller : spanControllerMap.values()) {
                controller.checkBeforeChange(getText(), styleSelectionInfo, added);
            }
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection result = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return result;
    }

    protected StyleSelectionInfo getAllSelectionInfo() {
        return new StyleSelectionInfo(0, length(), 0, length(), true);
    }

    public void checkAfterChange(boolean passive) {
        if (inflateFinished) {
            StyleSelectionInfo styleSelectionInfo = StyleSelectionInfo.getStyleSelectionInfo(this);
            for (SpanController<?, ?> controller : spanControllerMap.values()) {
                controller.checkAfterChange(this, styleSelectionInfo, passive);
            }
            if (DEBUG) {
                SpanUtil.logSpans(getText(), spanControllerMap.values());
            }
        }
    }

    public boolean isDuringRestoreHistoryPoint() {
        return historyModule.isDuringRestoreHistoryPoint();
    }

    public String getHtml() {
        return getHtml(true);
    }

    public void setHtml(String html) {
        setHtml(html, null);
    }

    public String getHtml(boolean standalone) {
        return htmlExportModule.getHtml(this, spanControllerMap.values(), properties, standalone);
    }

    public String getCssStyle() {
        return htmlExportModule.getCssStyle(this, spanControllerMap.values(), properties);
    }

    public void undo() {
        historyModule.undo();
    }

    public void redo() {
        historyModule.redo();
    }

    public void setHistoryLimit(int limit) {
        historyModule.setLimit(limit);
    }

    public boolean isHistoryEnabled() {
        return historyModule.isEnabled();
    }

    public void setHistoryEnabled(boolean enabled) {
        historyModule.setEnabled(enabled);
    }

    public boolean isStyled() {
        Object[] spans = getText().getSpans(0, getText().length(), Object.class);
        for (Object span : spans) {
            for (SpanController<?, ?> controller : spanControllerMap.values()) {
                if (controller.acceptSpan(span)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getTextOrHtml() {
        return isStyled() ? getHtml() : getText().toString();
    }

    void binaryClick(Class<? extends BinarySpanController<?>> clazz) {
        getModule(clazz).perform(getText(), getCurrentSelection());
        historyModule.saveHistory();
        checkAfterChange(true);
    }

    <T> void multiClick(T value, Class<? extends MultiSpanController<?, T>> clazz) {
        getModule(clazz).perform(value, getText(), getCurrentSelection());
        historyModule.saveHistory();
    }

    protected StyleSelectionInfo getCurrentSelection() {
        if (oneStyleMode) {
            return getAllSelectionInfo();
        } else {
            return StyleSelectionInfo.getStyleSelectionInfo(this);
        }
    }

    protected void invokeTextChangeDelayedListeners() {
        for (OnValueChangeListener<Editable> listener : onTextChangeDelayedListeners) {
            listener.onValueChange(getText());
        }
    }

    protected void invokeTextChangeListeners() {
        if (onTextChangeListeners != null) {
            for (OnValueChangeListener<Editable> listener : onTextChangeListeners) {
                listener.onValueChange(getText());
            }
        }
    }

    public void addOnTextChangeListener(OnValueChangeListener<Editable> onValueChangeListener) {
        onTextChangeListeners.add(onValueChangeListener);
    }

    public void removeOnTextChangeListener(OnValueChangeListener<Editable> onValueChangeListener) {
        onTextChangeListeners.remove(onValueChangeListener);
    }

    public void addOnTextChangeDelayedListener(OnValueChangeListener<Editable> onValueChangeListener) {
        onTextChangeDelayedListeners.add(onValueChangeListener);
    }

    public void removeOnTextChangeDelayedListener(OnValueChangeListener<Editable> onValueChangeListener) {
        onTextChangeDelayedListeners.remove(onValueChangeListener);
    }

    public long getOnTextChangeDelay() {
        return onTextChangeDelayMs;
    }

    public void setOnTextChangeDelay(long delayMs) {
        this.onTextChangeDelayMs = delayMs;
    }

    public void addOnHistoryChangeListener(HistoryModule.OnHistoryChangeListener onHistoryChangeListener) {
        historyModule.addOnHistoryChangeListener(onHistoryChangeListener);
    }

    public void removeOnHistoryChangeListener(HistoryModule.OnHistoryChangeListener onHistoryChangeListener) {
        historyModule.removeOnHistoryChangeListener(onHistoryChangeListener);
    }

    public float getLineSpacingMultiplierCompat() {
        return CompatUtils.getLineSpacingMultiplier(this);
    }

    public float getLineSpacingExtraCompat() {
        return CompatUtils.getLineSpacingExtra(this);
    }

    public boolean isOneStyleMode() {
        return oneStyleMode;
    }

    public void setOneStyleMode(boolean oneStyleMode) {
        this.oneStyleMode = oneStyleMode;
    }

    public void setIgnoreWindowFocusChange(boolean ignoreWindowFocusChange) {
        this.ignoreWindowFocusChange = ignoreWindowFocusChange;
    }

    public void setInseparable(int start, int end) {
        InseparableModule.setInseparable(getEditableText(), start, end);
    }

    public void addInseparable(String text) {
        if (getSelectionStart() != -1) {
            addInseparable(text, getSelectionStart(), getSelectionEnd());
        } else {
            addInseparable(text, length(), length());
        }
    }

    public void addInseparable(String text, int start, int end) {
        InseparableModule.addInseparable(getEditableText(), text, start, end);
    }

    public String getSelectedText() {
        if (getSelectionStart() == -1) {
            return "";
        } else {
            return getText().subSequence(getSelectionStart(), getSelectionEnd()).toString();
        }
    }

    public void replaceTextInSelection(String textToInsert) {
        int start = Math.max(getSelectionStart(), 0);
        int end = Math.max(getSelectionEnd(), 0);
        getText().replace(Math.min(start, end), Math.max(start, end), textToInsert);
    }

    public boolean isInseparableEnabled() {
        return InseparableModule.isEnabled();
    }

    public void setInseparableEnabled(boolean inseparableEnabled) {
        InseparableModule.setEnabled(inseparableEnabled);
    }

    public void changeTextOnSpan(String text, Object span) {
        boolean inseparableEnabled = isInseparableEnabled();
        Editable editable = getEditableText();
        setInseparableEnabled(false);
        int start = editable.getSpanStart(span);
        int end = editable.getSpanEnd(span);
        editable.replace(start, end, text);
        setInseparableEnabled(inseparableEnabled);
    }

    public String getSpanText(Object span) {
        Editable editable = getEditableText();
        int start = editable.getSpanStart(span);
        int end = editable.getSpanEnd(span);
        return editable.subSequence(start, end).toString();
    }

    public HistoryModule.HistoryPoint createHistoryPoint() {
        return historyModule.createHistoryPoint();
    }

    public void restoreHistoryPoint(HistoryModule.HistoryPoint historyPoint) {
        historyModule.restoreHistoryPoint(historyPoint);
    }

    public interface OnValueChangeListener<T> {

        void onValueChange(T value);
    }
}
