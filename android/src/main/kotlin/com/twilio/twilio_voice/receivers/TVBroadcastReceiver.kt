package com.twilio.twilio_voice.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.twilio.twilio_voice.TwilioVoicePlugin

/**
 * Broadcast receiver provides communication from [TVConnectionService] to [TwilioVoicePlugin]
 */
class TVBroadcastReceiver(private val plugin: TwilioVoicePlugin) : BroadcastReceiver() {
    private val TAG = "TVBroadcastReceiver"

    companion object {
        /**
         * Action used to notify the [TwilioVoicePlugin] for mute state changes.
         */
        const val ACTION_MUTE_STATE: String = "com.twilio.ACTION_MUTE_STATE"

        /**
         * Action used to notify the [TwilioVoicePlugin] for hold state changes.
         */
        const val ACTION_HOLD_STATE: String = "com.twilio.ACTION_HOLD_STATE"

        /**
         * Action used to notify the [TwilioVoicePlugin] for speaker state changes.
         */
        const val ACTION_SPEAKER_STATE: String = "com.twilio.ACTION_SPEAKER_STATE"

        /**
         * Action used to notify the [TwilioVoicePlugin] of a change in the active call state.
         */
        const val ACTION_ACTIVE_CALL_CHANGED: String = "com.twilio.ACTION_ACTIVE_CALL_CHANGED"

        /**
         * Action used to notify the [TwilioVoicePlugin] of an incoming call.
         */
        const val ACTION_INCOMING_CALL: String = "com.twilio.ACTION_INCOMING_CALL"

        /**
         * Action used to notify the [TwilioVoicePlugin] of an incoming call.
         */
        const val ACTION_INCOMING_CALL_IGNORED: String = "com.twilio.ACTION_INCOMING_CALL_IGNORED"

        /**
         * Action used to notify the [TwilioVoicePlugin] of a call ended.
         */
        const val ACTION_CALL_ENDED: String = "com.twilio.ACTION_CALL_ENDED"

        /**
         * Extra used with [ACTION_ACTIVE_CALL_CHANGED], [ACTION_CALL_ENDED] providing the active call handle.
         */
        const val EXTRA_CALL_HANDLE: String = "EXTRA_CALL_HANDLE"

        /**
         * Extra used providing the call's caller ID.
         */
        const val EXTRA_CALL_FROM: String = "EXTRA_CALL_FROM"

        /**
         * Extra used providing the call's recipient ID.
         */
        const val EXTRA_CALL_TO: String = "EXTRA_CALL_TO"

        /**
         * Extra used providing the call direction.
         */
        const val EXTRA_CALL_DIRECTION: String = "EXTRA_CALL_DIRECTION"

        /**
         * Extra used with [ACTION_INCOMING_CALL] providing the [CallInvite].
         */
        const val EXTRA_CALL_INVITE: String = "EXTRA_CALL_INVITE"

        /**
         * Extra used with [ACTION_CALL_STATE] providing call mute state.
         */
        const val EXTRA_MUTE_STATE: String = "EXTRA_MUTE_STATE"

        /**
         * Extra used with [ACTION_CALL_STATE] providing call hold state.
         */
        const val EXTRA_HOLD_STATE: String = "EXTRA_HOLD_STATE"

        /**
         * Extra used with [ACTION_CALL_STATE] providing call speaker state.
         */
        const val EXTRA_SPEAKER_STATE: String = "EXTRA_SPEAKER_STATE"

        /**
         * Extra used with [ACTION_INCOMING_CALL_IGNORED] providing call mute state.
         */
        const val EXTRA_INCOMING_CALL_IGNORED_REASON: String = "EXTRA_INCOMING_CALL_IGNORED_REASON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null) {
            Log.e(TAG, "onReceive: Received null action")
            return
        }
        Log.d(TAG, "onReceive: Received broadcast for action $action")

        plugin.handleBroadcastIntent(intent)
    }
}