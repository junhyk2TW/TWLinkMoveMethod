package com.example.twinny32_ljh.twlinkmovehelper;

import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.twinny32_ljh.twlinkmovehelper.listener.OnLinkClickListener;
import com.example.twinny32_ljh.twlinkmovehelper.listener.OnLinkLongClickListener;

public class TwLinkMovementMethod extends LinkMovementMethod {

    private static TwLinkMovementMethod singleInstance;
    private static final int LINKFY_NONE = -2;
    private OnLinkClickListener onLinkClickListener;
    private OnLinkLongClickListener onLinkLongClickListener;
    private final RectF touchedLineBounds = new RectF();
    private boolean isUrlHighlighted;
    private ClickableSpan clickableSpanUnderTouchOnActionDown;
    private int activeTextViewHashcode;
    private LongPressTimer ongoingLongPressTimer;
    private boolean wasLongPressRegistered;




    //생성자
    public static TwLinkMovementMethod newInstance() {
        return new TwLinkMovementMethod();
    }

    private TwLinkMovementMethod() {
    }

    /**
     * Fragment에서 쓰기위한 링크옵션
     */
    public static TwLinkMovementMethod linkify(int linkifyMask, TextView... textViews) {
        TwLinkMovementMethod movementMethod = newInstance();
        for (TextView textView : textViews) {
            addLinks(linkifyMask, movementMethod, textView);
        }
        return movementMethod;
    }


    /**
     * Fragment에서 쓰기위한 링크옵션
     */
    public static TwLinkMovementMethod linkify(int linkifyMask, ViewGroup viewGroup) {
        TwLinkMovementMethod movementMethod = newInstance();
        rAddLinks(linkifyMask, viewGroup, movementMethod);
        return movementMethod;
    }


    /**
     * 클릭 리스너 설정 메소드
     */
    public TwLinkMovementMethod setOnLinkClickListener(OnLinkClickListener clickListener) {
        if (this == singleInstance) {
            throw new UnsupportedOperationException("getInstance ()에 의해 리턴 된 인스턴스에 클릭 리스너를 설정하는 것은 메모리 누출을 피하기 위해 지원되지 않습니다. 대신 newInstance () 또는 linkify () 메소드를 사용하십시오.");
        }

        this.onLinkClickListener = clickListener;
        return this;
    }

    /**
     * 롱클릭 리스너 설정 메소드
     */
    public TwLinkMovementMethod setOnLinkLongClickListener(OnLinkLongClickListener longClickListener) {
        if (this == singleInstance) {
            throw new UnsupportedOperationException("getInstance ()에 의해 리턴 된 인스턴스에서 long-click 리스너를 설정하는 것은 메모리 누출을 피하기 위해 지원되지 않습니다. 대신 newInstance () 또는 linkify () 메서드를 사용하십시오.");
        }

        this.onLinkLongClickListener = longClickListener;
        return this;
    }

    // ======== ======== ======== ======== ======== ======== ======== PUBLIC END ======== ======== ======== ======== ======== ======== ======== //



    private static void rAddLinks(int linkifyMask, ViewGroup viewGroup, TwLinkMovementMethod movementMethod) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);

            if (child instanceof ViewGroup) {
                //TextViews 링크연결.
                rAddLinks(linkifyMask, ((ViewGroup) child), movementMethod);

            } else if (child instanceof TextView) {
                TextView textView = (TextView) child;
                addLinks(linkifyMask, movementMethod, textView);
            }
        }
    }

    private static void addLinks(int linkifyMask, TwLinkMovementMethod movementMethod, TextView textView) {
        textView.setMovementMethod(movementMethod);
        if (linkifyMask != LINKFY_NONE) {
            Linkify.addLinks(textView, linkifyMask);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean onTouchEvent(final TextView widget, Spannable buffer, MotionEvent event) {
        if (activeTextViewHashcode != widget.hashCode()) {
            //......알려진이슈//......
        // Bug workaround: TextView stops calling onTouchEvent() once any URL is highlighted.
        // A hacky solution is to reset any "autoLink" property set in XML. But we also want
        // to do this once per TextView.
        activeTextViewHashcode = widget.hashCode();
            widget.setAutoLinkMask(0);
    }

        final ClickableSpan clickableSpanUnderTouch = findClickableSpanUnderTouch(widget, buffer, event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            clickableSpanUnderTouchOnActionDown = clickableSpanUnderTouch;
        }
        final boolean touchStartedOverAClickableSpan = clickableSpanUnderTouchOnActionDown != null;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (clickableSpanUnderTouch != null) {
                    highlightUrl(widget, clickableSpanUnderTouch, buffer);
                }

                if (touchStartedOverAClickableSpan && onLinkLongClickListener != null) {
                    TwLinkMovementMethod.LongPressTimer.OnTimerReachedListener longClickListener= new LongPressTimer.OnTimerReachedListener() {
                        @Override
                        public void onTimerReached() {
                            wasLongPressRegistered = true;
                            widget.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            removeUrlHighlightColor(widget);
                            dispatchUrlLongClick(widget, clickableSpanUnderTouch);
                        }
                    };
                    startTimerForRegisteringLongClick(widget, longClickListener);
                }
                return touchStartedOverAClickableSpan;

            case MotionEvent.ACTION_UP:
                if (!wasLongPressRegistered && touchStartedOverAClickableSpan && clickableSpanUnderTouch == clickableSpanUnderTouchOnActionDown) {
                    dispatchUrlClick(widget, clickableSpanUnderTouch);
                }
                cleanupOnTouchUp(widget);

                return touchStartedOverAClickableSpan;

            case MotionEvent.ACTION_CANCEL:
                cleanupOnTouchUp(widget);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (clickableSpanUnderTouch != clickableSpanUnderTouchOnActionDown) {
                    removeLongPressCallback(widget);
                }

                if (!wasLongPressRegistered) {
                    if (clickableSpanUnderTouch != null) {
                        highlightUrl(widget, clickableSpanUnderTouch, buffer);
                    } else {
                        removeUrlHighlightColor(widget);
                    }
                }

                return touchStartedOverAClickableSpan;

            default:
                return false;
        }
    }





    private void cleanupOnTouchUp(TextView textView) {
        wasLongPressRegistered = false;
        clickableSpanUnderTouchOnActionDown = null;
        removeUrlHighlightColor(textView);
        removeLongPressCallback(textView);
    }

    /**
     * ClickableSpan 텍스트뷰의 위치를 측정해서 반환해준다.
     */
    private ClickableSpan findClickableSpanUnderTouch(TextView textView, Spannable text, MotionEvent event) {
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();

        // 해당텍스트뷰의 패팅은빼기
        touchX -= textView.getTotalPaddingLeft();
        touchY -= textView.getTotalPaddingTop();


        touchX += textView.getScrollX();
        touchY += textView.getScrollY();

        final Layout layout = textView.getLayout();
        final int touchedLine = layout.getLineForVertical(touchY);
        final int touchOffset = layout.getOffsetForHorizontal(touchedLine, touchX);

        touchedLineBounds.left = layout.getLineLeft(touchedLine);
        touchedLineBounds.top = layout.getLineTop(touchedLine);
        touchedLineBounds.right = layout.getLineWidth(touchedLine) + touchedLineBounds.left;
        touchedLineBounds.bottom = layout.getLineBottom(touchedLine);

        if (touchedLineBounds.contains(touchX, touchY)) {
            // Find a ClickableSpan that lies under the touched area.
            final Object[] spans = text.getSpans(touchOffset, touchOffset, ClickableSpan.class);
            for (final Object span : spans) {
                if (span instanceof ClickableSpan) {
                    return (ClickableSpan) span;
                }
            }
            // No ClickableSpan found under the touched location.
            return null;

        } else {
            // Touch lies outside the line's horizontal bounds where no spans should exist.
            return null;
        }
    }

    /**
     * Adds a background color span at <var>clickableSpan</var>'s location.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    protected void highlightUrl(TextView textView, ClickableSpan clickableSpan, Spannable text) {
        if (isUrlHighlighted) {
            return;
        }
        isUrlHighlighted = true;

        int spanStart = text.getSpanStart(clickableSpan);
        int spanEnd = text.getSpanEnd(clickableSpan);
        BackgroundColorSpan highlightSpan = new BackgroundColorSpan(textView.getHighlightColor());
        text.setSpan(highlightSpan, spanStart, spanEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        textView.setTag(R.id.highlight_background_tag, highlightSpan);

        Selection.setSelection(text, spanStart, spanEnd);
    }

    /**
     * Removes the highlight color under the Url.
     */
    protected void removeUrlHighlightColor(TextView textView) {
        if (!isUrlHighlighted) {
            return;
        }
        isUrlHighlighted = false;

        Spannable text = (Spannable) textView.getText();
        BackgroundColorSpan highlightSpan = (BackgroundColorSpan) textView.getTag(R.id.highlight_background_tag);
        text.removeSpan(highlightSpan);

        Selection.removeSelection(text);
    }

    protected void startTimerForRegisteringLongClick(TextView textView, TwLinkMovementMethod.LongPressTimer.OnTimerReachedListener longClickListener) {
        ongoingLongPressTimer = new LongPressTimer();
        ongoingLongPressTimer.setOnTimerReachedListener(longClickListener);
        textView.postDelayed(ongoingLongPressTimer, ViewConfiguration.getLongPressTimeout());
    }

    /**
     * Remove the long-press detection timer.
     */
    protected void removeLongPressCallback(TextView textView) {
        if (ongoingLongPressTimer != null) {
            textView.removeCallbacks(ongoingLongPressTimer);
            ongoingLongPressTimer = null;
        }
    }

    protected void dispatchUrlClick(TextView textView, ClickableSpan clickableSpan) {
        ClickableSpanWithText clickableSpanWithText = ClickableSpanWithText.ofSpan(textView, clickableSpan);
        boolean handled = onLinkClickListener != null && onLinkClickListener.onClick(textView, clickableSpanWithText.text(),0);

        if (!handled) {
            // Let Android handle this click.
            clickableSpanWithText.span().onClick(textView);
        }
    }

    protected void dispatchUrlLongClick(TextView textView, ClickableSpan clickableSpan) {
        ClickableSpanWithText clickableSpanWithText = ClickableSpanWithText.ofSpan(textView, clickableSpan);
        boolean handled = onLinkLongClickListener != null && onLinkLongClickListener.onLongClick(textView, clickableSpanWithText.text(),0);

        if (!handled) {
            clickableSpanWithText.span().onClick(textView);
        }
    }

    /**
     * 얼마나 눌렸는지를 측정해주는 정적내부클래스
     */
    protected static final class LongPressTimer implements Runnable {
        private OnTimerReachedListener onTimerReachedListener;

        protected interface OnTimerReachedListener {
            void onTimerReached();
        }

        @Override
        public void run() {
            onTimerReachedListener.onTimerReached();
        }

        public void setOnTimerReachedListener(OnTimerReachedListener listener) {
            onTimerReachedListener = listener;
        }
    }

    /**
     * 링크가 달려있는 글들을 잡아주는 정적내부클래스
     */
    protected static class ClickableSpanWithText {
        private ClickableSpan span;
        private String text;

        static ClickableSpanWithText ofSpan(TextView textView, ClickableSpan span) {
            Spanned s = (Spanned) textView.getText();
            String text;
            if (span instanceof URLSpan) {
                text = ((URLSpan) span).getURL();
            } else {
                int start = s.getSpanStart(span);
                int end = s.getSpanEnd(span);
                text = s.subSequence(start, end).toString();
            }
            return new ClickableSpanWithText(span, text);
        }

        ClickableSpanWithText(ClickableSpan span, String text) {
            this.span = span;
            this.text = text;
        }

        ClickableSpan span() {
            return span;
        }

        protected String text() {
            return text;
        }
    }

}
