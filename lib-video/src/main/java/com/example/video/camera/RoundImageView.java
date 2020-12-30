package com.example.video.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ImageView;

import com.example.video.R;


/**
 * 圆角图片或圆形图片控件
 *
 * @author lujiarong
 * @date 2019/05/31
 */
public class RoundImageView extends ImageView {

    private static final String TAG = RoundImageView.class.getSimpleName();

    /**
     * 外部边框的宽和颜色
     */
    private static final int DEFAULT_OUTER_BORDER_WIDTH = 0;
    private static final int DEFAULT_OUTER_BORDER_COLOR = Color.TRANSPARENT;
    private int outerWidth = DEFAULT_OUTER_BORDER_WIDTH;
    private int outerColor = DEFAULT_OUTER_BORDER_COLOR;

    private static final int COLORDRAWABLE_DIMENSION = 1;

    /**
     * 显示图片的类型
     */
    private static final int TYPE_CIRCLE = 0;
    private static final int TYPE_ROUND = 1;
    private int showType = TYPE_CIRCLE;

    /**
     * 圆角大小的默认值
     */
    private static final int DEFAULT_CORNER_ANGLE = 6;

    /**
     * 圆角实际大小值
     */
    private int mCornerAngle = 0;

    /**
     * 圆形图片时候半径大小
     */
    private int mCircleRadius = 0;

    /**
     * 绘图画笔paint
     */
    private Paint mBitmapPaint = null;
    private Paint mOuterPaint = null;


    /**
     * 3X3缩小放大矩阵
     */
    private Matrix mMatrix = null;

    /**
     * 渲染图像，为绘制图形着色
     */
    private BitmapShader mBitmapShader = null;

    /**
     * 大小
     */
    private int mCircleViewWidth = 0;
    private RectF mDrawableRectF = null;
    private RectF mOuterRectF = null;

    public RoundImageView(Context context) {
        this(context, null);
        init(null);
    }

    public RoundImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(attrs);
    }

    public RoundImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * 初始化操作
     */
    private void init(AttributeSet attrs) {

        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.RoundImageView);

        showType = ta.getInt(R.styleable.RoundImageView_show_type, TYPE_ROUND);
        mCornerAngle = ta.getDimensionPixelSize(R.styleable.RoundImageView_corner_angle, dp2px());
        outerWidth = ta.getDimensionPixelSize(R.styleable.RoundImageView_outer_border_width, DEFAULT_OUTER_BORDER_WIDTH);
        outerColor = ta.getColor(R.styleable.RoundImageView_outer_border_color, DEFAULT_OUTER_BORDER_COLOR);

        ta.recycle();

        mMatrix = new Matrix();

        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);

        mOuterPaint = new Paint();
        mOuterPaint.setStyle(Paint.Style.STROKE);
        mOuterPaint.setAntiAlias(true);
        mOuterPaint.setColor(outerColor);
        mOuterPaint.setStrokeWidth(outerWidth);

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        /**
         * 测量的时候，如果获取的类型是圆形的，则强制把view的宽高设为相同大小,以小的为标准
         */
        if (showType == TYPE_CIRCLE) {
            mCircleViewWidth = Math.min(getMeasuredWidth(), getMeasuredHeight());
            mCircleRadius = mCircleViewWidth / 2 - outerWidth / 2;
            setMeasuredDimension(mCircleViewWidth, mCircleViewWidth);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        /**
         * 圆角图片的范围
         */
        if (showType == TYPE_ROUND) {
            mOuterRectF = new RectF(0, 0, getWidth(), getHeight());
            mDrawableRectF = new RectF(outerWidth, outerWidth, getWidth() - outerWidth, getHeight() - outerWidth);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            drawable = getBackground();
        }
        if (drawable == null) {
            Log.e(TAG, "[null] drawable is null.");
            return;
        }
        setShader(getBitmapFromDrawable(drawable));
        switch (showType) {
            case TYPE_CIRCLE:
                canvas.drawCircle(getWidth() / 2, getHeight() / 2, mCircleRadius, mBitmapPaint);
                canvas.drawCircle(getWidth() / 2, getHeight() / 2, mCircleRadius, mOuterPaint);
                break;

            case TYPE_ROUND:
                canvas.drawRoundRect(mDrawableRectF, mCornerAngle, mCornerAngle, mBitmapPaint);
                canvas.drawRoundRect(mOuterRectF, mCornerAngle, mCornerAngle, mOuterPaint);
                break;
            default:
                break;
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
    }

    /**
     * 初始化BitmapShader
     */
    private void setShader(Bitmap mBitmap) {

        if (mBitmap == null) {
            Log.i(TAG, "[null] mBitmap is null.");
            return;
        }

        if (mBitmapShader != null) {
            mBitmapShader = null;
        }

        /**
         * 将mBitmap作为着色器，也就是在指定的区域内绘制mBitmap
         */
        mBitmapShader = new BitmapShader(mBitmap, TileMode.CLAMP, TileMode.CLAMP);

        /**
         * 缩放比例
         */
        float scale = 1.0f;
        switch (showType) {
            case TYPE_CIRCLE:
                /**
                 * 拿图片的宽高最小值做缩放比例
                 */
                scale = mCircleViewWidth * 1.0F / Math.min(mBitmap.getWidth(), mBitmap.getHeight());
                break;
            case TYPE_ROUND:
                /**
                 * 如果图片的宽高与view的宽高不匹配，缩放的宽高一定要大于view的宽高才能填充完整view，所以要取较大值
                 */
                scale = Math.max(getWidth() * 1.0f / mBitmap.getWidth(), getHeight() * 1.0f / mBitmap.getHeight());
                break;
            default:
                break;
        }

        /**
         * 变换矩阵设置缩放大小
         */
        mMatrix.setScale(scale, scale);

        /**
         * 设置变换矩阵
         */
        mBitmapShader.setLocalMatrix(mMatrix);

        /**
         * 设置着色器
         */
        mBitmapPaint.setShader(mBitmapShader);
    }

    /**
     * 从drawable中获取bitmap
     */
    private Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        try {
            Bitmap bitmap = null;
            if (drawable instanceof ColorDrawable) {
                bitmap = Bitmap.createBitmap(COLORDRAWABLE_DIMENSION, COLORDRAWABLE_DIMENSION, Bitmap.Config.ARGB_8888);
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据手机获取合适的像素大小
     */
    private int dp2px() {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_CORNER_ANGLE,
                getResources().getDisplayMetrics());
    }
}

