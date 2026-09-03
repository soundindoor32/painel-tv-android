package com.soundindoor.paineltv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val intencaoAbrirApp = Intent(context, MainActivity::class.java)
            intencaoAbrirApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intencaoAbrirApp)
        }
    }
}
