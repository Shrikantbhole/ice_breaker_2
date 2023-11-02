package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class CamaraIntentActivity extends Activity  implements SensorEventListener{

    private static  final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private static final int ACTIVITY_START_CAMERA_APP = 0;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mState;

    private ImageView mPhotoCapturedImageView;
    private String mImageFileLocation = "";
    private String GALLERY_LOCATION = "image gallery";
    private File mGalleryFolder;

    private static LruCache<String, Bitmap> mMemoryCache;
    private RecyclerView mRecyclerView;

    private Size mPreviewSize;

    private String mCameraId;

    private TextureView mTextureView;

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceCallback
            = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();

            //Toast.makeText(getApplicationContext(), "Camera Opened!", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;

        }
    };

    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                    setupCamera(i, i1);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

                }
            };


    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;

    private CameraCaptureSession mCameraCaptureSession;



    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result){
            switch(mState){
                case STATE_PREVIEW:
                    // Do nothing
                    break;

                case STATE_WAIT_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        //restartFocus();
                        captureStillImage();
                    }
                    if(afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED){
                        /*
                        unLockFocus();
                        Toast.makeText(getApplicationContext(), "Focus Locked Successful", Toast.LENGTH_SHORT).show();

                       */
                        captureStillImage();
                    }
                    break;
            }

        }
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Toast.makeText(getApplicationContext(), "Focus Locked Unsuccessful", Toast.LENGTH_SHORT).show();

        }
    };


    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private static File mImageFile;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    mBackgroundHandler.post(new ImageSaver(imageReader.acquireNextImage()));
                }
            };

    private static class ImageSaver implements Runnable{

        private  final Image mImage;
        private  ImageSaver (Image image){
            mImage = image;
        }
        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            FileOutputStream fileOutputStream = null;
            try{
                fileOutputStream = new FileOutputStream(mImageFile);
                fileOutputStream.write(bytes);
            }catch(IOException e){
                e.printStackTrace();
            }finally{
                mImage.close();
                if(fileOutputStream != null){
                    try{
                        fileOutputStream.close();
                    }catch(IOException e){
                        e.printStackTrace();
                    }

                }
            }


        }
    }

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private TextView inclinationTextView;
    private DecimalFormat decimalFormat;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_intent);

        createImageGallery();

        mRecyclerView = (RecyclerView) findViewById(R.id.galleryRecyclerView);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mRecyclerView.setLayoutManager(layoutManager);
        RecyclerView.Adapter imageAdapter = new ImageAdapter(mGalleryFolder);
        mRecyclerView.setAdapter(imageAdapter);

        final int maxMemorySize = (int) Runtime.getRuntime().maxMemory() / 1024;
        final int cacheSize = maxMemorySize / 10;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;

            }
        };
        mTextureView = (TextureView) findViewById(R.id.textureView);
        // Initialize the SensorManager and accelerometer
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // Find the TextView by its ID
        inclinationTextView = findViewById(R.id.inclinationTextView);
        // Initialize the decimal format with two decimal places
        decimalFormat = new DecimalFormat("#.##");

    }

    @Override
    protected void onPause() {
        closeCamera();
        closeBackgroundThread();
        sensorManager.unregisterListener((SensorListener) this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the accelerometer sensor listener
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        openBackgroundThread();
        if (mTextureView.isAvailable()) {
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
                openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float[] gravity = event.values;

            // Calculate the angle of inclination with respect to the ground
            double inclination = Math.atan2(gravity[1], gravity[2]);

            // Convert to degrees
            inclination = Math.toDegrees(inclination);
            String roundedAngle = decimalFormat.format(inclination);
            // Update the TextView with the inclination angle
            inclinationTextView.setText("Inclination Angle: " + roundedAngle);
            // Round the inclination angle to two decimal places


            // Update your UI or perform other actions with the inclination angle
            // For example, you can display it in a TextView
            // textView.setText("Inclination Angle: " + inclination);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_camara_intent, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void takePhoto(View view) {
        /*
        Intent callCameraApplicationIntent = new Intent();
        callCameraApplicationIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = null;
        try {
            photoFile = createImageFile();

        } catch (IOException e) {
            e.printStackTrace();
        }
        callCameraApplicationIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));

        startActivityForResult(callCameraApplicationIntent, ACTIVITY_START_CAMERA_APP);

         */

        try {
            mImageFile = createImageFile();

        } catch (IOException e) {
            e.printStackTrace();
        }

        lockFocus();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) {
            // Toast.makeText(this, "Picture taken successfully", Toast.LENGTH_SHORT).show();
            // Bundle extras = data.getExtras();
            // Bitmap photoCapturedBitmap = (Bitmap) extras.get("data");
            // mPhotoCapturedImageView.setImageBitmap(photoCapturedBitmap);
            // Bitmap photoCapturedBitmap = BitmapFactory.decodeFile(mImageFileLocation);
            // mPhotoCapturedImageView.setImageBitmap(photoCapturedBitmap);
            // setReducedImageSize();
            RecyclerView.Adapter newImageAdapter = new ImageAdapter(mGalleryFolder);
            mRecyclerView.swapAdapter(newImageAdapter, false);

        }
    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(storageDirectory, GALLERY_LOCATION);
        if (!mGalleryFolder.exists()) {
            mGalleryFolder.mkdirs();
        }

    }

    File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";

        File image = File.createTempFile(imageFileName, ".jpg", mGalleryFolder);
        mImageFileLocation = image.getAbsolutePath();

        return image;

    }

    void setReducedImageSize() {
        int targetImageViewWidth = mPhotoCapturedImageView.getWidth();
        int targetImageViewHeight = mPhotoCapturedImageView.getHeight();

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mImageFileLocation, bmOptions);
        int cameraImageWidth = bmOptions.outWidth;
        int cameraImageHeight = bmOptions.outHeight;

        int scaleFactor = Math.min(cameraImageWidth / targetImageViewWidth, cameraImageHeight / targetImageViewHeight);
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inJustDecodeBounds = false;

        Bitmap photoReducedSizeBitmp = BitmapFactory.decodeFile(mImageFileLocation, bmOptions);
        mPhotoCapturedImageView.setImageBitmap(photoReducedSizeBitmp);


    }

    private File[] sortFilesToLatest(File fileImagesDir) {
        File[] files = fileImagesDir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File file, File t1) {
                return Long.valueOf(t1.lastModified()).compareTo(file.lastModified());
            }
        });
        return files;
    }

    public static void setBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public static Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size largestImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size size, Size t1) {
                                return Long.signum(size.getWidth()*size.getHeight() - t1.getWidth()*t1.getHeight());
                            }
                        }
                );
                mImageReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraId;
                return;


            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSizes.add(option);
                } else {
                    if (option.getWidth() > height && option.getHeight() > width) {
                        collectorSizes.add(option);
                    }
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum(size.getWidth() * size.getHeight() - t1.getWidth() * t1.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(mCameraId, mCameraDeviceCallback, mBackgroundHandler);
        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    private void closeCamera(){
        if(mCameraCaptureSession != null){
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mImageReader != null){
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void createCameraPreviewSession(){
        try{
                SurfaceTexture surfaceTexture= mTextureView.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                Surface previewSurface = new Surface(surfaceTexture);
                mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest((CameraDevice.TEMPLATE_PREVIEW));
                mPreviewCaptureRequestBuilder.addTarget(previewSurface);
                mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        if(mCameraDevice == null){
                            return;
                        }
                        try{
                            mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                            mCameraCaptureSession = cameraCaptureSession;
                            mCameraCaptureSession.setRepeatingRequest(
                                    mPreviewCaptureRequest,
                                    mSessionCaptureCallback,
                                    mBackgroundHandler
                            );

                        } catch (CameraAccessException ex) {
                           ex.printStackTrace();
                        }

                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(getApplicationContext(), "create camera session failed", Toast.LENGTH_SHORT);
                    }
                }, null);
        }catch(CameraAccessException ex){
            ex.printStackTrace();
        }
    }

    private void openBackgroundThread(){
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void closeBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;

        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }

    private void lockFocus() {
        try {
            mState = STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void restartFocus(){
        try {
            mPreviewCaptureRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(
                    mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
            mPreviewCaptureRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void unLockFocus(){
        try {
            mState = STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void captureStillImage(){
        try{
            CaptureRequest.Builder captureStillRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillRequestBuilder.addTarget(mImageReader.getSurface());
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureStillRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(getApplicationContext(), "Image  Captured! ", Toast.LENGTH_SHORT).show();
                    unLockFocus();
                }
            };
            mCameraCaptureSession.capture(
                    captureStillRequestBuilder.build(), captureCallback, null
            );
        }catch(CameraAccessException e){
            e.printStackTrace();
        }

    }
}
