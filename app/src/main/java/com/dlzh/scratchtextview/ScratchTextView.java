package com.dlzh.scratchtextview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Created by Dlzh on 2015-07-22.
 */
public class ScratchTextView extends AppCompatTextView {
    private static final String TAG = "ScratchTextView";
    private Bitmap mBitmap;// 盖在字上面的图片
    private Canvas mCanvas; //画线的画布
    private Paint mPaint;//划线的画笔
    private Path mPath;//线
    private float mX, mY;
    private float TOUCH_TOLERANCE;
    private boolean isInited = false;//用于判断时候覆盖了textview的文字

    //是否完成
    private boolean mIsCompleted;
    private OnCompleteListener onCompleteListener;

    public ScratchTextView(Context context) {
        super(context);
    }

    public ScratchTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScratchTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    //onDraw初始化的时候调用一次，然invalidate()的时候调用，
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInited) {
            //刮扫完成回调
            if (mIsCompleted) {
                if (null != onCompleteListener) {
                    onCompleteListener.complete();
                }
            }
            //判断是否完成，如果完成了就不绘制遮盖层
            if (!mIsCompleted) {
                mCanvas.drawPath(mPath, mPaint);//把线画到mCanvas上,mCanva会把线画到mBitmap
                canvas.drawBitmap(mBitmap, 0, 0, null);// 把mBitmap画到textview上 canvas是父textvie传过来的。
            }
        }
    }

    public void setOnCompleteListener(OnCompleteListener onCompleteListener) {
        this.onCompleteListener = onCompleteListener;
    }

    interface OnCompleteListener {
        void complete();
    }

    /**
     * 初始化刮刮卡
     *
     * @param bgColor          刮刮卡背景色，用于盖住下面的字
     * @param paintStrokeWidth 擦除线宽
     * @param touchTolerance   画线容差
     */
    public void initScratchCard(final int bgColor, final int paintStrokeWidth, float touchTolerance) {
        TOUCH_TOLERANCE = touchTolerance;
        mPaint = new Paint();//创建画笔
        mPaint.setAlpha(240);
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));//
        mPaint.setAntiAlias(true);// 抗锯齿
        mPaint.setDither(true);// 防抖动
        mPaint.setStyle(Paint.Style.STROKE);// 画笔类型： STROKE空心 FILL实心 FILL_AND_STROKE用契形填充
        mPaint.setStrokeJoin(Paint.Join.ROUND);// 画笔接洽点类型
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 画笔笔刷类型
        mPaint.setStrokeWidth(paintStrokeWidth);// 画笔笔刷宽度

        mPath = new Path();

        mBitmap = Bitmap.createBitmap(getLayoutParams().width, getLayoutParams().height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);//通过bitmap生成一个画布
        Paint paint = new Paint();//用于绘制生成的背景图片的字体
        paint.setTextSize(50);
        paint.setColor(Color.parseColor("#A79F9F"));
        mCanvas.drawColor(bgColor);
        mCanvas.drawText("刮开此图层", getLayoutParams().width / 4, getLayoutParams().height / 2 + 15, paint);
        isInited = true;

    }

    //该触膜事件可以定义在调用的activity中实现
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isInited) {
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPath.reset();        // 重置绘制路线，即隐藏之前绘制的轨迹
                mPath.moveTo(event.getX(), event.getY());        // mPath绘制的绘制起点
                mX = event.getX();
                mY = event.getY();
                invalidate();//更新界面
                //                Log.d(TAG, mX + "|" + mY);
                break;
            case MotionEvent.ACTION_MOVE:
                //x和y移动的距离
                float dx = Math.abs(event.getX() - mX);
                float dy = Math.abs(event.getY() - mY);
                //x,y移动的距离大于画线容差
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    // 二次贝塞尔，实现平滑曲线；mX, mY为操作点，(x + mX) / 2, (y + mY) / 2为终点
                    mPath.quadTo(mX, mY, (event.getX() + mX) / 2, (event.getY() + mY) / 2);
                    // 第二次执行时，第一次结束调用的坐标值将作为第二次调用的初始坐标值
                    mX = event.getX();
                    mY = event.getY();
                    //                    Log.d(TAG, mX + "|" + mY);

                    invalidate();
                    break;
                }
            case MotionEvent.ACTION_UP:
                //优化需要用线程池
                //                new Thread(mRunnable).start();
                ExecutorManager.getInstance().diskIO().execute(mRunnable);
                break;
        }
        return true;
    }


    /**
     * 起一个线程来计算已经扫的面积及占总区域的比例
     * 根据区域来判断是否完成
     */
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            int w = getWidth();
            int h = getHeight();

            float wipeArea = 0;
            float totalArea = w * h;

            Bitmap bitmap = mBitmap;
            //            Log.d(TAG, "bitmap==" + bitmap);

            int[] mPixels = new int[w * h];
            //获取bitmap的所有像素信息
            bitmap.getPixels(mPixels, 0, w, 0, 0, w, h);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int index = i + j * w;
                    //                    Log.d(TAG, "mPixels[index]=" + mPixels[index]);
                    if (mPixels[index] >= 0) {
                        wipeArea++;
                    }
                }
            }
            //            Log.d(TAG, "wipeArea=" + wipeArea);
            //            Log.d(TAG, "totalArea=" + totalArea);

            //计算已扫区域所占的比例
            if (wipeArea > 0 && totalArea > 0) {
                int percent = (int) (wipeArea * 100 / totalArea);
                Log.d(TAG, "percent=" + percent);

                if (percent > 60) {
                    //清除图层区域
                    mIsCompleted = true;
                    postInvalidate();

                }
            }
        }

    };
}
