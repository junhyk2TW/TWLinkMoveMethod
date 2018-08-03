package com.example.twinny32_ljh.twlinkmovehelper.listener;

import android.widget.TextView;

public interface OnLinkClickListener {
    /**
     * @param textView The TextView on which a click was registered.
     * @param url      The clicked URL.
     * @param position 어댑터아이템 포지션
     * @return True 끝낸다. False 다른거계속한다..
     */
    boolean onClick(TextView textView, String url, int position);
}