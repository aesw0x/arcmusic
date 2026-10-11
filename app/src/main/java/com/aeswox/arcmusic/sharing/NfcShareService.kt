package com.aeswox.arcmusic.sharing

import android.nfc.cardemulation.HostApduService
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NfcShareService : HostApduService() {

    companion object {
        private const val TAG = "NfcShareService"
        @Volatile
        var currentToken: String? = null

        private val _tapDetectedAtElapsedRealtime = MutableStateFlow<Long?>(null)
        val tapDetectedAtElapsedRealtime: StateFlow<Long?> = _tapDetectedAtElapsedRealtime.asStateFlow()
        private val _connectionFailedAtElapsedRealtime = MutableStateFlow<Long?>(null)
        val connectionFailedAtElapsedRealtime: StateFlow<Long?> = _connectionFailedAtElapsedRealtime.asStateFlow()

        fun markTapDetected() {
            _tapDetectedAtElapsedRealtime.value = SystemClock.elapsedRealtime()
        }

        @Synchronized
        fun clearTapDetected(expectedTimestamp: Long) {
            if (_tapDetectedAtElapsedRealtime.value == expectedTimestamp) {
                _tapDetectedAtElapsedRealtime.value = null
            }
        }

        fun markConnectionFailed() {
            _connectionFailedAtElapsedRealtime.value = SystemClock.elapsedRealtime()
        }

        @Synchronized
        fun clearConnectionFailed(expectedTimestamp: Long) {
            if (_connectionFailedAtElapsedRealtime.value == expectedTimestamp) {
                _connectionFailedAtElapsedRealtime.value = null
            }
        }
        
        // Select APDU instruction
        private val SELECT_APDU_HEADER = byteArrayOf(
            0x00.toByte(), // CLA
            0xA4.toByte(), // INS
            0x04.toByte(), // P1
            0x00.toByte()  // P2
        )
        // AID matching the XML
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        
        // Success response
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())
        // Failure response
        private val FAILURE_SW = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        fun readerCallback(onTokenRead: (String) -> Unit): NfcAdapter.ReaderCallback =
            NfcAdapter.ReaderCallback { tag ->
                val token = readToken(tag) ?: return@ReaderCallback
                markTapDetected()
                onTokenRead(token)
            }

        private fun readToken(tag: Tag): String? {
            val isoDep = IsoDep.get(tag) ?: return null
            return try {
                isoDep.connect()
                val selectApdu = byteArrayOf(
                    0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), AID.size.toByte()
                ) + AID + byteArrayOf(0x00.toByte())
                val response = isoDep.transceive(selectApdu)
                if (response.size < 2 ||
                    response[response.lastIndex - 1] != SUCCESS_SW[0] ||
                    response[response.lastIndex] != SUCCESS_SW[1]
                ) {
                    null
                } else {
                    String(response.copyOfRange(0, response.size - 2), Charsets.UTF_8)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Unable to read NFC share token", exception)
                null
            } finally {
                runCatching { isoDep.close() }
            }
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: Received APDU")
        
        // Check if it's a SELECT APDU command
        if (commandApdu.size >= SELECT_APDU_HEADER.size + AID.size) {
            var isSelect = true
            for (i in SELECT_APDU_HEADER.indices) {
                if (commandApdu[i] != SELECT_APDU_HEADER[i]) {
                    isSelect = false
                    break
                }
            }
            
            if (isSelect) {
                val token = currentToken
                if (token != null) {
                    markTapDetected()
                    val tokenBytes = token.toByteArray(Charsets.UTF_8)
                    return tokenBytes + SUCCESS_SW
                }
            }
        }
        
        return FAILURE_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: reason = $reason")
    }
}
