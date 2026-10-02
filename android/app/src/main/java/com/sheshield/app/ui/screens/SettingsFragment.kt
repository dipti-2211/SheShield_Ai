package com.sheshield.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sheshield.app.BuildConfig
import com.sheshield.app.R
import com.sheshield.app.ui.components.Ui

class SettingsFragment:ScreenFragment(){
    private var smsSwitch:Switch?=null
    private var updatingSms=false
    private fun reflectSms(enabled:Boolean){updatingSms=true;smsSwitch?.isChecked=enabled;updatingSms=false}
    private val smsPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->repo.prefs.edit().putBoolean("device_sms",granted).apply();reflectSms(granted);if(!granted)Ui.error(requireContext(),"Automatic SMS is unavailable. You can still use the SMS composer.")}
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val body=page("Settings")
        val appearance=Ui.col(c,20);appearance.addView(Ui.text(c,"Appearance",20,true));appearance.addView(Ui.text(c,"Follow your device, or choose your preferred look.",15,tint=R.color.on_surface_secondary));appearance.addView(Ui.button(c,"Choose appearance",true){MaterialAlertDialogBuilder(c).setTitle("Appearance").setItems(arrayOf("System","Light","Dark")){_,i->val mode=listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.MODE_NIGHT_YES)[i];repo.prefs.edit().putInt("appearance",mode).apply();AppCompatDelegate.setDefaultNightMode(mode)}.show()});body.addView(Ui.card(c,appearance))
        val safety=Ui.col(c,20);safety.addView(Ui.text(c,"Journey alerts",20,true));safety.addView(Ui.button(c,"Location and notification settings",true){startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))})
        val sms=Switch(c).apply{text="Allow automatic device SMS";isChecked=repo.prefs.getBoolean("device_sms",false);setTextColor(Ui.color(c,R.color.on_surface));minHeight=Ui.dp(c,56);setOnCheckedChangeListener{_,checked->if(!updatingSms){if(checked){reflectSms(false);Ui.confirm(c,"Enable device texting?","A live SOS can text your trusted contacts through your SIM. Your carrier's normal SMS charges may apply.","Enable"){smsPermission.launch(Manifest.permission.SEND_SMS)}}else repo.prefs.edit().putBoolean("device_sms",false).apply()}}}
        smsSwitch=sms;safety.addView(sms);safety.addView(Ui.text(c,"Rehearsal never sends texts or calls. Direct texting needs a SIM and permission. A requested call or sent text does not mean someone has acknowledged.",14,tint=R.color.on_surface_secondary));body.addView(Ui.card(c,safety))
        val privacy=Ui.col(c,20);privacy.addView(Ui.text(c,"Your privacy",20,true));privacy.addView(Ui.text(c,"Location is recorded during an active journey. Your circle receives your recorded location only during SOS. Live trip sharing uses a private link that expires in two hours. Your history can be deleted in Activity.",15,tint=R.color.on_surface_secondary));body.addView(Ui.card(c,privacy))
        val dev=Ui.col(c,20);dev.addView(Ui.text(c,"Demo connection",20,true));dev.addView(Ui.text(c,"The local API handles routes and journey state. n8n Cloud handles configured calls. Keep your laptop and callback tunnel running for the live demonstration.",15,tint=R.color.on_surface_secondary))
        val url=Ui.input(c,"API URL",repo.prefs.getString("backend_url",BuildConfig.BACKEND_BASE_URL)?:BuildConfig.BACKEND_BASE_URL);dev.addView(url.first)
        val code=Ui.input(c,"Enrollment code (leave blank to keep saved code)");code.second.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;dev.addView(code.first)
        dev.addView(Ui.button(c,"Save connection",true){runAction{if(repo.active()!=null){Ui.error(c,"End the current journey before changing its connection.");return@runAction};val target=url.second.text.toString().trim().trimEnd('/');val uri=Uri.parse(target);if(uri.scheme !in listOf("http","https")||uri.host.isNullOrBlank()){url.first.error="Enter a valid HTTP or HTTPS URL";return@runAction};if(uri.host?.endsWith(".n8n.cloud")==true){url.first.error="Use the SheShield API URL. The n8n workspace is configured on the server.";return@runAction};val entered=code.second.text.toString().trim();val savedCode=if(entered.isNotEmpty())entered else repo.prefs.getString("enrollment_code","")?:"";repo.prefs.edit().putString("backend_url",target).putString("enrollment_code",savedCode).remove("session_token").apply();Ui.error(c,"Connection saved. Check readiness to verify it.")}})
        val readiness=Ui.text(c,"Connection has not been checked.",15,tint=R.color.on_surface_secondary);dev.addView(Ui.button(c,"Check readiness",true){runAction{readiness.text="Checking…";try{val r=repo.ready();readiness.text="Routing: ${if(r.routing)"configured" else "missing key"}\nStreet reports: ${r.streetReports}\nWider-area references: ${r.evidenceContext}\nCrime coverage: requires a documented complete feed\nn8n: ${if(r.n8n)"configured" else "not configured"}\nCallbacks: ${if(r.callbacks)"configured" else "not configured"}\nCloud calls: ${if(r.liveAlerts)"enabled for approved recipients" else "disabled"}\nCloud SMS: ${if(r.cloudSms)"enabled for approved recipients" else "disabled"}\nRehearsal is available offline."}catch(e:Exception){readiness.text=com.sheshield.app.data.network.NetworkClient.message(e)}}});dev.addView(readiness)
        dev.addView(Ui.text(c,"n8n Cloud: alluvia.app.n8n.cloud\nSecrets belong in the local API environment and n8n credentials, never in this app.",13,tint=R.color.on_surface_secondary));body.addView(Ui.card(c,dev));return Ui.scroll(c,body)
    }
    override fun onDestroyView(){smsSwitch=null;super.onDestroyView()}
}
