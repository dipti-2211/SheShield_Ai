function recordedTime(location){
 const value=location?.timestamp_ms;
 if(!Number.isFinite(value)||value<=0)return null;
 const date=new Date(value);
 return Number.isFinite(date.getTime())?date:null;
}
function spokenAge(location,now){
 const recorded=recordedTime(location);
 if(!recorded||recorded.getTime()>now+30000)return 'We do not know when that position was recorded.';
 const minutes=Math.floor(Math.max(0,now-recorded.getTime())/60000);
 if(minutes<1)return 'It was recorded less than a minute ago.';
 const [amount,unit]=minutes<60?[minutes,'minute']:minutes<1440?[Math.floor(minutes/60),'hour']:[Math.floor(minutes/1440),'day'];
 return `It was recorded ${amount} ${unit}${amount===1?'':'s'} ago.`;
}
export function voiceAlertMessage(incident,now=Date.now()){
 const loc=incident.location;
 const locationText=loc?`Their last recorded position was latitude ${loc.latitude}, longitude ${loc.longitude}. ${spokenAge(loc,now)}`:'';
 return [
  `SheShield alert. Your contact ${incident.trigger==='TIMEOUT'?'missed a safety check-in':'requested help'}.`,
  locationText,
  'Please call them now. Press 1 to acknowledge you have received this alert.'
 ].filter(Boolean).join(' ');
}
export function smsAlertMessage(incident){
 const loc=incident.location,recorded=recordedTime(loc);
 const locationText=loc?` Last recorded position (${recorded?recorded.toISOString():'recording time unknown'}): https://maps.google.com/?q=${loc.latitude},${loc.longitude}`:' No location was shared with this alert.';
 return 'SheShield SOS: Your contact '+(incident.trigger==='TIMEOUT'?'missed a safety check-in.':'requested help.')+locationText+' Please call your contact. SMS delivery does not confirm they are safe.';
}
