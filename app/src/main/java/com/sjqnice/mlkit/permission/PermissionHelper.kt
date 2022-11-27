package com.sjqnice.mlkit.permission

import android.content.Context
import android.graphics.Color
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PermissionUtils

object PermissionHelper {
    fun requestSinglePermission(context: Context, singlePermission:String, grantBlock:() -> Unit){
        if (!PermissionUtils.isGranted(singlePermission)) {
            PermissionUtils.permission(singlePermission)
                .rationale { _, shouldRequest -> shouldRequest.again(true) }
                .callback(object : PermissionUtils.FullCallback {
                    override fun onGranted(granted: MutableList<String>) {
                        grantBlock.invoke()
                    }

                    override fun onDenied(
                        deniedForever: MutableList<String>,
                        denied: MutableList<String>
                    ) {
                        LogUtils.e(denied)
                        if (deniedForever.isNotEmpty()) {
                            PermissionUtils.launchAppDetailsSettings()
                        }
                    }
                })
                .theme { activity -> activity.window.navigationBarColor = Color.TRANSPARENT }
                .request()
        }else{
            grantBlock.invoke()
        }
    }
}