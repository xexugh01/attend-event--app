package com.example.attend
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33)registerForActivityResult(ActivityResultContracts.RequestPermission()){}.launch(Manifest.permission.POST_NOTIFICATIONS)
  setContent{ MaterialTheme(colorScheme=if(isSystemInDarkTheme())darkColorScheme() else lightColorScheme()){ Surface(Modifier.fillMaxSize()){ App(this) } } }
  Remind.scheduleAll(this)
 }
}

fun fmt(d:LocalDate,p:String):String=d.format(DateTimeFormatter.ofPattern(p))

@Composable fun App(c:Context){
 var events by remember{mutableStateOf(Store.events(c))}
 var log by remember{mutableStateOf(Store.log(c))}
 var view by remember{mutableStateOf("Week")}
 var anchor by remember{mutableStateOf(LocalDate.now())}
 var adding by remember{mutableStateOf(false)}
 var del by remember{mutableStateOf<Ev?>(null)}
 var msg by remember{mutableStateOf("")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->
  if(u!=null){
   val r=Ics.parse(c.contentResolver.openInputStream(u)!!.bufferedReader().readText(),events)
   events=events+r; Store.saveEvents(c,events); Remind.scheduleAll(c); msg="Imported ${r.size}"
  }
 }
 val from=when(view){"Day"->anchor;"Week"->anchor.with(DayOfWeek.MONDAY);else->anchor.withDayOfMonth(1)}
 val to=when(view){"Day"->anchor;"Week"->from.plusDays(6);else->from.withDayOfMonth(from.lengthOfMonth())}
 val label=when(view){"Day"->fmt(from,"EEEE d MMM");"Week"->fmt(from,"d MMM")+" – "+fmt(to,"d MMM");else->fmt(from,"MMMM yyyy")}
 fun step(n:Long){anchor=when(view){"Day"->anchor.plusDays(n);"Week"->anchor.plusWeeks(n);else->anchor.plusMonths(n)}}
 fun mark(e:Ev,d:LocalDate,s:String){val k="${e.id}|$d";log=if(log[k]==s)log-k else log+(k to s);Store.saveLog(c,log)}
 val rows=generateSequence(from){it.plusDays(1)}.takeWhile{!it.isAfter(to)}.toList()
  .map{d->d to events.filter{it.occursOn(d)}.sortedBy{it.time}}.filter{it.second.isNotEmpty()}
 var a=0;var m=0;var u=0
 rows.forEach{(d,l)->l.forEach{when(log["${it.id}|$d"]){"a"->a++;"m"->m++;else->u++}}}

 Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("Day","Week","Month").forEach{v->
    if(v==view)Button({},Modifier.weight(1f)){Text(v)} else OutlinedButton({view=v},Modifier.weight(1f)){Text(v)} }
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   TextButton({step(-1)}){Text("‹")}
   Text(label,Modifier.weight(1f),textAlign=TextAlign.Center)
   TextButton({step(1)}){Text("›")}
  }
  Card(Modifier.fillMaxWidth()){Text("Went $a · Missed $m · Unmarked $u · Rate ${if(a+m>0)a*100/(a+m) else 0}%",Modifier.padding(12.dp))}
  LazyColumn(Modifier.weight(1f).padding(top=8.dp)){
   if(rows.isEmpty())item{Text("No events in this period.",Modifier.padding(16.dp))}
   items(rows){(d,l)->
    Text(fmt(d,"EEE d MMM"),Modifier.padding(top=10.dp),style=MaterialTheme.typography.labelLarge)
    l.forEach{e->
     val st=log["${e.id}|$d"]
     Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){
      Text(e.title+(e.time?.let{" · $it"}?:"")+(if(e.rep!="none")" · ${e.rep}" else ""))
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
       if(st=="a")Button({mark(e,d,"a")}){Text("✓ Went")} else OutlinedButton({mark(e,d,"a")}){Text("✓ Went")}
       if(st=="m")Button({mark(e,d,"m")}){Text("✗ Missed")} else OutlinedButton({mark(e,d,"m")}){Text("✗ Missed")}
       TextButton({del=e}){Text("Delete")}
      }
     }
    }
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
   Button({adding=true}){Text("+ Add")}
   OutlinedButton({picker.launch(arrayOf("*/*"))}){Text("Import .ics")}
   Text(msg)
  }
 }
 if(adding)AddDialog(c){ev->
  adding=false
  if(ev!=null){events=events+ev;Store.saveEvents(c,events);Remind.scheduleAll(c)}
 }
 del?.let{e->
  AlertDialog(onDismissRequest={del=null},title={Text("Delete “${e.title}”?")},text={Text("This also removes its history.")},
   confirmButton={TextButton({
    Remind.cancel(c,e);events=events.filter{it.id!=e.id};log=log.filterKeys{!it.startsWith(e.id+"|")}
    Store.saveEvents(c,events);Store.saveLog(c,log);del=null}){Text("Delete")}},
   dismissButton={TextButton({del=null}){Text("Cancel")}})
 }
}

@Composable fun AddDialog(c:Context,onDone:(Ev?)->Unit){
 var t by remember{mutableStateOf("")}
 var d by remember{mutableStateOf(LocalDate.now())}
 var tm by remember{mutableStateOf<LocalTime?>(null)}
 var r by remember{mutableStateOf(0)}
 var rm by remember{mutableStateOf(0)}
 val reps=listOf("none","daily","weekly","monthly"); val rms=listOf(-1,0,10,30,60,1440)
 AlertDialog(onDismissRequest={onDone(null)},title={Text("Add event")},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(t,{t=it},label={Text("Event name")},singleLine=true)
   OutlinedButton({DatePickerDialog(c,{_,y,mo,dd->d=LocalDate.of(y,mo+1,dd)},d.year,d.monthValue-1,d.dayOfMonth).show()}){Text("Date: $d")}
   OutlinedButton({TimePickerDialog(c,{_,h,mi->tm=LocalTime.of(h,mi)},9,0,true).show()}){Text("Time: ${tm?.toString()?:"none"}")}
   OutlinedButton({r=(r+1)%reps.size}){Text("Repeat: ${reps[r]}")}
   if(tm!=null)OutlinedButton({rm=(rm+1)%rms.size}){Text("Reminder: "+if(rms[rm]<0)"none" else "${rms[rm]} min before")}
  }},
  confirmButton={TextButton({if(t.isNotBlank())onDone(Ev(UUID.randomUUID().toString(),t.trim(),d,tm,reps[r],if(tm==null)-1 else rms[rm]))}){Text("Add")}},
  dismissButton={TextButton({onDone(null)}){Text("Cancel")}})
}
