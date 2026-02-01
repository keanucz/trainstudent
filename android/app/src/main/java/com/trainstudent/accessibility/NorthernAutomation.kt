package com.trainstudent.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Northern Railway App Automation
 * 
 * Automates the process of:
 * 1. Opening Northern app
 * 2. Clicking on the passenger/promo button (below "Outbound", above "Advanced search options")
 * 3. Clicking on Promotions textbox
 * 4. Clearing and pasting the promo code
 * 5. Clicking Select
 * 6. Filling origin, destination, and time
 * 7. Clicking "Search live times and prices"
 */
class NorthernAutomation(private val context: Context) {

    companion object {
        private const val TAG = "NorthernAutomation"
        const val NORTHERN_PACKAGE = "org.northernrail.android"
    }

    enum class AutomationState {
        IDLE,
        LAUNCHING_APP,
        WAITING_FOR_APP,
        FINDING_PASSENGER_BUTTON,
        CLICKED_PASSENGER_BUTTON,
        FINDING_PROMOTIONS_FIELD,
        ENTERING_PROMO_CODE,
        CLICKING_SELECT,
        FILLING_ORIGIN,
        FILLING_DESTINATION,
        FILLING_TIME,
        CLICKING_SEARCH,
        COMPLETED,
        FAILED
    }

    data class NorthernSearchParams(
        val promoCode: String,
        val origin: String,
        val originCrs: String,
        val destination: String,
        val destinationCrs: String,
        val departureTime: String
    )

    private val _state = MutableStateFlow(AutomationState.IDLE)
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var searchParams: NorthernSearchParams? = null

    /**
     * Start the Northern automation flow
     */
    suspend fun startAutomation(params: NorthernSearchParams): Boolean {
        Log.d(TAG, "Starting Northern automation with promo: ${params.promoCode}")
        searchParams = params
        _state.value = AutomationState.LAUNCHING_APP
        _errorMessage.value = null

        // Step 1: Copy promo code to clipboard
        copyToClipboard(params.promoCode)

        // Step 2: Launch Northern app
        if (!launchNorthernApp()) {
            _state.value = AutomationState.FAILED
            _errorMessage.value = "Could not launch Northern app. Is it installed?"
            return false
        }

        _state.value = AutomationState.WAITING_FOR_APP
        
        // Wait for app to fully load
        delay(2000)

        // Now the accessibility service will take over via onNorthernAppReady()
        return true
    }

    /**
     * Called by the accessibility service when Northern app is ready
     */
    suspend fun onNorthernAppReady() {
        val service = TrainStudentAccessibilityService.getInstance() ?: run {
            _state.value = AutomationState.FAILED
            _errorMessage.value = "Accessibility service not running"
            return
        }

        val params = searchParams ?: run {
            _state.value = AutomationState.FAILED
            _errorMessage.value = "No search parameters set"
            return
        }

        try {
            // Step 3: Find and click the passenger/promo button
            // This button is below "Outbound" and above "Advanced search options"
            // It typically shows text like "1 adult, railcard, promo added"
            _state.value = AutomationState.FINDING_PASSENGER_BUTTON
            
            if (!clickPassengerPromoButton(service)) {
                // Try alternative approach - scroll and retry
                service.scrollDown()
                delay(500)
                if (!clickPassengerPromoButton(service)) {
                    _state.value = AutomationState.FAILED
                    _errorMessage.value = "Could not find passenger/promo button"
                    return
                }
            }
            
            _state.value = AutomationState.CLICKED_PASSENGER_BUTTON
            delay(800)  // Wait for modal to open

            // Step 4: Find and click Promotions textbox
            _state.value = AutomationState.FINDING_PROMOTIONS_FIELD
            
            if (!clickPromotionsField(service)) {
                _state.value = AutomationState.FAILED
                _errorMessage.value = "Could not find Promotions field"
                return
            }
            
            delay(500)

            // Step 5: Clear and paste promo code
            _state.value = AutomationState.ENTERING_PROMO_CODE
            
            if (!enterPromoCode(service, params.promoCode)) {
                _state.value = AutomationState.FAILED
                _errorMessage.value = "Could not enter promo code"
                return
            }
            
            delay(300)

            // Step 6: Click Select button
            _state.value = AutomationState.CLICKING_SELECT
            
            if (!clickSelectButton(service)) {
                _state.value = AutomationState.FAILED
                _errorMessage.value = "Could not find Select button"
                return
            }
            
            delay(800)  // Wait for modal to close

            // Step 7: Fill origin
            _state.value = AutomationState.FILLING_ORIGIN
            
            if (!fillStationField(service, "From", params.origin)) {
                if (!fillStationField(service, "Origin", params.origin)) {
                    Log.w(TAG, "Could not auto-fill origin, continuing anyway")
                }
            }
            
            delay(500)

            // Step 8: Fill destination
            _state.value = AutomationState.FILLING_DESTINATION
            
            if (!fillStationField(service, "To", params.destination)) {
                if (!fillStationField(service, "Destination", params.destination)) {
                    Log.w(TAG, "Could not auto-fill destination, continuing anyway")
                }
            }
            
            delay(500)

            // Step 9: The time should already be set to current/default, 
            // but we could fill it if needed
            _state.value = AutomationState.FILLING_TIME
            // For now, we'll skip time filling as the default is usually fine

            // Step 10: Click "Search live times and prices"
            _state.value = AutomationState.CLICKING_SEARCH
            
            if (!clickSearchButton(service)) {
                _state.value = AutomationState.FAILED
                _errorMessage.value = "Could not find Search button"
                return
            }

            _state.value = AutomationState.COMPLETED
            Log.d(TAG, "Northern automation completed successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Automation error: ${e.message}", e)
            _state.value = AutomationState.FAILED
            _errorMessage.value = "Automation error: ${e.message}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Promo Code", text)
        clipboard.setPrimaryClip(clip)
        Log.d(TAG, "Copied promo code to clipboard: $text")
    }

    private fun launchNorthernApp(): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(NORTHERN_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                Log.d(TAG, "Launched Northern app")
                true
            } else {
                Log.e(TAG, "Northern app not installed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Northern app: ${e.message}")
            false
        }
    }

    /**
     * Find and click the passenger/promo button
     * This button is below "Outbound" and above "Advanced search options"
     * It contains text like "1 adult" or "adult" and "promo" or "railcard"
     */
    private fun clickPassengerPromoButton(service: TrainStudentAccessibilityService): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false

        // Strategy 1: Look for text containing "adult" (most reliable)
        val adultNodes = rootNode.findAccessibilityNodeInfosByText("adult")
        for (node in adultNodes) {
            // Check if this node or its parent is clickable
            if (tryClick(node)) {
                Log.d(TAG, "Clicked passenger button via 'adult' text")
                return true
            }
        }

        // Strategy 2: Look for text containing "promo"
        val promoNodes = rootNode.findAccessibilityNodeInfosByText("promo")
        for (node in promoNodes) {
            if (tryClick(node)) {
                Log.d(TAG, "Clicked passenger button via 'promo' text")
                return true
            }
        }

        // Strategy 3: Look for text containing "railcard"
        val railcardNodes = rootNode.findAccessibilityNodeInfosByText("railcard")
        for (node in railcardNodes) {
            if (tryClick(node)) {
                Log.d(TAG, "Clicked passenger button via 'railcard' text")
                return true
            }
        }

        // Strategy 4: Look for common patterns like "Passengers" or "Tickets"
        val passengerNodes = rootNode.findAccessibilityNodeInfosByText("Passenger")
        for (node in passengerNodes) {
            if (tryClick(node)) {
                Log.d(TAG, "Clicked passenger button via 'Passenger' text")
                return true
            }
        }

        return false
    }

    private fun tryClick(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        
        // Try clicking the node itself
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        // Try clicking the parent
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
            depth++
        }
        
        return false
    }

    /**
     * Find and click the Promotions textbox/field
     */
    private fun clickPromotionsField(service: TrainStudentAccessibilityService): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false

        // Look for "Promotions" or "Promo code" text
        val searchTerms = listOf("Promotions", "Promo code", "Promo", "Promotion code", "Discount code")
        
        for (term in searchTerms) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(term)
            for (node in nodes) {
                // Check if this is editable or has an editable sibling
                if (node.isEditable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                
                // Try clicking to open an input field
                if (tryClick(node)) {
                    Log.d(TAG, "Clicked promotions field via '$term' text")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Clear the current field and enter the promo code
     */
    private fun enterPromoCode(service: TrainStudentAccessibilityService, promoCode: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false

        // Find any focused editable field
        val editableField = findFocusedOrEditableField(rootNode)
        
        if (editableField != null) {
            // Clear existing text
            val clearBundle = Bundle()
            clearBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            editableField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearBundle)
            
            // Set the promo code
            val setTextBundle = Bundle()
            setTextBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, promoCode)
            editableField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextBundle)
            
            Log.d(TAG, "Entered promo code: $promoCode")
            return true
        }

        // Fallback: Try using the general typeIntoField method
        return service.typeIntoField("promo", promoCode) ||
               service.typeIntoField("code", promoCode) ||
               service.typeIntoField("discount", promoCode)
    }

    private fun findFocusedOrEditableField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        
        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedOrEditableField(child)
            if (result != null) return result
        }
        
        // If nothing focused, find any editable field
        if (node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isEditable) return child
        }
        
        return null
    }

    /**
     * Click the Select button to confirm promo code
     */
    private fun clickSelectButton(service: TrainStudentAccessibilityService): Boolean {
        return service.clickButton("Select") ||
               service.clickButton("Done") ||
               service.clickButton("Apply") ||
               service.clickButton("OK") ||
               service.clickButton("Confirm")
    }

    /**
     * Fill a station field (From/To)
     */
    private fun fillStationField(service: TrainStudentAccessibilityService, fieldLabel: String, stationName: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false

        // Find the field by label
        val labelNodes = rootNode.findAccessibilityNodeInfosByText(fieldLabel)
        
        for (labelNode in labelNodes) {
            // Find an editable field nearby (sibling or child)
            val parent = labelNode.parent ?: continue
            
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                
                if (sibling.isEditable || sibling.isClickable) {
                    // Click to focus
                    sibling.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    
                    // Wait and then type
                    if (sibling.isEditable) {
                        val bundle = Bundle()
                        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, stationName)
                        sibling.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                        Log.d(TAG, "Filled '$fieldLabel' with: $stationName")
                        return true
                    }
                }
            }
        }

        // Fallback: Try the general method
        return service.typeIntoField(fieldLabel, stationName)
    }

    /**
     * Click the "Search live times and prices" button
     */
    private fun clickSearchButton(service: TrainStudentAccessibilityService): Boolean {
        return service.clickButton("Search live times and prices") ||
               service.clickButton("Search") ||
               service.clickButton("Find trains") ||
               service.clickButton("Search trains")
    }

    /**
     * Check if Northern app is installed
     */
    fun isNorthernAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(NORTHERN_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reset automation state
     */
    fun reset() {
        _state.value = AutomationState.IDLE
        _errorMessage.value = null
        searchParams = null
    }
}
