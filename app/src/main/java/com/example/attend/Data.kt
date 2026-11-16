package com.example.attend
import android.content.Context
import org.json.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Ev(val id:String,val title:String,val date:LocalDate,val time:LocalTime?,val rep:String,val remind:Int,val end:LocalDate?=null)

object Store{
 private fun sp(c:Context)=c.getSharedPreferences("attend",0)
 fun events(c:Context):List<Ev> = runCatching{
  val a=JSONArray(sp(c).getString("ev","[]"))
  (0 until a.length()).map{ val o=a.getJSONObject(it)
   Ev(o.getString("id"),o.getString("t"),LocalDate.parse(o.getString("d")),
    o.optString("tm").takeIf{s->s.isNotEmpty()}?.let{s->LocalTime.parse(s)},o.getString("r"),o.getInt("m"),
    o.optString("e").takeIf{s->s.isNotEmpty()}?.let{s->LocalDate.parse(s)}) }
 }.getOrDefault(emptyList())
 fun saveEvents(c:Context,l:List<Ev>){
  val a=JSONArray()
  l.forEach{ a.put(JSONObject().put("id",it.id).put("t",it.title).put("d",it.date.toString()).put("tm",it.time?.toString()?:"").put("r",it.rep).put("m",it.remind).put("e",it.end?.toString()?:"")) }
  sp(c).edit().putString("ev",a.toString()).apply()
 }
 fun log(c:Context):Map<String,String> = runCatching{
  val o=JSONObject(sp(c).getString("log","{}")); o.keys().asSequence().associateWith{o.getString(it)}
 }.getOrDefault(emptyMap())
 fun saveLog(c:Context,m:Map<String,String>){ sp(c).edit().putString("log",JSONObject(m).toString()).apply() }
}

fun Ev.occursOn(d:LocalDate):Boolean{
 if(d<date||(end!=null&&d>end))return false
 return when(rep){
  "daily"->true
  "weekly"->ChronoUnit.DAYS.between(date,d)%7==0L
  "monthly"->d.dayOfMonth==minOf(date.dayOfMonth,d.lengthOfMonth())
  else->d==date }
}

object Ics{
 fun dt(v:String):Pair<LocalDate,LocalTime?>?{
  val m=Regex("(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(?:\\d{2})?)?(Z)?").matchEntire(v)?:return null
  val g=m.groupValues; val d=LocalDate.of(g[1].toInt(),g[2].toInt(),g[3].toInt())
  if(g[4].isEmpty())return d to null
  var x=LocalDateTime.of(d,LocalTime.of(g[4].toInt(),g[5].toInt()))
  if(g[6]=="Z")x=x.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
  return x.toLocalDate() to x.toLocalTime()
 }
 fun parse(txt:String,have:List<Ev>):List<Ev>{
  val out=mutableListOf<Ev>(); val cut=LocalDate.now().minusDays(30)
  txt.replace(Regex("\r?\n[ \t]"),"").split("BEGIN:VEVENT").drop(1).forEach{blk->
   val f=HashMap<String,String>()
   blk.substringBefore("END:VEVENT").lines().forEach{l->
    val i=l.indexOf(':'); if(i>0)f.putIfAbsent(l.substring(0,i).substringBefore(';').uppercase(),l.substring(i+1).trim()) }
   if(f.containsKey("RECURRENCE-ID"))return@forEach
   val s=f["DTSTART"]?:return@forEach
   val title=f["SUMMARY"]?.replace("\\,",",")?.replace("\\;",";")?:return@forEach
   val (d,t)=dt(s)?:return@forEach
   val rule=f["RRULE"]?:""
   val fr=Regex("FREQ=(\\w+)").find(rule)?.groupValues?.get(1)?.lowercase()
   val rep=if(fr!=null&&fr in listOf("daily","weekly","monthly")&&!Regex("INTERVAL=([2-9]|\\d\\d)").containsMatchIn(rule))fr else "none"
   val end=Regex("UNTIL=(\\w+)").find(rule)?.groupValues?.get(1)?.let{dt(it)?.first}
   if((rep=="none"&&d<cut)||(end!=null&&end<cut))return@forEach
   if((have+out).any{it.title==title&&it.date==d&&it.time==t})return@forEach
   out+=Ev(UUID.randomUUID().toString(),title,d,t,rep,-1,end)
  }
  return out
 }
}
