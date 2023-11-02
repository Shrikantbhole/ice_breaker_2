package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class TShirtOutlineView extends View {

    private Paint paint;
    private Path tShirtPath;
    public TShirtOutlineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE); // Set the color of the T-shirt border
        paint.setStyle(Paint.Style.STROKE); // Set the style to stroke (border)
        paint.setStrokeWidth(5); // Set the stroke width (border thickness)

        tShirtPath = new Path();
        // Define the path for the T-shirt shape (vertices and segments)
        tShirtPath.moveTo(200, 900); // left-sleeves-outer
        tShirtPath.lineTo(300, 1000); // left-sleeves-inner
        tShirtPath.lineTo(400, 900); // left-chest
        tShirtPath.lineTo(380, 1500); // left-waist
        tShirtPath.lineTo(720, 1500); // right-waist
        tShirtPath.lineTo(700, 900); // right-chest
        tShirtPath.lineTo(800, 1000); // right-sleeves-inner
        tShirtPath.lineTo(900, 900); // right-sleeves-outer
        tShirtPath.lineTo(780, 730); // right-shoulder
        tShirtPath.lineTo(660, 670); //right-neck-upper
        tShirtPath.lineTo(610, 720); // right-neck-middle
        tShirtPath.lineTo(560, 740); // right-neck-lower
        tShirtPath.lineTo(540, 740); // left-neck-lower
        tShirtPath.lineTo(490, 720); // left-neck-middle
        tShirtPath.lineTo(440, 670); // left-neck-upper
        tShirtPath.lineTo(320, 730); // left-shoulder
        tShirtPath.close();





    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the T-shirt shape outline (border)
        canvas.drawPath(tShirtPath, paint);
    }
}
