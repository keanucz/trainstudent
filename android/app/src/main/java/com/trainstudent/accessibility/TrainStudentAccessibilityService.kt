package com.trainstudent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * TrainStudent Accessibility Service
 * 
 * This service enables automatic interaction with train booking apps to:
 * - Auto-fill promo codes
 * - Click "Apply" buttons
 * - Navigate through booking flows
 * 
 * Target apps:
 * - Northern Railway app
 * - TPE (TransPennine Express) app
 * - Avanti West Coast app
 * - Trainline app
 */
class TrainStudentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TrainStudentA11y"
        
        // Singleton instance for external access
        private var instance: TrainStudentAccessibilityService? = null
        
        // State flows for communication with the app
        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled
        
        private val _currentPackage = MutableStateFlow<String?>(null)
        val currentPackage: StateFlow<String?> = _currentPackage
        
        private val _actionEvents = MutableSharedFlow<ActionEvent>()
        val actionEvents: SharedFlow<ActionEvent> = _actionEvents
        
        // Pending Northern automation (set by UI, executed by service)
        private var pendingNorthernAutomation: NorthernAutomation? = null
        private var northernAutomationReady = false
        
        fun setPendingNorthernAutomation(automation: NorthernAutomation) {
            pendingNorthernAutomation = automation
            northernAutomationReady = true
        }
        
        fun clearPendingNorthernAutomation() {
            pendingNorthernAutomation = null
            northernAutomationReady = false
        }
        
        // Target train app packages
        val TARGET_PACKAGES = setOf(
            "uk.co.nationalrail.nreapp",       // National Rail
            "com.thetrainline",                 // Trainline
            "uk.co.northernrailway.app",       // Northern Railway (old)
            "org.northernrail.android",        // Northern Railway (current)
            "uk.co.tpexpress",                  // TransPennine Express
            "uk.co.avantiwestcoast",           // Avanti West Coast
            "uk.co.lner",                       // LNER
            "com.gwr.gwr"                       // Great Western Railway
        )
        
        // Northern app specific package
        const val NORTHERN_PACKAGE = "org.northernrail.android"
        
        /**
         * Get the singleton instance (null if service not running)
         */
        fun getInstance(): TrainStudentAccessibilityService? = instance
        
        /**
         * Check if the service is currently running
         */
        fun isRunning(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Queue of actions to perform
    private val actionQueue = mutableListOf<AutomationAction>()
    private var isProcessingActions = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceEnabled.value = true
        Log.d(TAG, "TrainStudent Accessibility Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "TrainStudent Accessibility Service connected")
        
        // Service is now ready to receive events
        _isServiceEnabled.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val packageName = event.packageName?.toString() ?: return
        _currentPackage.value = packageName
        
        // Only process events from target train apps
        if (packageName !in TARGET_PACKAGES) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "Window changed in $packageName: ${event.className}")
                onWindowChanged(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content updated - check for promo code fields
                checkForPromoCodeFields(packageName)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                Log.d(TAG, "View focused: ${event.className}")
            }
        }
    }
    
    private fun onWindowChanged(packageName: String, event: AccessibilityEvent) {
        serviceScope.launch {
            _actionEvents.emit(ActionEvent.WindowChanged(packageName, event.className?.toString()))
            
            // Check if this is the Northern app and we have a pending automation
            if (packageName == NORTHERN_PACKAGE && northernAutomationReady) {
                Log.d(TAG, "Northern app detected with pending automation")
                pendingNorthernAutomation?.let { automation ->
                    // Wait a moment for the app to fully load
                    delay(1500)
                    automation.onNorthernAppReady()
                    northernAutomationReady = false
                }
            }
        }
        
        // Check for promo code fields when window changes
        checkForPromoCodeFields(packageName)
    }
    
    private fun checkForPromoCodeFields(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Look for common promo code field identifiers
            val promoFields = findNodesByText(rootNode, listOf(
                "promo", "promo code", "discount", "voucher", "coupon", "offer code"
            ))
            
            if (promoFields.isNotEmpty()) {
                Log.d(TAG, "Found ${promoFields.size} potential promo code fields")
                serviceScope.launch {
                    _actionEvents.emit(ActionEvent.PromoFieldFound(packageName, promoFields.size))
                }
            }
        } finally {
            // Don't recycle rootNode - it's managed by the system
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "TrainStudent Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        serviceScope.cancel()
        Log.d(TAG, "TrainStudent Accessibility Service destroyed")
    }

    // ========== Public API for automation ==========
    
    /**
     * Find and click a button by its text
     */
    fun clickButton(buttonText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        return try {
            val buttons = findClickableByText(rootNode, buttonText)
            if (buttons.isNotEmpty()) {
                val button = buttons.first()
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked button: $buttonText")
                true
            } else {
                Log.d(TAG, "Button not found: $buttonText")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking button: ${e.message}")
            false
        }
    }
    
    /**
     * Find a text field and type into it
     */
    fun typeIntoField(fieldHint: String, text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        return try {
            val fields = findEditableFields(rootNode, fieldHint)
            if (fields.isNotEmpty()) {
                val field = fields.first()
                
                // First, focus the field
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                
                // Then, set the text
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                Log.d(TAG, "Typed into field '$fieldHint': $text")
                true
            } else {
                Log.d(TAG, "Field not found: $fieldHint")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error typing into field: ${e.message}")
            false
        }
    }
    
    /**
     * Apply a promo code by finding the promo field, typing the code, and clicking apply
     */
    suspend fun applyPromoCode(promoCode: String): Boolean {
        Log.d(TAG, "Attempting to apply promo code: $promoCode")
        
        // Step 1: Find and type into the promo code field
        val typed = typeIntoField("promo", promoCode) || 
                    typeIntoField("voucher", promoCode) || 
                    typeIntoField("discount", promoCode) ||
                    typeIntoField("code", promoCode)
        
        if (!typed) {
            Log.e(TAG, "Could not find promo code field")
            return false
        }
        
        // Wait a moment for the UI to update
        delay(300)
        
        // Step 2: Click the apply button
        val clicked = clickButton("Apply") || 
                      clickButton("Add") || 
                      clickButton("Redeem") ||
                      clickButton("Submit")
        
        if (!clicked) {
            Log.w(TAG, "Could not find apply button, trying to submit")
            // Try pressing Enter/Done on keyboard
            performGlobalAction(GLOBAL_ACTION_BACK) // Close keyboard first
        }
        
        return typed
    }
    
    /**
     * Perform a tap gesture at specific coordinates
     */
    fun tap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * Perform a swipe gesture
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    /**
     * Scroll down on the current screen
     */
    fun scrollDown(): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.7f
        val endY = displayMetrics.heightPixels * 0.3f
        
        return swipe(centerX, startY, centerX, endY)
    }
    
    /**
     * Get all text content visible on screen
     */
    fun getScreenContent(): List<String> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val texts = mutableListOf<String>()
        
        collectTextFromNode(rootNode, texts)
        return texts
    }
    
    /**
     * Find an element by resource ID
     */
    fun findByResourceId(resourceId: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
        return nodes.firstOrNull()
    }
    
    // ========== Helper methods ==========
    
    private fun findNodesByText(node: AccessibilityNodeInfo, texts: List<String>): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        for (text in texts) {
            results.addAll(node.findAccessibilityNodeInfosByText(text))
        }
        
        return results
    }
    
    private fun findClickableByText(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val allNodes = node.findAccessibilityNodeInfosByText(text)
        return allNodes.filter { it.isClickable || it.parent?.isClickable == true }
    }
    
    private fun findEditableFields(node: AccessibilityNodeInfo, hint: String): List<AccessibilityNodeInfo> {
        val allNodes = node.findAccessibilityNodeInfosByText(hint)
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        
        for (n in allNodes) {
            if (n.isEditable) {
                editableNodes.add(n)
            } else {
                // Check if there's an editable sibling or child nearby
                val parent = n.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child?.isEditable == true) {
                            editableNodes.add(child)
                        }
                    }
                }
            }
        }
        
        // Also look for EditText nodes directly
        findEditableNodesRecursive(node, editableNodes)
        
        return editableNodes.distinctBy { it.hashCode() }
    }
    
    private fun findEditableNodesRecursive(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>) {
        node ?: return
        
        if (node.isEditable) {
            results.add(node)
        }
        
        for (i in 0 until node.childCount) {
            findEditableNodesRecursive(node.getChild(i), results)
        }
    }
    
    private fun collectTextFromNode(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        node ?: return
        
        node.text?.let { texts.add(it.toString()) }
        node.contentDescription?.let { texts.add(it.toString()) }
        
        for (i in 0 until node.childCount) {
            collectTextFromNode(node.getChild(i), texts)
        }
    }
}

/**
 * Events emitted by the accessibility service
 */
sealed class ActionEvent {
    data class WindowChanged(val packageName: String, val className: String?) : ActionEvent()
    data class PromoFieldFound(val packageName: String, val fieldCount: Int) : ActionEvent()
    data class ActionCompleted(val action: String, val success: Boolean) : ActionEvent()
}

/**
 * Actions that can be queued for automation
 */
sealed class AutomationAction {
    data class TypeText(val fieldHint: String, val text: String) : AutomationAction()
    data class ClickButton(val buttonText: String) : AutomationAction()
    data class Tap(val x: Float, val y: Float) : AutomationAction()
    data class Wait(val millis: Long) : AutomationAction()
}
