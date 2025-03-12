package com.twilio.twilio_voice.service

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.*
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.twilio.twilio_voice.R
import com.twilio.twilio_voice.call.TVCallInviteParametersImpl
import com.twilio.twilio_voice.call.TVCallParametersImpl
import com.twilio.twilio_voice.call.TVParameters
import com.twilio.twilio_voice.fcm.VoiceFirebaseMessagingService
import com.twilio.twilio_voice.receivers.TVBroadcastReceiver
import com.twilio.twilio_voice.storage.Storage
import com.twilio.twilio_voice.storage.StorageImpl
import com.twilio.twilio_voice.types.BundleExtensions.getParcelableSafe
import com.twilio.twilio_voice.types.CallDirection
import com.twilio.twilio_voice.types.CompletionHandler
import com.twilio.twilio_voice.types.ContextExtension.appName
import com.twilio.twilio_voice.types.ContextExtension.hasCallPhonePermission
import com.twilio.twilio_voice.types.ContextExtension.hasManageOwnCallsPermission
import com.twilio.twilio_voice.types.IntentExtension.getParcelableExtraSafe
import com.twilio.twilio_voice.types.TVNativeCallEvents
import com.twilio.twilio_voice.types.TelecomManagerExtension.canReadPhoneState
import com.twilio.twilio_voice.types.TelecomManagerExtension.getPhoneAccountHandle
import com.twilio.twilio_voice.types.TelecomManagerExtension.hasCallCapableAccount
import com.twilio.twilio_voice.types.TelecomManagerExtension.registerPhoneAccount
import com.twilio.twilio_voice.types.ValueBundleChanged
import com.twilio.voice.*
import com.twilio.voice.Call


class TVConnectionService : ConnectionService() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        val TAG = "TwilioVoiceConnectionService"

        val activeConnections = HashMap<String, TVCallConnection>()

        val TWI_SCHEME: String = "twi"

        val SERVICE_TYPE_MICROPHONE: Int = 100

        //region ACTIONS_* Constants
        /**
         * Action used with [VoiceFirebaseMessagingService] to notify of incoming calls
         */
        const val ACTION_CALL_INVITE: String = "ACTION_CALL_INVITE"

        //region ACTIONS_* Constants
        /**
         * Action used with [EXTRA_CALL_HANDLE] to cancel a call connection.
         */
        const val ACTION_CANCEL_CALL_INVITE: String = "ACTION_CANCEL_CALL_INVITE"

        /**
         * Action used with [EXTRA_DIGITS] to send digits to the [TVConnection] active call.
         */
        const val ACTION_SEND_DIGITS: String = "ACTION_SEND_DIGITS"

        /**
         * Action used to hangup an active call connection.
         */
        const val ACTION_HANGUP: String = "ACTION_HANGUP"

        /**
         * Action used to toggle the speakerphone state of an active call connection.
         */
        const val ACTION_TOGGLE_SPEAKER: String = "ACTION_TOGGLE_SPEAKER"

        /**
         * Action used to toggle bluetooth state of an active call connection.
         */
        const val ACTION_TOGGLE_BLUETOOTH: String = "ACTION_TOGGLE_BLUETOOTH"

        /**
         * Action used to toggle hold state of an active call connection.
         */
        const val ACTION_TOGGLE_HOLD: String = "ACTION_TOGGLE_HOLD"

        /**
         * Action used to toggle mute state of an active call connection.
         */
        const val ACTION_TOGGLE_MUTE: String = "ACTION_TOGGLE_MUTE"

        /**
         * Action used to answer an incoming call connection.
         */
        const val ACTION_ANSWER: String = "ACTION_ANSWER"

        /**
         * Action used to answer an incoming call connection.
         */
        const val ACTION_INCOMING_CALL: String = "ACTION_INCOMING_CALL"

        /**
         * Action used to place an outgoing call connection.
         * Additional parameters are required: [EXTRA_TOKEN], [EXTRA_TO] and [EXTRA_FROM]. Optionally, [EXTRA_OUTGOING_PARAMS] for bundled extra custom parameters.
         */
        const val ACTION_PLACE_OUTGOING_CALL: String = "ACTION_PLACE_OUTGOING_CALL"

        /**
         * Action used to poll the ConnectionService for the active call handle.
         */
        const val ACTION_ACTIVE_HANDLE: String = "ACTION_ACTIVE_HANDLE"

        //region EXTRA_* Constants
        /**
         * Extra used with [ACTION_SEND_DIGITS] to send digits to the [TVConnection] active call.
         */
        const val EXTRA_DIGITS: String = "EXTRA_DIGITS"

        /**
         * Extra used with [ACTION_CANCEL_CALL_INVITE] to cancel a call connection.
         */
        const val EXTRA_INCOMING_CALL_INVITE: String = "EXTRA_INCOMING_CALL_INVITE"

        /**
         * Extra used to identify a call connection.
         */
        const val EXTRA_CALL_HANDLE: String = "EXTRA_CALL_HANDLE"

        /**
         * Extra used with [ACTION_CANCEL_CALL_INVITE] to cancel a call connection
         */
        const val EXTRA_CANCEL_CALL_INVITE: String = "EXTRA_CANCEL_CALL_INVITE"

        /**
         * Extra used with [ACTION_PLACE_OUTGOING_CALL] to place an outgoing call connection. Denotes the Twilio Voice access token.
         */
        const val EXTRA_TOKEN: String = "EXTRA_TOKEN"

        /**
         * Extra used with [ACTION_PLACE_OUTGOING_CALL] to place an outgoing call connection. Denotes the recipient's identity.
         */
        const val EXTRA_TO: String = "EXTRA_TO"

        /**
         * Extra used with [ACTION_PLACE_OUTGOING_CALL] to place an outgoing call connection. Denotes the caller's identity.
         */
        const val EXTRA_FROM: String = "EXTRA_FROM"

        /**
         * Extra used with [ACTION_PLACE_OUTGOING_CALL] to send additional parameters to the [TVConnectionService] active call.
         */
        const val EXTRA_OUTGOING_PARAMS: String = "EXTRA_OUTGOING_PARAMS"

        /**
         * Extra used with [ACTION_TOGGLE_SPEAKER] to send additional parameters to the [TVCallConnection] active call.
         */
        const val EXTRA_SPEAKER_STATE: String = "EXTRA_SPEAKER_STATE"

        /**
         * Extra used with [ACTION_TOGGLE_BLUETOOTH] to send additional parameters to the [TVCallConnection] active call.
         */
        const val EXTRA_BLUETOOTH_STATE: String = "EXTRA_BLUETOOTH_STATE"

        /**
         * Extra used with [ACTION_TOGGLE_HOLD] to send additional parameters to the [TVCallConnection] active call.
         */
        const val EXTRA_HOLD_STATE: String = "EXTRA_HOLD_STATE"

        /**
         * Extra used with [ACTION_TOGGLE_MUTE] to send additional parameters to the [TVCallConnection] active call.
         */
        const val EXTRA_MUTE_STATE: String = "EXTRA_MUTE_STATE"
        //endregion

        fun hasActiveCalls(): Boolean {
            return activeConnections.isNotEmpty()
        }

        /**
         * Active call definition is extended to include calls in which one can actively communicate, or call is on hold, or call is ringing or dialing. This applies only to this and calling functions.
         * Gets the first ongoing call handle, if any. Else, gets the first call on hold. Lastly, gets the first call in either a ringing or dialing state, if any. Returns null if there are no active calls. If there are more than one active calls, the first call handle is returned.
         * Note: this might not necessarily correspond to the current active call.
         */
        fun getActiveCallHandle(): String? {
            if (!hasActiveCalls()) return null
            return activeConnections.entries.firstOrNull { it.value.state == Connection.STATE_ACTIVE }?.key
                ?: activeConnections.entries.firstOrNull { it.value.state == Connection.STATE_HOLDING }?.key
                ?: activeConnections.entries.firstOrNull {
                    arrayListOf(
                        Connection.STATE_RINGING,
                        Connection.STATE_DIALING
                    ).contains(it.value.state)
                }?.key
        }

        fun getIncomingCallHandle(): String? {
            if (!hasActiveCalls()) return null
            return activeConnections.entries.firstOrNull { it.value.state == Connection.STATE_RINGING }?.key
        }

        fun getConnection(callSid: String): TVCallConnection? {
            return activeConnections[callSid]
        }
    }

    private fun stopSelfSafe(): Boolean {
        if (!hasActiveCalls()) {
            stopSelf()
            return true
        } else {
            return false
        }
    }

    //region Service onStartCommand
    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread.currentThread().contextClassLoader = CallInvite::class.java.classLoader
        super.onStartCommand(intent, flags, startId)
        intent?.let {
            when (it.action) {
                ACTION_SEND_DIGITS -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_SEND_DIGITS is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }
                    val digits = it.getStringExtra(EXTRA_DIGITS) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: ACTION_SEND_DIGITS is missing String EXTRA_DIGITS"
                        )
                        return@let
                    }

                    getConnection(callHandle)?.sendDigits(digits) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_SEND_DIGITS] could not find connection for callHandle: $callHandle"
                        )
                    }
                }

                ACTION_CANCEL_CALL_INVITE -> {
                    // Load CancelledCallInvite class loader
                    // See: https://github.com/twilio/voice-quickstart-android/issues/561#issuecomment-1678613170
                    it.setExtrasClassLoader(CallInvite::class.java.classLoader)
                    val cancelledCallInvite =
                        it.getParcelableExtraSafe<CancelledCallInvite>(EXTRA_CANCEL_CALL_INVITE)
                            ?: run {
                                Log.e(
                                    TAG,
                                    "onStartCommand: ACTION_CANCEL_CALL_INVITE is missing parcelable EXTRA_CANCEL_CALL_INVITE"
                                )
                                return@let
                            }

                    val callHandle = cancelledCallInvite.callSid
                    getConnection(callHandle)?.onAbort() ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_CANCEL_CALL_INVITE] could not find connection for callHandle: $callHandle"
                        )
                    }

                    // Close notification
                    stopForegroundService()

                    // Show missed call notification
                    showMissedCallNotification(cancelledCallInvite)
                }

                ACTION_INCOMING_CALL -> {
                    // Load CallInvite class loader & get callInvite
                    val callInvite =
                        it.getParcelableExtraSafe<CallInvite>(EXTRA_INCOMING_CALL_INVITE) ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: 'ACTION_INCOMING_CALL' is missing parcelable 'EXTRA_INCOMING_CALL_INVITE'"
                            )
                            return@let
                        }

                    val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                    if (!telecomManager.canReadPhoneState(applicationContext)) {
                        Log.e(
                            TAG,
                            "onCallInvite: Permission to read phone state not granted or requested."
                        )
                        callInvite.reject(applicationContext)
                        return@let
                    }

                    val phoneAccountHandle =
                        telecomManager.getPhoneAccountHandle(applicationContext)
                    val phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle)
                    if (phoneAccount == null) {
                        Log.e(
                            TAG,
                            "onStartCommand: PhoneAccount is null, make sure to register one with `registerPhoneAccount()`"
                        )
                        return@let
                    }
                    if (!phoneAccount.isEnabled) {
                        Log.e(
                            TAG,
                            "onStartCommand: PhoneAccount is not enabled, prompt the user to enable the phone account by opening settings with `openPhoneAccountSettings()`"
                        )
                        return@let
                    }

                    // Get telecom manager
//                    if (!telecomManager.hasCallCapableAccount(applicationContext, phoneAccountHandle.componentName.className)) {
//                        Log.e(
//                            TAG, "onCallInvite: No registered phone account for PhoneHandle $phoneAccountHandle.\n" +
//                                    "Check the following:\n" +
//                                    "- Have you requested READ_PHONE_STATE permissions\n" +
//                                    "- Have you registered a PhoneAccount \n" +
//                                    "- Have you activated the Calling Account?"
//                        )
//                        callInvite.reject(applicationContext)
//                        return@let
//                    }

                    val myBundle: Bundle = Bundle().apply {
                        putParcelable(EXTRA_INCOMING_CALL_INVITE, callInvite)
                    }
                    myBundle.classLoader = CallInvite::class.java.classLoader

                    // Add extras for [addNewIncomingCall] method
                    val extras = Bundle().apply {
                        putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, myBundle)
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)

                        if (callInvite.customParameters.containsKey("_TWI_SUBJECT")) {
                            putString(
                                TelecomManager.EXTRA_CALL_SUBJECT,
                                callInvite.customParameters["_TWI_SUBJECT"]
                            )
                        }
                    }

                    // Add new incoming call to the telecom manager
                    telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                }

                ACTION_ANSWER -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getIncomingCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_ANSWER is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }

                    if (!isAppVisible() && !isDeviceLocked()) {
                        val callInvite =
                            it.getParcelableExtraSafe<CallInvite>(EXTRA_INCOMING_CALL_INVITE)
                                ?: run {
                                    Log.e(
                                        TAG,
                                        "onStartCommand: 'ACTION_ANSWER' is missing parcelable 'EXTRA_INCOMING_CALL_INVITE'"
                                    )
                                    return@let
                                }

                        // Open app
                        Intent().apply {
                            action = "${applicationContext.packageName}.intent.action.INCOMING_CALL"
                            putExtra("EXTRA_CALL_FROM", callInvite.from)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                        or Intent.FLAG_ACTIVITY_NO_HISTORY
                            )
                            applicationContext.startActivity(this)
                        }
                    } else {
                        val connection = getConnection(callHandle) ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: [ACTION_ANSWER] could not find connection for callHandle: $callHandle"
                            )
                        }

                        if (connection is TVCallInviteConnection) {
                            connection.acceptInvite()
                        } else {
                            Log.e(
                                TAG,
                                "onStartCommand: [ACTION_ANSWER] could not find connection for callHandle: $callHandle"
                            )
                        }

                        // Close notification
                        stopForegroundService()
                    }
                }

                ACTION_HANGUP -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_HANGUP is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }

                    getConnection(callHandle)?.disconnect() ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_HANGUP] could not find connection for callHandle: $callHandle"
                        )
                    }

                    // Close notification
                    stopForegroundService()
                }

                ACTION_PLACE_OUTGOING_CALL -> {
                    // check required EXTRA_TOKEN, EXTRA_TO, EXTRA_FROM
                    val token = it.getStringExtra(EXTRA_TOKEN) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TOKEN"
                        )
                        return@let
                    }
                    val to = it.getStringExtra(EXTRA_TO) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TO"
                        )
                        return@let
                    }
                    val from = it.getStringExtra(EXTRA_FROM) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_FROM"
                        )
                        return@let
                    }

                    // Get all params from bundle
                    val params = HashMap<String, String>()
                    val outGoingParams = it.getParcelableExtraSafe<Bundle>(EXTRA_OUTGOING_PARAMS)
                    outGoingParams?.keySet()?.forEach { key ->
                        outGoingParams.getString(key)?.let { value ->
                            params[key] = value
                        }
                    }

                    // Add required params
                    params[EXTRA_FROM] = from
                    params[EXTRA_TO] = to
                    params[EXTRA_TOKEN] = token

                    // Create Twilio Param bundles
                    val myBundle = Bundle().apply {
                        putBundle(EXTRA_OUTGOING_PARAMS, Bundle().apply {
                            params.forEach { (key, value) ->
                                putString(key, value)
                            }
                        })
                    }

                    val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                    val phoneAccountHandle =
                        telecomManager.getPhoneAccountHandle(applicationContext)

                    if (!telecomManager.canReadPhoneState(applicationContext)) {
                        Log.e(TAG, "onStartCommand: Missing READ_PHONE_STATE permission")
                        return@let
                    }

                    val phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle)
                    if (phoneAccount == null) {
                        Log.e(
                            TAG,
                            "onStartCommand: PhoneAccount is null, make sure to register one with `registerPhoneAccount()`"
                        )
                        return@let
                    }
                    if (!phoneAccount.isEnabled) {
                        Log.e(
                            TAG,
                            "onStartCommand: PhoneAccount is not enabled, prompt the user to enable the phone account by opening settings with `openPhoneAccountSettings()`"
                        )
                        return@let
                    }

                    if (!telecomManager.hasCallCapableAccount(
                            applicationContext,
                            phoneAccountHandle.componentName.className
                        )
                    ) {
                        Log.e(
                            TAG,
                            "onStartCommand: No registered phone account for PhoneHandle $phoneAccountHandle"
                        )
                        telecomManager.registerPhoneAccount(applicationContext, phoneAccountHandle)
                    }

                    if (!applicationContext.hasCallPhonePermission()) {
                        Log.e(
                            TAG,
                            "onStartCommand: Missing CALL_PHONE permission, request permission with `requestCallPhonePermission()`"
                        )
                        return@let
                    }

                    if (!applicationContext.hasManageOwnCallsPermission()) {
                        Log.e(
                            TAG,
                            "onStartCommand: Missing MANAGE_OWN_CALLS permission, request permission with `requestManageOwnCallsPermission()`"
                        )
                        return@let
                    }

                    // Create outgoing extras
                    val extras = Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                        putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, myBundle)
                    }

                    val address: Uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, to, null)
                    telecomManager.placeCall(address, extras)
                }

                ACTION_TOGGLE_BLUETOOTH -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_TOGGLE_BLUETOOTH is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }
                    val bluetoothState = it.getBooleanExtra(EXTRA_BLUETOOTH_STATE, false)

                    getConnection(callHandle)?.toggleBluetooth(bluetoothState) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_TOGGLE_BLUETOOTH] could not find connection for callHandle: $callHandle"
                        )
                    }
                }

                ACTION_TOGGLE_HOLD -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_TOGGLE_HOLD is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }
                    val holdState = it.getBooleanExtra(EXTRA_HOLD_STATE, false)

                    getConnection(callHandle)?.toggleHold(holdState) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_TOGGLE_HOLD] could not find connection for callHandle: $callHandle"
                        )
                    }
                }

                ACTION_TOGGLE_MUTE -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_TOGGLE_MUTE is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }
                    val muteState = it.getBooleanExtra(EXTRA_MUTE_STATE, false)

                    getConnection(callHandle)?.toggleMute(muteState) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_TOGGLE_MUTE] could not find connection for callHandle: $callHandle"
                        )
                    }
                }

                ACTION_TOGGLE_SPEAKER -> {
                    val callHandle =
                        it.getStringExtra(EXTRA_CALL_HANDLE) ?: getActiveCallHandle() ?: run {
                            Log.e(
                                TAG,
                                "onStartCommand: ACTION_TOGGLE_SPEAKER is missing String EXTRA_CALL_HANDLE"
                            )
                            return@let
                        }
                    val speakerState = it.getBooleanExtra(EXTRA_SPEAKER_STATE, false)
                    getConnection(callHandle)?.toggleSpeaker(speakerState) ?: run {
                        Log.e(
                            TAG,
                            "onStartCommand: [ACTION_TOGGLE_SPEAKER] could not find connection for callHandle: $callHandle"
                        )
                    }
                }

                ACTION_ACTIVE_HANDLE -> {
                    val activeCallHandle = getActiveCallHandle()
                    sendBroadcastCallHandle(applicationContext, activeCallHandle)
                }

                else -> {
                    Log.e(TAG, "onStartCommand: unknown action: ${it.action}")
                }
            }
        } ?: run {
            Log.e(TAG, "onStartCommand: intent is null")
        }
        return START_STICKY
    }
    //endregion

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        assert(request != null) { "ConnectionRequest cannot be null" }
        assert(connectionManagerPhoneAccount != null) { "ConnectionManagerPhoneAccount cannot be null" }

        super.onCreateIncomingConnection(connectionManagerPhoneAccount, request)
        Log.d(TAG, "onCreateIncomingConnection")

        val extras = request?.extras
        val myBundle: Bundle = extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS) ?: run {
            Log.e(
                TAG,
                "onCreateIncomingConnection: request is missing Bundle EXTRA_INCOMING_CALL_EXTRAS"
            )
            throw Exception("onCreateIncomingConnection: request is missing Bundle EXTRA_INCOMING_CALL_EXTRAS");
        }

        myBundle.classLoader = CallInvite::class.java.classLoader
        val ci: CallInvite = myBundle.getParcelableSafe(EXTRA_INCOMING_CALL_INVITE) ?: run {
            Log.e(
                TAG,
                "onCreateIncomingConnection: request is missing CallInvite EXTRA_INCOMING_CALL_INVITE"
            )
            throw Exception("onCreateIncomingConnection: request is missing CallInvite EXTRA_INCOMING_CALL_INVITE");
        }

        // Create storage instance for call parameters
        val storage: Storage = StorageImpl(applicationContext)

        // Resolve call parameters
        val callParams: TVParameters = TVCallInviteParametersImpl(storage, ci);

        // Create connection
        val connection = TVCallInviteConnection(applicationContext, ci, callParams)

        // Remove call invite from extras, causes marshalling error i.e. Class not found.
        val requestBundle = request.extras.also { it ->
            it.remove(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
        }
        connection.extras = requestBundle

        // Setup connection event listeners and UI parameters
        attachCallEventListeners(connection, ci.callSid)
        applyParameters(connection, callParams)
        connection.setRinging()

        // Start play ringtone and show notification
        playRingtone()
        startForegroundService(ci)

        if (!isAppVisible()) {
            // Open app
            Intent().apply {
                action = "${applicationContext.packageName}.intent.action.INCOMING_CALL"
                putExtra("EXTRA_CALL_FROM", ci.from)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                            or Intent.FLAG_ACTIVITY_NO_HISTORY
                )
                applicationContext.startActivity(this)
            }
        }

        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        assert(request != null) { "ConnectionRequest cannot be null" }
        assert(connectionManagerPhoneAccount != null) { "ConnectionManagerPhoneAccount cannot be null" }

        super.onCreateOutgoingConnection(connectionManagerPhoneAccount, request)
        Log.d(TAG, "onCreateOutgoingConnection")

        val extras = request?.extras
        val myBundle: Bundle = extras?.getBundle(EXTRA_OUTGOING_PARAMS) ?: run {
            Log.e(
                TAG,
                "onCreateOutgoingConnection: request is missing Bundle EXTRA_OUTGOING_PARAMS"
            )
            throw Exception("onCreateOutgoingConnection: request is missing Bundle EXTRA_OUTGOING_PARAMS");
        }

        // check required EXTRA_TOKEN, EXTRA_TO, EXTRA_FROM
        val token: String = myBundle.getString(EXTRA_TOKEN) ?: run {
            Log.e(
                TAG,
                "onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TOKEN"
            )
            throw Exception("onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TOKEN");
        }
        val to = myBundle.getString(EXTRA_TO) ?: run {
            Log.e(
                TAG,
                "onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TO"
            )
            throw Exception("onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_TO");
        }
        val from = myBundle.getString(EXTRA_FROM) ?: run {
            Log.e(
                TAG,
                "onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_FROM"
            )
            throw Exception("onCreateOutgoingConnection: ACTION_PLACE_OUTGOING_CALL is missing String EXTRA_FROM");
        }

        // Get all params from bundle
        val params = HashMap<String, String>()
        myBundle.keySet().forEach { key ->
            when (key) {
                EXTRA_TO, EXTRA_FROM, EXTRA_TOKEN -> {}
                else -> {
                    myBundle.getString(key)?.let { value ->
                        params[key] = value
                    }
                }
            }
        }
        params["From"] = from
        params["To"] = to

        // create connect options
        val connectOptions = ConnectOptions.Builder(token)
            .params(params)
            .build()

        // create outgoing connection
        val connection = TVCallConnection(applicationContext)

        // create Voice SDK call
        connection.twilioCall = Voice.connect(applicationContext, connectOptions, connection)

        // create storage instance for call parameters
        val mStorage: Storage = StorageImpl(applicationContext)

        // Set call state listener, applies non-temporary Call SID when call is ringing or connected (i.e. when assigned by Twilio)
        val onCallStateListener: CompletionHandler<Call.State> = CompletionHandler { state ->
            if (state == Call.State.RINGING || state == Call.State.CONNECTED) {
                val call = connection.twilioCall!!
                val callSid = call.sid!!

                // Resolve call parameters
                val callParams = TVCallParametersImpl(mStorage, call, to, from, params)
                connection.setCallParameters(callParams)

                // If call is not attached, attach it
                if (!activeConnections.containsKey(callSid)) {
                    applyParameters(connection, callParams)
                    attachCallEventListeners(connection, callSid)
                    callParams.callSid = callSid
                }
            }
        }
        connection.setOnCallStateListener(onCallStateListener)

        // Setup connection UI parameters
        connection.setInitializing()

        // Apply extras
        connection.extras = request.extras

        return connection
    }

    /**
     * Attach call event listeners to the given connection. This includes responding to call events, call actions and when call has ended.
     * @param connection The connection to attach the listeners to.
     * @param callSid The call SID of the connection.
     */
    private fun <T : TVCallConnection> attachCallEventListeners(connection: T, callSid: String) {

        val onAction: ValueBundleChanged<String> =
            ValueBundleChanged { event: String?, extra: Bundle? ->
                sendBroadcastEvent(applicationContext, event ?: "", callSid, extra)
            }

        val onEvent: ValueBundleChanged<String> =
            ValueBundleChanged { event: String?, extra: Bundle? ->
                sendBroadcastEvent(applicationContext, event ?: "", callSid, extra)
                // This is a temporary solution since `isOnCall` returns true when there is an active ConnectionService, regardless of the source app. This also applies to SIM/Telecom calls.
                sendBroadcastCallHandle(
                    applicationContext,
                    extra?.getString(TVBroadcastReceiver.EXTRA_CALL_HANDLE)
                )
                // Stop notification when call connect failed
                if (TVNativeCallEvents.EVENT_CONNECT_FAILURE == event) {
                    stopForegroundService()
                    stopSelfSafe()
                }
                // Stop notification when call is connected
                if (TVNativeCallEvents.EVENT_CONNECTED == event) {
                    stopForegroundService()
                }
            }
        val onDisconnect: CompletionHandler<DisconnectCause> = CompletionHandler {
            if (activeConnections.containsKey(callSid)) {
                activeConnections.remove(callSid)
            }
            stopForegroundService()
            stopSelfSafe()
        }

        // Add to local connection cache
        activeConnections[callSid] = connection

        // attach listeners
        connection.setOnCallActionListener(onAction)
        connection.setOnCallEventListener(onEvent)
        connection.setOnCallDisconnected(onDisconnect)
    }

    /**
     * Apply the given parameters to the given connection. This sets the address, caller display name and subject, any and all if present.
     * @param connection The connection to apply the parameters to.
     * @param params The parameters to apply to the connection.
     */
    private fun <T : TVCallConnection> applyParameters(connection: T, params: TVParameters) {
        params.getExtra(TVParameters.PARAM_SUBJECT, null)?.let {
            connection.extras.putString(TelecomManager.EXTRA_CALL_SUBJECT, it)
        }
        val name =
            if (connection.callDirection == CallDirection.OUTGOING) params.to else params.from
        connection.setAddress(
            Uri.fromParts(PhoneAccount.SCHEME_TEL, name, null),
            TelecomManager.PRESENTATION_ALLOWED
        )
        connection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
    }

    private fun sendBroadcastEvent(
        ctx: Context,
        event: String,
        callSid: String?,
        extras: Bundle? = null
    ) {
        Intent(ctx, TVBroadcastReceiver::class.java).apply {
            action = event
            putExtra(EXTRA_CALL_HANDLE, callSid)
            extras?.let { putExtras(it) }
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(this)
        }
    }

    private fun sendBroadcastCallHandle(ctx: Context, callSid: String?) {
        Log.d(TAG, "sendBroadcastCallHandle: ${if (callSid != null) "On call" else "Not on call"}}")
        Intent(ctx, TVBroadcastReceiver::class.java).apply {
            action = TVBroadcastReceiver.ACTION_ACTIVE_CALL_CHANGED
            putExtra(EXTRA_CALL_HANDLE, callSid)
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(this)
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.d(TAG, "onCreateOutgoingConnectionFailed")
        stopForegroundService()
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.d(TAG, "onCreateIncomingConnectionFailed")
        stopForegroundService()
    }

    private fun getOrCreateChannel(): NotificationChannel {
        val id = "${applicationContext.packageName}_calls"
        val name = applicationContext.appName
        val descriptionText = "Active Voice Calls"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(id, name, importance).apply {
            description = descriptionText
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return channel
    }

    private fun createNotification(callInvite: CallInvite): Notification {
        val channel = getOrCreateChannel()
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val intent = Intent(
            applicationContext,
            TVConnectionService::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            0,
            intent,
            flag,
        )

        val acceptCallIntent = Intent(
            applicationContext,
            TVConnectionService::class.java,
        ).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_INCOMING_CALL_INVITE, callInvite)
            putExtra(EXTRA_CALL_HANDLE, callInvite.callSid)
        }
        val acceptCallPendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            acceptCallIntent,
            flag,
        )
        val acceptCallAction = Notification.Action.Builder(
            Icon.createWithResource(applicationContext, R.drawable.ic_microphone),
            "Accept",
            acceptCallPendingIntent,
        ).build()

        val declineCallIntent = Intent(
            applicationContext,
            TVConnectionService::class.java,
        ).apply {
            action = ACTION_HANGUP
            putExtra(EXTRA_INCOMING_CALL_INVITE, callInvite)
            putExtra(EXTRA_CALL_HANDLE, callInvite.callSid)
        }
        val declineCallPendingIntent = PendingIntent.getService(
            applicationContext,
            2,
            declineCallIntent,
            flag,
        )
        val declineCallAction = Notification.Action.Builder(
            Icon.createWithResource(applicationContext, R.drawable.ic_microphone),
            "Decline",
            declineCallPendingIntent,
        ).build()

        return Notification.Builder(this, channel.id).apply {
            setOngoing(true)
            setSmallIcon(R.drawable.ic_app_logo)
            setContentTitle("Incoming Call")
            setContentText(callInvite.from)
            setCategory(Notification.CATEGORY_CALL)
            setFullScreenIntent(pendingIntent, true)
            addAction(declineCallAction)
            addAction(acceptCallAction)
        }.build()
    }

    private fun cancelNotification() {
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SERVICE_TYPE_MICROPHONE)
    }

    /// Source: https://github.com/react-native-webrtc/react-native-callkeep/blob/master/android/src/main/java/io/wazo/callkeep/VoiceConnectionService.java#L295
    private fun startForegroundService(callInvite: CallInvite) {
        val notification = createNotification(callInvite)
        Log.d(TAG, "[VoiceConnectionService] Starting foreground service")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Optional for Android +11, required for Android +14
                startForeground(
                    SERVICE_TYPE_MICROPHONE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(SERVICE_TYPE_MICROPHONE, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[VoiceConnectionService] Can't start foreground service : $e")
        }
    }

    /// Source: https://github.com/react-native-webrtc/react-native-callkeep/blob/master/android/src/main/java/io/wazo/callkeep/VoiceConnectionService.java#L352C5-L377C6
    private fun stopForegroundService() {
        Log.d(TAG, "[VoiceConnectionService] stopForegroundService")
        try {
            stopRingtone()
            stopForeground(SERVICE_TYPE_MICROPHONE)
            cancelNotification()
            // Some android devices need to cancel notification twice to completely remove it
            stopForeground(SERVICE_TYPE_MICROPHONE)
            cancelNotification()
        } catch (e: java.lang.Exception) {
            Log.w(TAG, "[VoiceConnectionService] can't stop foreground service :$e")
        }
    }

    private fun showMissedCallNotification(callInvite: CancelledCallInvite) {
        val storage: Storage = StorageImpl(applicationContext)
        if (!storage.showNotifications) {
            return
        }

        val channel = getOrCreateChannel()

        val notification = Notification.Builder(this, channel.id)
            .setSmallIcon(R.drawable.ic_app_logo) // Use an appropriate missed call icon
            .setContentTitle("Missed Call")
            .setContentText(callInvite.from)
            .setCategory(Notification.CATEGORY_CALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification) // Unique ID for missed calls
    }

    /**
     * Check if app is in foreground or not
     *
     * @return **`true`** if app is in foreground, **`false`** otherwise
     */
    private fun isAppVisible(): Boolean {
        return ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    /**
     * Check if device is locked
     *
     * @return **`true`** if device is locked, **`false`** otherwise
     */
    private fun isDeviceLocked(): Boolean {
        val keyboardManager: KeyguardManager =
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyboardManager.isKeyguardLocked;
    }

    /**
     * Play ringtone with or without vibration
     *
     * @param[vibrate] determine whether play ringtone with vibration or not
     */
    private fun playRingtone(vibrate: Boolean = true): Unit {
        try {
            // Ringtone
            val ringtoneManager: RingtoneManager = RingtoneManager(applicationContext)
            ringtoneManager.stopPreviousRingtone()

            val ringtoneUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE,
            )

            ringtone = RingtoneManager.getRingtone(
                applicationContext,
                ringtoneUri,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play();

            // Vibration
            if (vibrate) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager: VibratorManager =
                        this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator
                }

                if (vibrator?.hasVibrator() == true) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(500, 500, 500),
                            1,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace();
        }
    }

    /**
     * Stop ringtone and vibration
     */
    private fun stopRingtone(): Unit {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace();
        }
    }
}
