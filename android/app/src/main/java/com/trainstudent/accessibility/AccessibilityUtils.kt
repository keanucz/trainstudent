package com.trainstudent.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * Utility functions for checking accessibility service status
 */
object AccessibilityUtils {
    
    private const val SERVICE_CLASS = "com.trainstudent.accessibility.TrainStudentAccessibilityService"
    
    /**
     * Check if our accessibility service is enabled using Settings.Secure
     * This is the most reliable method as recommended by Android documentation
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, TrainStudentAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Alternative method using AccessibilityManager
     * Checks if our service is in the list of enabled services
     */
    fun isServiceEnabledViaManager(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        return enabledServices.any { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.let { info ->
                info.packageName == context.packageName && 
                info.name == SERVICE_CLASS
            }
        }
    }
    
    /**
     * Check if accessibility services are enabled at all on the device
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isEnabled == true
    }
    
    /**
     * Get the service instance if it's running
     */
    fun getServiceInstance(): TrainStudentAccessibilityService? {
        return TrainStudentAccessibilityService.getInstance()
    }
    
    /**
     * Check if the service singleton is available (service is running)
     */
    fun isServiceRunning(): Boolean {
        return TrainStudentAccessibilityService.isRunning()
    }
}
