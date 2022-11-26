package com.sjqnice.mlkit.mlkit;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.blankj.utilcode.util.ScreenUtils;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.sjqnice.mlkit.mlkit.barcodescanner.BarcodeScannerProcessor;
import com.sjqnice.mlkit.mlkit.hardware.BeepManager;
import com.sjqnice.mlkit.mlkit.textdetector.TextRecognitionProcessor;
import java.io.IOException;
import java.util.List;

public class MLKit implements LifecycleObserver {

    private static final String TAG = "MLKit";
    private FragmentActivity activity;
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private boolean isAnalyze = true;//是否分析结果
    private boolean isContinuousScanning = true;//是否连续扫描
    public BarcodeScannerOptions scannerOptions;
    @NonNull
    public TextRecognizerOptionsInterface recognizerOptions = new ChineseTextRecognizerOptions.Builder().build();

    private BeepManager beepManager;
    boolean isOpenLight = false;
    boolean playBeep = true;
    boolean vibrate = true;
    private VisionImageProcessor imageProcessor;
    private boolean isTextRecognize = false;

    public MLKit(FragmentActivity activity, CameraSourcePreview preview, GraphicOverlay graphicOverlay) {
        this.activity = activity;
        this.preview = preview;
        this.graphicOverlay = graphicOverlay;
        activity.getLifecycle().addObserver(this);
        onCreate();
    }

    public void onCreate() {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(beepManager == null) {
            beepManager = new BeepManager(activity);
        }
        createCameraSource();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        Log.d(TAG, "onStart");
        createCameraSource();
        startCameraSource();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        Log.d(TAG, "onStop");
        preview.stop();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    public synchronized void scanningImage(Uri imageUri){
        try {
            if (imageUri == null){
                onScanListener.onFail(2, new Exception("photo url is null!"));
            }
            Bitmap imageBitmap = BitmapUtils.getBitmapFromContentUri(activity.getContentResolver(),imageUri);
            if (imageBitmap == null){
                return;
            }
            graphicOverlay.clear();
            float scaleFactor = ((float) imageBitmap.getWidth()) / ((float) ScreenUtils.getScreenWidth());
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap,Math.round (imageBitmap.getWidth() / scaleFactor),Math.round(imageBitmap.getHeight() /scaleFactor),true);
            /*Camera camera = Camera.open(cameraSource.getCameraFacing());
            Camera.Size previewSize = camera.new Size(resizedBitmap.getWidth(),resizedBitmap.getHeight());
            CameraSource.SizePair sizePair =
                    CameraSource.selectSizePair(camera,resizedBitmap.getWidth(),resizedBitmap.getHeight());
            PreferenceUtils.saveString(activity,R.string.pref_key_rear_camera_preview_size,sizePair.preview.toString());*/
            /*albumImage.setImageBitmap(resizedBitmap);
            graphicOverlay.setImageSourceInfo(resizedBitmap.getWidth(),resizedBitmap.getHeight(),false);
            barcodeScannerProcessor.processBitmap(resizedBitmap, graphicOverlay);*/
            graphicOverlay.setDisplayStillImage(true);
            if (isTextRecognize) {
                recognizeInImage(resizedBitmap, graphicOverlay);
            } else {
                detectInImage(resizedBitmap, graphicOverlay);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void scanningImage(String photoPath) {
        if (TextUtils.isEmpty(photoPath)) {
            onScanListener.onFail(2, new Exception("photo url is null!"));
        }
        Bitmap bitmap = BitmapUtils.decodeBitmapFromPath(photoPath, 600, 600, false);

        detectInImage(bitmap, graphicOverlay);
    }

    private void detectInImage(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        BarcodeScanner barcodeScanner;
        if(scannerOptions != null) {
            barcodeScanner = BarcodeScanning.getClient(scannerOptions);
        }else {
            barcodeScanner = BarcodeScanning.getClient();
        }
        // Or, to specify the formats to recognize:
        // BarcodeScanner scanner = BarcodeScanning.getClient(options);
        // [END get_detector]

        // [START run_detector]
        Task<List<Barcode>> result = barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty()) {
                        Log.v(TAG, "No barcode has been detected");
                    }
                    if(isAnalyze()) {
                        if(onScanListener != null) {
                            if(!barcodes.isEmpty()) {
                                playBeepAndVibrate();
                            }
                            onScanListener.onSuccess(barcodes, graphicOverlay, image);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Barcode detection failed " + e);
                    if(onScanListener != null) {
                        onScanListener.onFail(1, e);
                    }
                });
    }

    private void recognizeInImage(Bitmap bitmap, final GraphicOverlay graphicOverlay){
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer textRecognizer = TextRecognition.getClient(recognizerOptions);
        textRecognizer.process(image)
                .addOnSuccessListener(text -> {
                    if(isAnalyze()) {
                        if (onRecognizeListener != null){
                            if (!text.getText().isEmpty()){
                                playBeepAndVibrate();
                            }
                            onRecognizeListener.onSuccess(text,graphicOverlay,image);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (onRecognizeListener != null){
                        onRecognizeListener.onFail(1, e);
                    }
                });
    }

    public interface OnScanListener extends onResultListener<List<Barcode>>{}

    public interface OnRecognizeListener extends onResultListener<Text>{}

    private interface onResultListener<T>{
        void onSuccess(T result,@NonNull GraphicOverlay graphicOverlay, InputImage image);
        void onFail(int code, Exception e);
    }

    public OnScanListener onScanListener;

    public void setOnScanListener(OnScanListener listener) {
        onScanListener = listener;
    }

    public OnRecognizeListener onRecognizeListener;

    public void setOnRecognizeListener(@NonNull OnRecognizeListener listener){
        onRecognizeListener = listener;
    }

    /**
     * 设置是否分析图像，通过此方法可以动态控制是否分析图像，常用于中断扫码识别。如：连扫时，扫到结果，然后停止分析图像
     *
     * 1. 因为分析图像默认为true，如果想支持连扫，设置setAnalyze(false)即可。
     *
     * 2. 如果只是想拦截扫码结果回调自己处理逻辑，但并不想继续分析图像（即不想连扫），可通过
     * 调用setAnalyze(false)来停止分析图像。
     * @param isAnalyze
     */
    public void setAnalyze(boolean isAnalyze) {
        this.isAnalyze = isAnalyze;
    }

    public boolean isAnalyze() {
        return isAnalyze;
    }

    public void setBarcodeFormats(BarcodeScannerOptions options) {
        this.scannerOptions = options;
    }

    public void switchCamera() {
        int numberOfCameras = Camera.getNumberOfCameras();// 获取摄像头个数
        if (numberOfCameras == 1) {
            return;
        }
        if(cameraSource != null) {
            if(cameraSource.getCameraFacing() == CameraSource.CAMERA_FACING_FRONT) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            }
        }
        preview.stop();
        startCameraSource();
    }

    public void setFacing(int facing) {
        if(cameraSource != null) {
            cameraSource.setFacing(facing);
        }
    }

    public void setPlayBeepAndVibrate(boolean playBeep, boolean vibrate) {
        this.playBeep = playBeep;
        this.vibrate = vibrate;
    }

    public void playBeepAndVibrate() {
        if(beepManager != null) {
            beepManager.playBeepSoundAndVibrate(playBeep, vibrate);
        }
    }

    public boolean hasLight() {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    /**
     * 开关闪关灯
     */
    public void switchLight() {
        if (hasLight()) {
            if (isOpenLight) {
                closeTorch();
            } else {
                openTorch();
            }
            isOpenLight = !isOpenLight;
        }
    }

    public void openTorch() {
        if(cameraSource != null) {
            cameraSource.setTorch(true);
        }
    }

    public void closeTorch() {
        if(cameraSource != null) {
            cameraSource.setTorch(false);
        }
    }

    private void createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(activity, graphicOverlay);
        }

        if (!isTextRecognize){
            imageProcessor = new BarcodeScannerProcessor(activity, this);
        }else {
            imageProcessor = new TextRecognitionProcessor(activity, this);
        }
        cameraSource.setMachineLearningFrameProcessor(imageProcessor);
    }

    public void switchBarcodeScanAndTextRecognize(boolean isTextRecognize){
        this.isTextRecognize = isTextRecognize;
        preview.stop();
        createCameraSource();
        startCameraSource();
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
                cameraSource.setOnCameraListener(camera -> new GestureDetectorUtil(preview, camera));
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    /**
     * 再一次进行扫描识别
     */
    public void startProcessor() {
        imageProcessor = new BarcodeScannerProcessor(activity, this);
        cameraSource.setMachineLearningFrameProcessor(imageProcessor);
    }

    /**
     * 扫描结果后，停止扫描识别
     */
    public void stopProcessor() {
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }
}
