package com.coara.browser2;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;

public final class SecretTouchpadActivity extends TouchpadActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String getPreferenceName() {
        return "SecretBrowserPrefs";
    }

}
