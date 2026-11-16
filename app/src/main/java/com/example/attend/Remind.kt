package com.example.attend
import android.app.*
import android.content.*
import java.time.*

object Remind{
 private fun pi(c:Context,e:Ev,d:LocalDate)=PendingIntent.getBroadcast(c,"${e.id}|$d".hashCode(),
  Intent(c,AlarmReceiver::class.java).putExtra("t",e.title).putExtra("m",if(e.remind==0)"Starting now" else "In ${e.remind} min"),
  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun scheduleAll(c:Context){
  val am=c.getSystemService(AlarmManager::class.java); val now=LocalDateTime.now()
  Store.events(c).forEach{e->
   val t=e.time?:return@forEach; if(e.remind<0)return@forEach
   for(i in 0..14){
    val d=LocalDate.now().plusDays(i.toLong()); if(!e.occursOn(d))continue
    val fire=LocalDateTime.of(d,t).minusMinutes(e.remind.toLong()); if(fire.isBefore(now))continue
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),pi(c,e,d))
   }
  }
 }
 fun cancel(c:Context,e:Ev){
  val am=c.getSystemService(AlarmManager::class.java)
  for(i in 0..14)am.cancel(pi(c,e,LocalDate.now().plusDays(i.toLong())))
 }
}

class AlarmReceiver:BroadcastReceiver(){
 override fun onReceive(c:Context,i:Intent){
  val nm=c.getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("r","Reminders",NotificationManager.IMPORTANCE_HIGH))
  val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  nm.notify(System.nanoTime().toInt(),Notification.Builder(c,"r").setSmallIcon(android.R.drawable.ic_menu_my_calendar)
   .setContentTitle(i.getStringExtra("t")).setContentText(i.getStringExtra("m")).setContentIntent(open).setAutoCancel(true).build())
  Remind.scheduleAll(c)
 }
}
class BootReceiver:BroadcastReceiver(){ override fun onReceive(c:Context,i:Intent){Remind.scheduleAll(c)} }
