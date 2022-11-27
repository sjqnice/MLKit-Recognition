package com.sjqnice.mlkit.activity

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_live_preview.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.sjqnice.mlkit.mlkit.*
import com.sjqnice.mlkit.mlkit.barcodescanner.WxGraphic
import com.sjqnice.mlkit.mlkit.textdetector.FitSizeTextGraphic
import com.sjqnice.mlkit.R
import com.sjqnice.mlkit.mlkit.MLKit
import com.sjqnice.mlkit.mlkit.ViewfinderView
import com.sjqnice.mlkit.permission.PermissionHelper
import com.zhihu.matisse.Matisse
import com.zhihu.matisse.MimeType
import com.zhihu.matisse.engine.impl.GlideEngine
import com.zhihu.matisse.internal.entity.CaptureStrategy

class LivePreviewActivity : AppCompatActivity() {
    private lateinit var mlKit: MLKit
    private lateinit var finderView: ViewfinderView
    private lateinit var btnScan: TextView
    private lateinit var btnTextRecognize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_preview)
        initView()
    }

    private fun initView() {
        btn_back.setOnClickListener {
            finishFinal()
        }
        val preview = findViewById<CameraSourcePreview>(R.id.preview_view)
        val graphicOverlay = findViewById<GraphicOverlay>(R.id.graphic_overlay)
        finderView = findViewById(R.id.finder_view)
        btnScan = findViewById(R.id.btn_scan)
        btnTextRecognize = findViewById(R.id.btn_text_recognize)
        btnScan.isSelected = true
        btn_local.setOnClickListener {
            PermissionHelper.requestSinglePermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) {
                openPhotoAlbum()
            }
        }
        //构造出扫描管理器
        mlKit = MLKit(this,preview,graphicOverlay)
        //是否扫描成功后播放提示音和震动
        mlKit.setPlayBeepAndVibrate(true,false)
        //仅识别条形码
//        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODABAR).build()
        mlKit.setBarcodeFormats(null)
        mlKit.setOnScanListener(object : MLKit.OnScanListener{
            override fun onSuccess(
                barcodes: MutableList<Barcode>?,
                graphicOverlay: GraphicOverlay,
                image: InputImage?
            ) {
                showScanResult(barcodes,graphicOverlay,image)
            }

            override fun onFail(code: Int, e: Exception?) {}
        })
        mlKit.setOnRecognizeListener(object : MLKit.OnRecognizeListener{
            override fun onSuccess(
                text: Text?,
                graphicOverlay: GraphicOverlay,
                image: InputImage?
            ) {
                showRecognizeResult(text,graphicOverlay,image)
            }

            override fun onFail(code: Int, e: Exception?) {}
        })
        btn_flash.setOnClickListener {
            mlKit.switchLight()
        }

        btnScan.setOnClickListener {
            btnScan.isSelected = true
            btnTextRecognize.isSelected = false
            mlKit.switchBarcodeScanAndTextRecognize(false)
        }
        btnTextRecognize.setOnClickListener {
            btnScan.isSelected = false
            btnTextRecognize.isSelected = true
            mlKit.switchBarcodeScanAndTextRecognize(true)
        }
    }

    private fun showScanResult(barcodes: MutableList<Barcode>?, graphicOverlay: GraphicOverlay, image: InputImage?){
        if (barcodes.isNullOrEmpty()) return
        val bitmap = addCameraImageGraphic(graphicOverlay,image)
        for (barcode in barcodes){
            val graphic = WxGraphic(graphicOverlay,barcode)
            graphic.setColor(Color.WHITE)
            if (graphic.isDisplayStillImage){
                graphic.setStillImageHeight(bitmap!!.height)
            }
            graphic.setOnClickListener {
                barcode.rawValue?.let {
                    if (it.startsWith("http")){
                        TODO()
                    }else{
                       handleBarcode(it,bitmap,barcode.format.toString())
                    }
                }
            }
            graphicOverlay.add(graphic)
        }
        if (barcodes.size > 0){
            mlKit.stopProcessor()
        }
    }

    private fun showRecognizeResult(text: Text?, graphicOverlay: GraphicOverlay, image: InputImage?){
        if (text?.text.isNullOrEmpty()) return
        val bitmap = addCameraImageGraphic(graphicOverlay,image)
        val graphic = FitSizeTextGraphic(graphicOverlay, text!!)
        if (graphic.isDisplayStillImage){
            graphic.setStillImageHeight(bitmap!!.height)
        }
        graphic.setOnClickListener {
            handleText(it)
        }
        graphicOverlay.add(graphic)
        text.text.isNotEmpty().let {
            mlKit.stopProcessor()
        }
    }

    private fun addCameraImageGraphic(graphicOverlay: GraphicOverlay, image: InputImage?) : Bitmap? {
        val byteBuffer = image?.byteBuffer
        val bitmap = if (byteBuffer != null){
            val builder = FrameMetadata.Builder().setWidth(image.width).setHeight(image.height)
                .setRotation(image.rotationDegrees)
            BitmapUtils.getBitmap(byteBuffer,builder.build())
        }else image?.bitmapInternal
        if (bitmap != null){
            graphicOverlay.add(CameraImageGraphic(graphicOverlay,bitmap))
        }
        return bitmap
    }

    private fun finishFinal(){
        finderView.destroyView()
        finish()
    }

    private fun handleBarcode(code: String, bitmap: Bitmap?, typ: String){
        ToastUtils.showShort(code)
    }

    private fun handleText(text: String){
        ToastUtils.showShort(text)
    }

    private fun openPhotoAlbum(){
        Matisse.from(this)
            .choose(MimeType.ofImage())
            .theme(R.style.Matisse_ModeSens)
            .countable(true)
            .captureStrategy(
                CaptureStrategy(true, "com.sjqnice.mlkit.fileprovider", "MLKit")
            )
            .maxSelectable(1)
            .gridExpectedSize(resources.getDimensionPixelSize(R.dimen.dp_120))
            .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            .thumbnailScale(0.85f)
            .imageEngine(GlideEngine())
            .autoHideToolbarOnSingleTap(true)
            .forResult(REQUEST_CODE_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PHOTO && resultCode == RESULT_OK){
            val uri = Matisse.obtainResult(data)
            mlKit.scanningImage(uri?.get(0))
        }
    }

    companion object{
        private const val REQUEST_CODE_PHOTO = 11111
    }
}