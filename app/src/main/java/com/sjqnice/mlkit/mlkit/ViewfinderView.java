/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sjqnice.mlkit.mlkit;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import androidx.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.sjqnice.mlkit.R;


/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final String TAG = "ViewfinderView";
    private Paint paint;
    private Paint paintResultPoint;
    private Paint paintText;
    private Paint paintTextBg;
    private Paint paintLine;
    private Paint paintLaser;
    private int maskColor;
    private int laserColor;

    private Rect frame;
    private String hintMsg;
    private int hintTextColor;
    private float hintTextSize;
    private int linePosition = 0;
    private int margin;
    private int laserLineW;
    private int cornerLineH;
    private int cornerLineW;
    private int gridColumn;
    private int gridHeight;

    //扫描线风格：1线，2网格
    private LaserStyle laserStyle;
    //是否需要全屏扫描，默认全屏扫描
    private boolean isFullScreenScan;
    //扫描框宽度大小比例，非全屏模式下生效，默认0.7，范围0.5-0.9
    private float scanFrameSizeScale;

    private ValueAnimator anim;
    private boolean needAnimation = true;

    public ViewfinderView(Context context){this(context,null);}

    public ViewfinderView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public ViewfinderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context,attrs);
    }

    private void init(Context context, AttributeSet attrs){
        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintResultPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLaser = new Paint(Paint.ANTI_ALIAS_FLAG);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ViewfinderView);
        maskColor = array.getColor(R.styleable.ViewfinderView_maskColor,Color.parseColor("#80000000"));
        laserColor = array.getColor(R.styleable.ViewfinderView_laserColor,Color.parseColor("#22CE6B"));
        hintMsg = array.getString(R.styleable.ViewfinderView_scanHintText);
        hintTextColor = array.getColor(R.styleable.ViewfinderView_scanHintTextColor, Color.WHITE);
        hintTextSize = array.getDimension(R.styleable.ViewfinderView_scanHintTextSize,TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,getResources().getDisplayMetrics()));
        laserStyle = LaserStyle.getFromInt(array.getInt(R.styleable.ViewfinderView_laserStyle, LaserStyle.LINE.mValue));
        isFullScreenScan = array.getBoolean(R.styleable.ViewfinderView_isFullScreenScan,true);
        scanFrameSizeScale = array.getFloat(R.styleable.ViewfinderView_scanFrameSizeScale,0.7f);
        gridColumn = array.getInt(R.styleable.ViewfinderView_gridScanColumn,24);
        gridHeight = array.getInt(R.styleable.ViewfinderView_gridScanHeight, getWidth() * 7 / 10);
        array.recycle();
        //文字
        paintText.setColor(hintTextColor);
        paintText.setTextSize(hintTextSize);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintTextBg.setColor(laserColor);
        paintTextBg.setTextAlign(Paint.Align.CENTER);
        //四角
        paintLine.setColor(laserColor);
        //扫描线
        paintLaser.setColor(laserColor);
        paintResultPoint.setColor(laserColor);
        //初始化数据大小
        initSize();
    }

    private void initSize() {
        //间距
        margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,getResources().getDisplayMetrics());
        //扫描线的宽度
        laserLineW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,getResources().getDisplayMetrics());
        //四角线块
        cornerLineH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,getResources().getDisplayMetrics());
        cornerLineW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14,getResources().getDisplayMetrics());
    }

    /**
     * 设置颜色
     *
     * @param laserColor
     */
    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        paintLine.setColor(this.laserColor);
        paintLaser.setColor(this.laserColor);
    }

    public enum LaserStyle{
        NONE(0),LINE(1),GRID(2);
        private int mValue;
        LaserStyle(int value){
            mValue = value;
        }

        private static LaserStyle getFromInt(int value){
            for(LaserStyle style : LaserStyle.values()){
                if(style.mValue == value){
                    return style;
                }
            }
            return LaserStyle.LINE;
        }
    }

    /**
     * 扫描线的样式
     *
     * @param laserStyle
     */
    public void setLaserStyle(LaserStyle laserStyle) {
        this.laserStyle = laserStyle;
    }

    /**
     * 背景色
     *
     * @param maskColor
     */
    public void setMaskColor(int maskColor) {
        this.maskColor = maskColor;
    }

    /**
     * 网格扫描列数
     *
     * @param gridColumn
     */
    public void setGridScannerColumn(int gridColumn) {
        if (gridColumn > 0) {
            this.gridColumn = gridColumn;
        }
    }

    /**
     * 网格扫描高度，默认扫描框的高度
     *
     * @param gridHeight
     */
    public void setGridScannerHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    /**
     * 设置文案
     */
    public void setHintText(String hintMsg) {
        //文字
        if (!TextUtils.isEmpty(hintMsg)) {
            this.hintMsg = hintMsg;
        } else {
            this.hintMsg = "";
        }
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        int txtMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,getResources().getDisplayMetrics());

        int frameWidth = (int) (width * scanFrameSizeScale);
        if (isFullScreenScan) {
            frameWidth = width * 9 / 10;
        }
        int left = (width - frameWidth) / 2;
        int top = (height - frameWidth) / 2;
        frame = new Rect(left, top, left + frameWidth, top + frameWidth);

        //重新赋值
        frame.top = (height - (frame.right - frame.left)) / 2;
        frame.bottom = frame.top + (frame.right - frame.left);
        frame.left = (width - (frame.right - frame.left)) / 2;
        frame.right = frame.left + (frame.right - frame.left);

        paintLine.setShader(null);
        //四角线块
        int rectH = cornerLineW;
        int rectW = cornerLineH;
        //判断是不是全屏模式
        if (isFullScreenScan) {
            //全屏透明
            paint.setColor(Color.TRANSPARENT);
            canvas.drawRect(0, 0, width, height, paint);
            //扫描线的宽度
            laserLineW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,getResources().getDisplayMetrics());
        } else {
            //扫描线的宽度
            laserLineW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,getResources().getDisplayMetrics());
            // 半透明背景
            paint.setColor(maskColor);

            canvas.drawRect(0, 0, width, frame.top, paint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
            canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
            canvas.drawRect(0, frame.bottom + 1, width, height, paint);
            //左上角
            canvas.drawRect(frame.left, frame.top, frame.left + rectW, frame.top + rectH, paintLine);
            canvas.drawRect(frame.left, frame.top, frame.left + rectH, frame.top + rectW, paintLine);
            //右上角
            canvas.drawRect(frame.right - rectW, frame.top, frame.right + 1, frame.top + rectH, paintLine);
            canvas.drawRect(frame.right - rectH, frame.top, frame.right + 1, frame.top + rectW, paintLine);
            //左下角
            canvas.drawRect(frame.left, frame.bottom - rectH, frame.left + rectW, frame.bottom + 1, paintLine);
            canvas.drawRect(frame.left, frame.bottom - rectW, frame.left + rectH, frame.bottom + 1, paintLine);
            //右下角
            canvas.drawRect(frame.right - rectW, frame.bottom - rectH, frame.right + 1, frame.bottom + 1, paintLine);
            canvas.drawRect(frame.right - rectH, frame.bottom - rectW, frame.right + 1, frame.bottom + 1, paintLine);
        }

        //带有背景框的文字，暂时不做
//        float textWidth = CommonUtils.getTextWidth(hintMsg, paintText);
//        float textHeight = CommonUtils.getTextHeight(hintMsg, paintText);
//        float startX = (width - textWidth) / 2 - CommonUtils.dip2px(getContext(), 20);
//        float startY = frame.bottom + txtMargin;
//        float endX = startX + textWidth + CommonUtils.dip2px(getContext(), 40);
//        float endY = startY + textHeight + CommonUtils.dip2px(getContext(), 12);
//        RectF rectF = new RectF(startX, startY, endX, endY);
//        canvas.drawRoundRect(rectF, 100, 100, paintTextBg);
//        if (mnScanConfig.isSupportZoom() && mnScanConfig.isShowZoomController() && mnScanConfig.getZoomControllerLocation() == MNScanConfig.ZoomControllerLocation.Bottom) {
//            canvas.drawText(hintMsg, width / 2, frame.top - txtMargin, paintText);
//        } else {
//            canvas.drawText(hintMsg, width / 2, startY + (rectF.height() - textHeight) + (rectF.height() - textHeight) / 2f, paintText);
//        }
        //文字
        canvas.drawText(hintMsg, width / 2, frame.bottom + txtMargin + getTextHeight(hintMsg, paintText), paintText);

        //中间的线：动画
        if (linePosition <= 0) {
            linePosition = frame.top + margin;
        }
        //扫描线
        if (laserStyle == LaserStyle.LINE) {
            drawLineScanner(canvas, frame);
        } else if (laserStyle == LaserStyle.GRID) {
            drawGridScanner(canvas, frame);
        }
        //动画刷新
        if (laserStyle != LaserStyle.NONE) {
            startAnimation();
        }
    }

    private int getTextHeight(String text, Paint paint){
        Rect rect = new Rect();
        paint.getTextBounds(text, 0, text.length(), rect);
        return rect.height();
    }

    /**
     * 绘制线性式扫描
     *
     * @param canvas
     * @param frame
     */
    private void drawLineScanner(Canvas canvas, Rect frame) {
        //线性渐变
        LinearGradient linearGradient = new LinearGradient(
                frame.left, linePosition,
                frame.left, linePosition + laserLineW,
                shadeColor(laserColor),
                laserColor,
                Shader.TileMode.MIRROR);
        paintLine.setShader(linearGradient);
        RectF rect = new RectF(frame.left + margin, linePosition, frame.right - margin, linePosition + laserLineW);
        canvas.drawOval(rect, paintLaser);
    }

    /**
     * 绘制网格式扫描
     *
     * @param canvas
     * @param frame
     */
    private void drawGridScanner(Canvas canvas, Rect frame) {
        if (gridHeight <= 0) {
            gridHeight = frame.bottom - frame.top;
        }
        int stroke = 2;
        paintLaser.setStrokeWidth(stroke);
        //计算Y轴开始位置
        int startY;
        if (gridHeight > 0 && linePosition - frame.top > gridHeight) {
            startY = linePosition - gridHeight;
        } else {
            startY = frame.top;
        }

        LinearGradient linearGradient = new LinearGradient(frame.left + frame.width() / 2, startY, frame.left + frame.width() / 2, linePosition, new int[]{shadeColor(laserColor), laserColor}, new float[]{0, 1f}, LinearGradient.TileMode.CLAMP);
        //给画笔设置着色器
        paintLaser.setShader(linearGradient);

        float wUnit = frame.width() * 1.0f / gridColumn;
        float hUnit = wUnit;
        //遍历绘制网格纵线
        for (int i = 0; i <= gridColumn; i++) {
            float startX;
            float stopX;
            if (i == 0) {
                startX = frame.left + 1;
            } else if (i == gridColumn) {
                startX = frame.left + i * wUnit - 1;
            } else {
                startX = frame.left + i * wUnit;
            }
            stopX = startX;
            canvas.drawLine(startX, startY, stopX, linePosition, paintLaser);
        }
        int height = gridHeight > 0 && linePosition - frame.top > gridHeight ? gridHeight : linePosition - frame.top;
        //遍历绘制网格横线
        for (int i = 0; i <= height / hUnit; i++) {
            canvas.drawLine(frame.left, linePosition - i * hUnit, frame.right, linePosition - i * hUnit, paintLaser);
        }
    }

    /**
     * 处理颜色模糊
     *
     * @param color
     * @return
     */
    public int shadeColor(int color) {
        String hax = Integer.toHexString(color);
        String result = "01" + hax.substring(2);
        return Integer.valueOf(result, 16);
    }

    public void startAnimation() {
        if (anim != null) {
            return;
        }
        anim = ValueAnimator.ofInt(frame.top - 2, frame.bottom + 2);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setDuration(2400);
        anim.addUpdateListener(animation -> {
            if (!needAnimation) {
                return;
            }
            linePosition = (int) animation.getAnimatedValue();
            try {
                postInvalidate(
                        frame.left - 2,
                        frame.top - 2,
                        frame.right + 2,
                        frame.bottom + 2);
            } catch (Exception e) {
                postInvalidate();
            }
        });
        anim.start();
    }

    public void destroyView() {
        if (anim != null) {
            anim.removeAllUpdateListeners();
            anim.cancel();
            anim.end();
            anim = null;
        }
    }

}
