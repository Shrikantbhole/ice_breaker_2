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
        tShirtPath.moveTo(50, 480); // left-shoulder
       // tShirtPath.lineTo(180, 900); // left-chest
        tShirtPath.lineTo(950, 480); // left-waist
        tShirtPath.lineTo(950, 1700); // right-waist
        //tShirtPath.lineTo(820, 900); // right-chest
        //tShirtPath.lineTo(930, 950); // right-sleeves-inner
        //tShirtPath.lineTo(980, 600); // right-sleeves-outer
        tShirtPath.lineTo(50, 1700); // right-shoulder
        tShirtPath.lineTo(50,480);
       // tShirtPath.lineTo(620, 460); //right-neck-upper
        //tShirtPath.lineTo(610, 720); // right-neck-middle
        //tShirtPath.lineTo(560, 740); // right-neck-lower
        //tShirtPath.lineTo(540, 740); // left-neck-lower
        //tShirtPath.lineTo(490, 720); // left-neck-middle
        //tShirtPath.lineTo(380, 460); // left-neck-upper
        tShirtPath.moveTo(500,400);
        tShirtPath.lineTo(500,1780);

        //tShirtPath.close();





    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the T-shirt shape outline (border)
        canvas.drawPath(tShirtPath, paint);
    }
}
