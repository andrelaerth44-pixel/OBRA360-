package com.obra360

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.math.ceil

private val Cream=Color(0xFFF8F7F3)
private val Ink=Color(0xFF17201B)
private val Surface=Color(0xFFFFFFFF)
private val Green=Color(0xFF315C4A)
private val Line=Color(0xFFE5E2DA)

data class Work(val id:String,val name:String,val location:String,val progress:Int,val status:String)
data class Post(val id:String,val text:String,val author:String,val created:String)

class Store(context:Context){
    private val p=context.getSharedPreferences("obra360",Context.MODE_PRIVATE)
    var userId:String? get()=p.getString("uid",null); set(v){p.edit().putString("uid",v).apply()}
    var email:String? get()=p.getString("email",null); set(v){p.edit().putString("email",v).apply()}
    var name:String get()=p.getString("name","Profissional") ?: "Profissional"; set(v){p.edit().putString("name",v).apply()}
    fun clear(){p.edit().clear().apply()}
}

class Api(private val store:Store){
    private val client=OkHttpClient()
    private val base="https://fmqdyaoqttvubynmjbxt.supabase.co"
    private val key="sb_publishable_cQwCwWG9t7NncfJXpF6tcg_AsKPktmL"
    private fun request(url:String,method:String="GET",body:String?=null,auth:Boolean=true):String{
        val b=Request.Builder().url(url).header("apikey",key).header("Content-Type","application/json")
        if(auth && store.userId!=null) b.header("Authorization","Bearer ${store.userId}")
        if(body!=null) b.method(method,body.toRequestBody("application/json".toMediaType()))
        else b.method(method,null)
        client.newCall(b.build()).execute().use{r-> if(!r.isSuccessful) throw IOException("HTTP ${r.code}"); return r.body?.string().orEmpty()}
    }
    suspend fun signUp(email:String,password:String,name:String):Boolean=withContext(Dispatchers.IO){
        val body=JSONObject().put("email",email).put("password",password).toString()
        val raw=request("$base/auth/v1/signup","POST",body,false)
        val o=JSONObject(raw)
        val id=o.optString("id").ifBlank{ o.optJSONObject("user")?.optString("id").orEmpty() }
        if(id.isBlank()) false else {store.userId=id;store.email=email;store.name=name;true}
    }
    suspend fun signIn(email:String,password:String):Boolean=withContext(Dispatchers.IO){
        val body=JSONObject().put("email",email).put("password",password).toString()
        val raw=request("$base/auth/v1/token?grant_type=password","POST",body,false)
        val o=JSONObject(raw); val token=o.optString("access_token"); val user=o.optJSONObject("user")
        if(token.isBlank()||user==null) false else {store.userId=token;store.email=user.optString("email",email);store.name=user.optJSONObject("user_metadata")?.optString("name","Profissional")?:"Profissional";true}
    }
    suspend fun listWorks():List<Work>=withContext(Dispatchers.IO){
        if(store.userId==null)return@withContext emptyList()
        runCatching{JSONArray(request("$base/rest/v1/obra_works?select=*&order=created_at.desc")).let{a->List(a.length()){i->val o=a.getJSONObject(i);Work(o.optString("id"),o.optString("name"),o.optString("location"),o.optInt("progress"),o.optString("status"))}}}.getOrDefault(emptyList())
    }
    suspend fun createWork(name:String,location:String):Boolean=withContext(Dispatchers.IO){
        if(store.userId==null)return@withContext false
        runCatching{val body=JSONObject().put("owner_id",store.userId).put("name",name).put("location",location).put("progress",0).put("status","planejamento").toString();request("$base/rest/v1/obra_works","POST",body);true}.getOrDefault(false)
    }
    suspend fun listPosts():List<Post>=withContext(Dispatchers.IO){
        runCatching{JSONArray(request("$base/rest/v1/obra_posts?select=*&order=created_at.desc")).let{a->List(a.length()){i->val o=a.getJSONObject(i);Post(o.optString("id"),o.optString("body"),o.optString("author_name","Profissional"),o.optString("created_at"))}}}.getOrDefault(emptyList())
    }
    suspend fun createPost(text:String):Boolean=withContext(Dispatchers.IO){
        if(store.userId==null)return@withContext false
        runCatching{val body=JSONObject().put("author_id",store.userId).put("body",text).put("author_name",store.name).toString();request("$base/rest/v1/obra_posts","POST",body);true}.getOrDefault(false)
    }
}

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);val store=Store(this);setContent{ObraTheme{ObraApp(store)}}}
}

@Composable fun ObraTheme(content:@Composable()->Unit){
    MaterialTheme(colorScheme=lightColorScheme(primary=Green,background=Cream,surface=Surface,onSurface=Ink),content=content)
}

@Composable fun ObraApp(store:Store){
    var logged by remember{mutableStateOf(store.userId!=null)}
    if(!logged){LoginScreen(store){logged=true};return}
    var tab by remember{mutableStateOf(0)}
    Scaffold(containerColor=Cream,bottomBar={
        NavigationBar(containerColor=Surface){
            listOf(Icons.Default.Home to "Início",Icons.Default.Groups to "Comunidade",Icons.Default.AddCircle to "Criar",Icons.Default.Construction to "Obras",Icons.Default.Person to "Perfil").forEachIndexed{i,(icon,label)->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,null)},label={Text(label)})
            }
        }
    }){pad->Box(Modifier.padding(pad).fillMaxSize()){when(tab){0->Home(store);1->Community(store);2->Create(store);3->Works(store);4->Profile(store)}}}
}

@Composable fun LoginScreen(store:Store,onDone:()->Unit){
    var signup by remember{mutableStateOf(false)};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Cream).padding(28.dp),verticalArrangement=Arrangement.Center){
        Text("OBRA360",fontSize=34.sp,fontWeight=FontWeight.Bold,color=Green)
        Text(if(signup)"Crie o seu acesso profissional" else "Gestão de obras e comunidade",color=Color.Gray)
        Spacer(Modifier.height(28.dp))
        if(signup)Field("Nome",name){name=it}
        Field("E-mail",email){email=it}
        Field("Palavra-passe",pass){pass=it}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick={busy=true;scope.launch{val ok=if(signup)Api(store).signUp(email,pass,name) else Api(store).signIn(email,pass);busy=false;if(ok)onDone() else error="Não foi possível autenticar. Verifique os dados e tente novamente."}},enabled=!busy,modifier=Modifier.fillMaxWidth().height(52.dp)){Text(if(signup)"Criar conta" else "Entrar")}
        TextButton(onClick={signup=!signup}){Text(if(signup)"Já tenho uma conta" else "Criar nova conta")}
    }
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),singleLine=true)}

@Composable fun Home(store:Store){
    Column(Modifier.fillMaxSize().padding(20.dp)){Text("Olá, ${store.name}",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Tudo o que precisa para acompanhar a obra.",color=Color.Gray);Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Obras","Gestão");Metric("Etapas","Progresso");Metric("Medições","Campo")}
        Spacer(Modifier.height(22.dp));Text("Ferramentas",fontWeight=FontWeight.Bold,fontSize=20.sp);Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(18.dp)){Text("Calculadoras de construção",fontWeight=FontWeight.SemiBold);Text("Concreto, aço, área, volume e orçamento rápido.",color=Color.Gray)}}
        Spacer(Modifier.height(12.dp));Text("Acesso rápido",fontWeight=FontWeight.Bold,fontSize=20.sp)
        Quick("Diário de obra","Registe acontecimentos e evidências do dia.")
        Quick("Problemas","Acompanhe pendências, riscos e soluções.")
        Quick("Documentos","Mantenha projetos e PDFs ligados à obra.")
    }
}
@Composable fun Metric(a:String,b:String){Card(Modifier.weight(1f),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(14.dp)){Text(a,fontWeight=FontWeight.Bold);Text(b,color=Color.Gray)}}}
@Composable fun Quick(a:String,b:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ChevronRight,null,tint=Green);Column{Text(a,fontWeight=FontWeight.SemiBold);Text(b,color=Color.Gray,fontSize=13.sp)}}}

@Composable fun Works(store:Store){
    var works by remember{mutableStateOf<List<Work>>(emptyList())};var show by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var loc by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){works=Api(store).listWorks()}
    Column(Modifier.fillMaxSize().padding(20.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Obras",fontSize=28.sp,fontWeight=FontWeight.Bold);IconButton({show=true}){Icon(Icons.Default.Add,null)}}
        if(works.isEmpty())Empty("Ainda não existem obras","Crie a primeira obra para começar o acompanhamento.")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(works){w->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(18.dp)){Text(w.name,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(w.location,color=Color.Gray);Spacer(Modifier.height(10.dp));LinearProgressIndicator({w.progress/100f},Modifier.fillMaxWidth());Text("${w.progress}% • ${w.status}",fontSize=12.sp,color=Color.Gray)}}}}
    }
    if(show)AlertDialog(onDismissRequest={show=false},title={Text("Nova obra")},text={Column{Field("Nome",name){name=it};Field("Localização",loc){loc=it}}},confirmButton={TextButton(onClick={scope.launch{if(Api(store).createWork(name,loc)){works=Api(store).listWorks();name="";loc="";show=false}}}){Text("Criar")}},dismissButton={TextButton({show=false}){Text("Cancelar")}})
}
@Composable fun Community(store:Store){
    var posts by remember{mutableStateOf<List<Post>>(emptyList())};LaunchedEffect(Unit){posts=Api(store).listPosts()}
    Column(Modifier.fillMaxSize().padding(20.dp)){Text("Comunidade",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Obras, experiências e conhecimento.",color=Color.Gray);Spacer(Modifier.height(16.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(posts){p->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(18.dp)){Text(p.author,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(p.text);Spacer(Modifier.height(8.dp));Row{Icon(Icons.Default.FavoriteBorder,null);Spacer(Modifier.width(18.dp));Icon(Icons.Default.ModeComment,null);Spacer(Modifier.width(18.dp));Icon(Icons.Default.BookmarkBorder,null)}}}}}}
}
@Composable fun Create(store:Store){
    var text by remember{mutableStateOf("")};var sent by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp)){Text("Criar",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Partilhe evolução real da obra.",color=Color.Gray);Spacer(Modifier.height(18.dp));OutlinedTextField(text,{text=it},label={Text("O que está acontecendo na obra?")},modifier=Modifier.fillMaxWidth().height(150.dp));Spacer(Modifier.height(12.dp));Button(onClick={scope.launch{sent=Api(store).createPost(text);if(sent)text=""}},modifier=Modifier.fillMaxWidth()){Text("Publicar")};if(sent)Text("Publicado.",color=Green,modifier=Modifier.padding(top=10.dp))}
}
@Composable fun Profile(store:Store){
    Column(Modifier.fillMaxSize().padding(20.dp)){Text("Perfil",fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(20.dp)){Text(store.name,fontWeight=FontWeight.Bold,fontSize=22.sp);Text(store.email.orEmpty(),color=Color.Gray);Text("Profissional de construção",color=Green,modifier=Modifier.padding(top=4.dp))}};Spacer(Modifier.height(18.dp));Text("Ferramentas",fontWeight=FontWeight.Bold,fontSize=19.sp);Spacer(Modifier.height(8.dp));CalculatorCard();Spacer(Modifier.height(20.dp));OutlinedButton(onClick={store.clear()}){Text("Terminar sessão")}}
}
@Composable fun CalculatorCard(){
    var length by remember{mutableStateOf("")};var width by remember{mutableStateOf("")};var height by remember{mutableStateOf("")};var result by remember{mutableStateOf("")}
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(18.dp)){Text("Calculadora de concreto",fontWeight=FontWeight.Bold);Field("Comprimento (m)",length){length=it};Field("Largura (m)",width){width=it};Field("Altura (m)",height){height=it};Button(onClick={val v=(length.toDoubleOrNull()?:0.0)*(width.toDoubleOrNull()?:0.0)*(height.toDoubleOrNull()?:0.0);result="${"%.2f".format(v)} m³ de volume"} ){Text("Calcular")};if(result.isNotBlank())Text(result,fontWeight=FontWeight.Bold,color=Green,modifier=Modifier.padding(top=8.dp))}}
}
@Composable fun Empty(title:String,text:String){Column(Modifier.fillMaxWidth().padding(top=60.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Architecture,null,tint=Green,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(title,fontWeight=FontWeight.Bold);Text(text,color=Color.Gray)}}
