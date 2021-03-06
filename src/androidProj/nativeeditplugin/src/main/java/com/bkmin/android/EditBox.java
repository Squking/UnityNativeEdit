package com.bkmin.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

public class EditBox {
    // Simplest way to notify the EditBox about the application lifecycle.
    class EditTextLifeCycle extends EditText {
        EditBox observerBox;

        public EditTextLifeCycle(Context context, EditBox box) {
            super(context);
            this.observerBox = box;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (!hasWindowFocus)
                observerBox.notifyFocusChanged(hasWindowFocus);
        }
    }

    private EditTextLifeCycle edit;
    private final RelativeLayout layout;
    private int tag;
    private int characterLimit;

    private static SparseArray<EditBox> editBoxMap = null;
    private static InputMethodManager inputMethodManager;
    private static int keyboardHeight = 0;
    private static int keyboardHeightThreshold = 0;

    private static final String MSG_CREATE = "CreateEdit";
    private static final String MSG_REMOVE = "RemoveEdit";
    private static final String MSG_SET_TEXT = "SetText";
    private static final String MSG_SET_RECT = "SetRect";
    private static final String MSG_SET_TEXTSIZE = "SetTextSize";
    private static final String MSG_SET_FOCUS = "SetFocus";
    private static final String MSG_SET_VISIBLE = "SetVisible";
    private static final String MSG_TEXT_CHANGE = "TextChange";
    private static final String MSG_TEXT_BEGIN_EDIT = "TextBeginEdit";
    private static final String MSG_TEXT_END_EDIT = "TextEndEdit";
    private static final String MSG_ANDROID_KEY_DOWN = "AndroidKeyDown";
    private static final String MSG_RETURN_PRESSED = "ReturnPressed";

    static void processRecvJsonMsg(RelativeLayout mainLayout, int nSenderId, final String strJson) {
        if (editBoxMap == null) {
            editBoxMap = new SparseArray<>();
            // add listener to get keyboard height
            keyboardHeightThreshold = NativeEditPlugin.rootView.getHeight() / 4;
            NativeEditPlugin.rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                private Rect rect = new Rect();
                private int windowVisibleBottomWithoutKeyboard = 0;

                @Override
                public void onGlobalLayout() {
                    rect.setEmpty();
                    NativeEditPlugin.rootView.getWindowVisibleDisplayFrame(rect);
                    int delta = windowVisibleBottomWithoutKeyboard - rect.bottom;
                    if (delta > keyboardHeightThreshold) {
                        boolean firstSetKeyboardHeight = keyboardHeight == 0;
                        keyboardHeight = delta;
                        if (firstSetKeyboardHeight)
                            // keyboardHeight has not been set when trying to input for the 1st time. Call showKeyboard(true) manually.
                            for (int i = 0; i < editBoxMap.size(); i++) {
                                EditBox box = editBoxMap.valueAt(i);
                                if (box.isFocused()) {
                                    box.showKeyboard(true);
                                    break;
                                }
                            }
                    } else {
                        windowVisibleBottomWithoutKeyboard = rect.bottom;
                    }
                }
            });
        }

        try {
            JSONObject jsonMsg = new JSONObject(strJson);
            String msg = jsonMsg.getString("msg");

            if (msg.equals(MSG_CREATE)) {
                EditBox nb = new EditBox(mainLayout);
                nb.Create(nSenderId, jsonMsg);
                editBoxMap.append(nSenderId, nb);
            } else {
                EditBox eb = editBoxMap.get(nSenderId);
                if (eb != null) {
                    eb.processJsonMsg(jsonMsg);
                } else {
                    Log.e(NativeEditPlugin.LOG_TAG, "EditBox not found, id : " + nSenderId);
                }
            }
        } catch (JSONException e) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private EditBox(RelativeLayout mainLayout) {
        layout = mainLayout;
        edit = null;

        //Tap on the layout to clear focus of EditText
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                layout.setFocusable(true);
                layout.setFocusableInTouchMode(true);
                layout.requestFocus();
                return false;
            }
        });
    }

    private void showKeyboard(boolean isShow) {
        if (inputMethodManager == null)
            inputMethodManager = (InputMethodManager) NativeEditPlugin.unityActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);

        int scrollY = 0;
        if (isShow) {
            inputMethodManager.showSoftInput(edit, InputMethodManager.SHOW_FORCED);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) edit.getLayoutParams();
            int bottomMargin = layout.getHeight() - lp.topMargin - lp.height;
            if (bottomMargin < keyboardHeight)
                // Keyboard will cover the EditText. Scroll up rootView to show whole area of EditText just above keyboard.
                scrollY = keyboardHeight - bottomMargin;
        } else {
            NativeEditPlugin.unityActivity.getWindow().getDecorView().clearFocus();
            inputMethodManager.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        }
        NativeEditPlugin.rootView.setScrollY(scrollY);
    }

    private void notifyFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus)
            showKeyboard(false);
    }

    private void processJsonMsg(JSONObject jsonMsg) {
        try {
            String msg = jsonMsg.getString("msg");

            switch (msg) {
                case MSG_REMOVE:
                    this.Remove();
                    break;
                case MSG_SET_TEXT:
                    String text = jsonMsg.getString("text");
                    this.SetText(text);
                    break;
                case MSG_SET_RECT:
                    this.SetRect(jsonMsg);
                    break;
                case MSG_SET_TEXTSIZE:
                    this.SetTextSize(jsonMsg);
                    break;
                case MSG_SET_FOCUS:
                    boolean isFocus = jsonMsg.getBoolean("isFocus");
                    this.SetFocus(isFocus);
                    break;
                case MSG_SET_VISIBLE:
                    boolean isVisible = jsonMsg.getBoolean("isVisible");
                    this.SetVisible(isVisible);
                    break;
                case MSG_ANDROID_KEY_DOWN:
                    String strKey = jsonMsg.getString("key");
                    this.OnForceAndroidKeyDown(strKey);
                    break;
            }

        } catch (JSONException e) {
        }
    }

    private void SendJsonToUnity(JSONObject jsonToUnity) {
        try {
            jsonToUnity.put("senderId", this.tag);
        } catch (JSONException e) {
        }
        NativeEditPlugin.SendUnityMessage(jsonToUnity);
    }

    private void Create(int _tag, JSONObject jsonObj) {
        this.tag = _tag;

        try {
            String placeHolder = jsonObj.getString("placeHolder");

            String font = jsonObj.getString("font");
            characterLimit = jsonObj.getInt("characterLimit");

            int textColor_r = (int) (255.0f * jsonObj.getDouble("textColor_r"));
            int textColor_g = (int) (255.0f * jsonObj.getDouble("textColor_g"));
            int textColor_b = (int) (255.0f * jsonObj.getDouble("textColor_b"));
            int textColor_a = (int) (255.0f * jsonObj.getDouble("textColor_a"));
            int backColor_r = (int) (255.0f * jsonObj.getDouble("backColor_r"));
            int backColor_g = (int) (255.0f * jsonObj.getDouble("backColor_g"));
            int backColor_b = (int) (255.0f * jsonObj.getDouble("backColor_b"));
            int backColor_a = (int) (255.0f * jsonObj.getDouble("backColor_a"));
            int placeHolderColor_r = (int) (255.0f * jsonObj.getDouble("placeHolderColor_r"));
            int placeHolderColor_g = (int) (255.0f * jsonObj.getDouble("placeHolderColor_g"));
            int placeHolderColor_b = (int) (255.0f * jsonObj.getDouble("placeHolderColor_b"));
            int placeHolderColor_a = (int) (255.0f * jsonObj.getDouble("placeHolderColor_a"));

            String contentType = jsonObj.getString("contentType");
            String inputType = jsonObj.optString("inputType");
            String keyboardType = jsonObj.optString("keyboardType");
            String returnKeyType = jsonObj.getString("return_key_type");

            String alignment = jsonObj.getString("align");
            boolean multiline = jsonObj.getBoolean("multiline");

            edit = new EditTextLifeCycle(NativeEditPlugin.unityActivity.getApplicationContext(), this);

            // It's important to set this first as it resets some things, for example character hiding if content type is password.
            edit.setSingleLine(!multiline);

            edit.setId(0);
            edit.setText("");
            edit.setHint(placeHolder);

            this.SetRect(jsonObj);
            edit.setPadding(0, 0, 0, 0);

            int editInputType = 0;
            switch (contentType) {
                case "Standard":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    break; // This is default behaviour
                case "Autocorrected":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                    break;
                case "IntegerNumber":
                    editInputType |= InputType.TYPE_CLASS_NUMBER;
                    break;
                case "DecimalNumber":
                    editInputType |= InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                    break;
                case "Alphanumeric":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    break; // This is default behaviour
                case "Name":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
                    break;
                case "EmailAddress":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                    break;
                case "Password":
                    editInputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
                    break;
                case "Pin":
                    editInputType |= InputType.TYPE_CLASS_PHONE;
                    break;

                case "Custom": // We need more details
                    switch (keyboardType) {
                        case "ASCIICapable":
                            editInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                            break;
                        case "NumbersAndPunctuation":
                            editInputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
                            break;
                        case "URL":
                            editInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI;
                            break;
                        case "NumberPad":
                            editInputType = InputType.TYPE_CLASS_NUMBER;
                            break;
                        case "PhonePad":
                            editInputType = InputType.TYPE_CLASS_PHONE;
                            break;
                        case "NamePhonePad":
                            editInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
                            break;
                        case "EmailAddress":
                            editInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                            break;
                        default:
                            editInputType = InputType.TYPE_CLASS_TEXT;
                    }
                    switch (inputType) {
                        case "AutoCorrect":
                            editInputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                            break;
                        case "Password":
                            editInputType |= InputType.TYPE_NUMBER_VARIATION_PASSWORD | InputType.TYPE_TEXT_VARIATION_PASSWORD;
                            break;
                    }
                    break;

                default:
                    editInputType |= InputType.TYPE_CLASS_TEXT;
                    break; // No action

            }
            if (multiline)
                editInputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            edit.setInputType(editInputType);

            int gravity = 0;
            switch (alignment) {
                case "UpperLeft":
                    gravity = Gravity.TOP | Gravity.START;
                    break;
                case "UpperCenter":
                    gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    break;
                case "UpperRight":
                    gravity = Gravity.TOP | Gravity.END;
                    break;
                case "MiddleLeft":
                    gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                    break;
                case "MiddleCenter":
                    gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                    break;
                case "MiddleRight":
                    gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                    break;
                case "LowerLeft":
                    gravity = Gravity.BOTTOM | Gravity.START;
                    break;
                case "LowerCenter":
                    gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    break;
                case "LowerRight":
                    gravity = Gravity.BOTTOM | Gravity.END;
                    break;
            }
            edit.setGravity(gravity);

            int imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            if (!multiline)
                switch (returnKeyType) {
                    case "Next":
                        imeOptions |= EditorInfo.IME_ACTION_NEXT;
                        break;
                    case "Done":
                        imeOptions |= EditorInfo.IME_ACTION_DONE;
                        break;
                    case "Send":
                        imeOptions |= EditorInfo.IME_ACTION_SEND;
                        break;
                    case "Go":
                        imeOptions |= EditorInfo.IME_ACTION_GO;
                        break;
                }
            edit.setImeOptions(imeOptions);

            this.SetTextSize(jsonObj);
            edit.setTextColor(Color.argb(textColor_a, textColor_r, textColor_g, textColor_b));
            edit.setBackgroundColor(Color.argb(backColor_a, backColor_r, backColor_g, backColor_b));
            edit.setHintTextColor(Color.argb(placeHolderColor_a, placeHolderColor_r, placeHolderColor_g, placeHolderColor_b));

            if (font != null && !font.isEmpty()) {
                Typeface tf = Typeface.create(font, Typeface.NORMAL);
                edit.setTypeface(tf);
            }

            final EditBox eb = this;

            edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {

                    JSONObject msgTextEndJSON = new JSONObject();
                    try {
                        msgTextEndJSON.put("msg", hasFocus ? MSG_TEXT_BEGIN_EDIT : MSG_TEXT_END_EDIT);
                        msgTextEndJSON.put("text", eb.GetText());
                    } catch (JSONException e) {
                    }
                    eb.SendJsonToUnity(msgTextEndJSON);
                    SetFocus(hasFocus);
                }
            });

            edit.addTextChangedListener(new TextWatcher() {

                public void afterTextChanged(Editable s) {
                    if (characterLimit > 0 && s.length() >= characterLimit + 1) {
                        s.delete(s.length() - 1, s.length());
                        edit.removeTextChangedListener(this);
                        edit.setText(s);
                        edit.setSelection(s.length());
                        edit.addTextChangedListener(this);
                    }

                    JSONObject jsonToUnity = new JSONObject();
                    try {
                        jsonToUnity.put("msg", MSG_TEXT_CHANGE);
                        jsonToUnity.put("text", s.toString());
                    } catch (JSONException e) {
                    }
                    eb.SendJsonToUnity(jsonToUnity);
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }
            });

            edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_NEXT
                            || actionId == EditorInfo.IME_ACTION_DONE
                            || actionId == EditorInfo.IME_ACTION_SEND
                            || actionId == EditorInfo.IME_ACTION_GO) {
                        JSONObject jsonToUnity = new JSONObject();
                        try {
                            jsonToUnity.put("msg", MSG_RETURN_PRESSED);
                        } catch (JSONException e) {
                        }

                        eb.SendJsonToUnity(jsonToUnity);
                        return true;
                    }
                    return false;
                }
            });

            layout.addView(edit);

        } catch (JSONException e) {
            Log.i(NativeEditPlugin.LOG_TAG, String.format("Create editbox error %s", e.getMessage()));
        }
    }

    private void Remove() {
        if (edit != null) {
            layout.removeView(edit);
            editBoxMap.remove(this.tag);
        }
        edit = null;
    }

    private void SetText(String newText) {
        if (edit != null) {
            int cursorPos = edit.getSelectionStart();
            int previousLength = edit.getText().length();

            edit.setText(newText);

            // Update text selection (cursor position) to be the same after editing text
            // If the user had multiple characters selected, we are losing them and get the cursor in only one position
            if (previousLength == cursorPos) {
                //The cursor was at the end of the text, let us put it again at the end of the text in case more characters were added
                cursorPos = newText.length();
            }

            if (cursorPos > newText.length()) {
                //Text was deleted, so cursor position is after the end of the text, let's put it at the last position
                cursorPos = newText.length();
            }
            edit.setSelection(cursorPos);
        }
    }

    private String GetText() {
        return edit.getText().toString();
    }

    private boolean isFocused() {
        return edit.isFocused();
    }

    private void SetFocus(boolean isFocus) {
        if (isFocus) {
            edit.requestFocus();
        } else {
            edit.clearFocus();
        }
        this.showKeyboard(isFocus);
    }

    private void SetTextSize(JSONObject jsonRect) {
        try {
            double fontSize = jsonRect.getDouble("fontSize");
            edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) fontSize);
        } catch (JSONException e) {
        }
    }

    private void SetRect(JSONObject jsonRect) {
        try {
            double x = jsonRect.getDouble("x") * layout.getWidth();
            double y = jsonRect.getDouble("y") * layout.getHeight();
            double width = jsonRect.getDouble("width") * layout.getWidth();
            double height = jsonRect.getDouble("height") * layout.getHeight();

            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams((int) width, (int) height);
            lp.setMargins((int) x, (int) y, 0, 0);
            edit.setLayoutParams(lp);
        } catch (JSONException e) {
        }
    }

    private void SetVisible(boolean bVisible) {
        edit.setEnabled(bVisible);
        edit.setVisibility(bVisible ? View.VISIBLE : View.INVISIBLE);
    }

    private void OnForceAndroidKeyDown(String strKey) {
        if (!this.isFocused()) return;

        // Need to force fire key event of backspace and enter because Unity eats them and never return back to plugin.
        // Same happens on number keys on top of the keyboard with Google Keyboard on password fields.
        int keyCode = -1;
        if (strKey.equalsIgnoreCase("backspace")) {
            keyCode = KeyEvent.KEYCODE_DEL;
        } else if (strKey.equalsIgnoreCase("enter")) {
            keyCode = KeyEvent.KEYCODE_ENTER;
        } else if (strKey.equals("0")) {
            keyCode = KeyEvent.KEYCODE_0;
        } else if (strKey.equals("1")) {
            keyCode = KeyEvent.KEYCODE_1;
        } else if (strKey.equals("2")) {
            keyCode = KeyEvent.KEYCODE_2;
        } else if (strKey.equals("3")) {
            keyCode = KeyEvent.KEYCODE_3;
        } else if (strKey.equals("4")) {
            keyCode = KeyEvent.KEYCODE_4;
        } else if (strKey.equals("5")) {
            keyCode = KeyEvent.KEYCODE_5;
        } else if (strKey.equals("6")) {
            keyCode = KeyEvent.KEYCODE_6;
        } else if (strKey.equals("7")) {
            keyCode = KeyEvent.KEYCODE_7;
        } else if (strKey.equals("8")) {
            keyCode = KeyEvent.KEYCODE_8;
        } else if (strKey.equals("9")) {
            keyCode = KeyEvent.KEYCODE_9;
        }
        if (keyCode > 0) {
            KeyEvent ke = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            Log.i(NativeEditPlugin.LOG_TAG, String.format("Force fire KEY EVENT %d", keyCode));
            edit.onKeyDown(keyCode, ke);
        }
    }
}
