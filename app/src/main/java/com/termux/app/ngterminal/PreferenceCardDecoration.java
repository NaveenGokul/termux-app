package com.termux.app.ngterminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

/**
 * NGTerminal: Pixel (Android 16) settings look for any PreferenceFragmentCompat list. Consecutive rows between
 * PreferenceCategory items form one rounded card: outer corners 24dp, inner corners 4dp, 2dp gaps between rows,
 * 16dp side margins. Draws behind the rows only; preference definitions are untouched.
 */
public final class PreferenceCardDecoration extends RecyclerView.ItemDecoration {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mOuter, mInner;
    private final int mSide, mGap;
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();

    public PreferenceCardDecoration(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        mOuter = 24 * d; mInner = 4 * d; mSide = Math.round(16 * d); mGap = Math.round(2 * d);
        mPaint.setColor(ContextCompat.getColor(context, R.color.ng_surface_container));
    }

    private static Preference item(RecyclerView parent, int pos) {
        RecyclerView.Adapter<?> a = parent.getAdapter();
        if (!(a instanceof PreferenceGroupAdapter) || pos < 0 || pos >= a.getItemCount()) return null;
        return ((PreferenceGroupAdapter) a).getItem(pos);
    }

    private static boolean isRow(Preference p) {
        return p != null && !(p instanceof PreferenceCategory);
    }

    @Override
    public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (!isRow(item(parent, pos))) return;
        out.left = mSide; out.right = mSide;
        out.bottom = isRow(item(parent, pos + 1)) ? mGap : 0;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(v);
            if (!isRow(item(parent, pos))) continue;
            float top = isRow(item(parent, pos - 1)) ? mInner : mOuter;
            float bottom = isRow(item(parent, pos + 1)) ? mInner : mOuter;
            mRect.set(v.getLeft(), v.getTop() + v.getTranslationY(), v.getRight(), v.getBottom() + v.getTranslationY());
            mPath.reset();
            mPath.addRoundRect(mRect, new float[]{top, top, top, top, bottom, bottom, bottom, bottom}, Path.Direction.CW);
            c.drawPath(mPath, mPaint);
        }
    }
}
