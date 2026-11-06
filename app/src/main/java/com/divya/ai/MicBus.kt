package com.divya.ai

/**
 * Asli mic state: kaun sach mein mic khol raha hai.
 * WakeBus.liveActive ek purana flag hai jo kabhi galat "true" reh jaata tha, isliye Guardian ke
 * "mic band karo" checks ab isi se chalte hain (yehi asli state hai).
 */
object MicBus {
    @Volatile var geminiMic = false     // Gemini live session ka mic chal raha hai
    @Volatile var offlineMic = false    // offline voice (OfflineBrain) chal raha hai
    @Volatile var recording = false     // Guardian ki awaaz enroll / test recording chal rahi hai

    fun busy(): Boolean = geminiMic || offlineMic || recording
}
