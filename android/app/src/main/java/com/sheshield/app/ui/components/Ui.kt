package com.sheshield.app.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sheshield.app.R

object Ui {
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun color(c:Context,id:Int)=ContextCompat.getColor(c,id)
    fun col(c:Context,padding:Int=0)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,padding),dp(c,padding),dp(c,padding),dp(c,padding));layoutParams=ViewGroup.LayoutParams(-1,-2)}
    fun row(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=ViewGroup.LayoutParams(-1,-2)}
    fun text(c:Context,value:String,size:Int=16,bold:Boolean=false,tint:Int=R.color.on_surface)=TextView(c).apply{text=value;textSize=size.toFloat();setTextColor(color(c,tint));if(bold)setTypeface(typeface,Typeface.BOLD);setLineSpacing(dp(c,3).toFloat(),1f);layoutParams=LinearLayout.LayoutParams(-1,-2)}
    fun space(c:Context,size:Int)=View(c).apply{layoutParams=LinearLayout.LayoutParams(1,dp(c,size));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    fun card(c:Context,body:View,selected:Boolean=false)=MaterialCardView(c).apply{radius=dp(c,24).toFloat();cardElevation=dp(c,1).toFloat();setCardBackgroundColor(color(c,if(selected)R.color.selection else R.color.surface));strokeWidth=if(selected)dp(c,2) else dp(c,1);strokeColor=color(c,if(selected)R.color.purple_primary else R.color.separator);addView(body);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(c,12)}}
    fun button(c:Context,label:String,secondary:Boolean=false,danger:Boolean=false,action:()->Unit)=MaterialButton(c).apply{
        text=label;isAllCaps=false;textSize=16f;cornerRadius=dp(c,18);minHeight=dp(c,54);insetTop=0;insetBottom=0
        backgroundTintList=android.content.res.ColorStateList.valueOf(color(c,if(danger)R.color.sos_red else if(secondary)R.color.selection else R.color.purple_primary))
        setTextColor(color(c,if(secondary&&!danger)R.color.purple_primary else R.color.white));setPadding(paddingLeft,dp(c,12),paddingRight,dp(c,12));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(c,8)}
        setOnClickListener{action()}
    }
    fun sosButton(c:Context,action:()->Unit)=button(c,"SOS · Request help",danger=true,action=action).apply{
        minHeight=dp(c,60);contentDescription="Request SOS. Hold, or tap to confirm."
        setOnLongClickListener{performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);action();true}
    }
    fun icon(c:Context,drawable:Int,label:String,action:()->Unit)=ImageButton(c).apply{setImageResource(drawable);imageTintList=android.content.res.ColorStateList.valueOf(color(c,R.color.on_surface));contentDescription=label;background=GradientDrawable().apply{setColor(color(c,R.color.surface));cornerRadius=dp(c,16).toFloat()};layoutParams=LinearLayout.LayoutParams(dp(c,48),dp(c,48));setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));setOnClickListener{action()}}
    fun header(c:Context,title:String,back:(()->Unit)?=null,settings:(()->Unit)?=null):LinearLayout{
        val row=row(c);if(back!=null)row.addView(icon(c,R.drawable.ic_back,"Back",back))
        row.addView(text(c,title,24,true).apply{layoutParams=LinearLayout.LayoutParams(0,-2,1f);setPadding(dp(c,8),0,0,0)})
        if(settings!=null)row.addView(icon(c,R.drawable.ic_settings,"Settings",settings));return row
    }
    fun input(c:Context,hint:String,value:String="",phone:Boolean=false):Pair<TextInputLayout,TextInputEditText>{
        val layout=TextInputLayout(c).apply{this.hint=hint;boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;setBoxCornerRadii(dp(c,16).toFloat(),dp(c,16).toFloat(),dp(c,16).toFloat(),dp(c,16).toFloat());layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(c,12)}}
        val edit=TextInputEditText(c).apply{setText(value);setTextColor(color(c,R.color.on_surface));setSingleLine();if(phone)inputType=android.text.InputType.TYPE_CLASS_PHONE}
        layout.addView(edit);return layout to edit
    }
    fun scroll(c:Context,body:View)=ScrollView(c).apply{isFillViewport=true;clipToPadding=false;setBackgroundColor(color(c,R.color.background));addView(body);layoutParams=ViewGroup.LayoutParams(-1,-1)}
    fun badge(c:Context,label:String,tint:Int=R.color.purple_primary)=text(c,label,13,true,tint).apply{setPadding(dp(c,12),dp(c,7),dp(c,12),dp(c,7));background=GradientDrawable().apply{setColor(color(c,R.color.selection));cornerRadius=dp(c,50).toFloat()};layoutParams=LinearLayout.LayoutParams(-2,-2)}
    fun error(c:Context,message:String){MaterialAlertDialogBuilder(c).setTitle("Let's fix that").setMessage(message).setPositiveButton("Got it",null).show()}
    fun confirm(c:Context,title:String,message:String,positive:String="Continue",action:()->Unit){MaterialAlertDialogBuilder(c).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton(positive){_,_->action()}.show()}
    fun riskColor(level:String)=when(level){"LOW"->R.color.risk_low;"MEDIUM"->R.color.risk_medium;"HIGH"->R.color.risk_high;else->R.color.risk_unknown}
}
