package com.sjqnice.mlkit

import android.Manifest
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sjqnice.mlkit.activity.LivePreviewActivity
import com.sjqnice.mlkit.permission.PermissionHelper
import kotlinx.android.synthetic.main.activity_main.btn_scan

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_scan.setOnClickListener {
            PermissionHelper.requestSinglePermission(this, Manifest.permission.CAMERA){
                startActivity(Intent(this, LivePreviewActivity::class.java))
            }
        }
    }
}