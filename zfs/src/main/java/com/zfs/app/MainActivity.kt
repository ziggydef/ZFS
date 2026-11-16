package com.zfs.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Bg=Color(0xFF090A10)
private val Panel=Color(0xFF171925)
private val Accent=Color(0xFF9B7BFF)
private val Muted=Color(0xFF9699AB)
private val Formats=listOf("ztxt","zdoc","zdata","zlog")

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{ZfsApp()}}
}
@Composable private fun ZfsApp(){
 val context= LocalContext.current
 var fileName by remember{mutableStateOf("my-secret")}
 var format by remember{mutableStateOf("ztxt")}
 var menu by remember{mutableStateOf(false)}
 var password by remember{mutableStateOf("")}
 var body by remember{mutableStateOf("")}
 var status by remember{mutableStateOf("Создавай и открывай зашифрованные файлы ZFS.")}
 var pending by remember{mutableStateOf<ByteArray?>(null)}
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
  if(uri==null)status="Сохранение отменено." else try{
   val bytes=pending?:error("Нет данных")
   context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:error("Нет доступа к файлу")
   status="ZFS-файл успешно создан."
  }catch(e:Exception){status="Ошибка сохранения: "+(e.message?:"ошибка")}
 }
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri==null)status="Открытие отменено." else try{
   val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Не удалось прочитать")
   val result=Zfs.decrypt(bytes,password);format=result.first;body=result.second;fileName="opened-file"
   status="Файл расшифрован и проверен: ."+format
  }catch(e:Exception){status="Не удалось открыть: проверь пароль, файл и его целостность."}
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel)){
  Surface(Modifier.fillMaxSize(),color=Bg){
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
     Icon(Icons.Rounded.Security,null,tint=Accent)
     Column{Text("ZFS VAULT",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black);Text("PRIVATE FILE ECOSYSTEM · 0.1",color=Muted,fontSize=10.sp)}
    }
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)){
     Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
      Text("Создать защищённый файл",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
      Text("Данные шифруются до записи. Простое переименование не откроет содержимое.",color=Muted,fontSize=12.sp)
      OutlinedTextField(fileName,{fileName=it},Modifier.fillMaxWidth(),label={Text("Имя файла")},singleLine=true)
      Box{TextButton(onClick={menu=true}){Text("Формат: ."+format+"  ▾",color=Accent)}
       DropdownMenu(expanded=menu,onDismissRequest={menu=false}){Formats.forEach{f->DropdownMenuItem(text={Text("."+f)},onClick={ format=f; menu=false })}}
      }
      OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("Пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
      OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().height(180.dp),label={Text("Содержимое")},minLines=5)
      Button(onClick={
       try{require(fileName.isNotBlank()){"Укажи имя"};require(password.length>=12){"Используй пароль минимум из 12 символов"}
        pending=Zfs.encrypt(format,body,password)
        val safe=fileName.trim().replace(Regex("[^A-Za-z0-9._-]"),"_").ifBlank{"zfs-file"}
        save.launch(safe+"."+format);status="Подготовлен зашифрованный контейнер."
       }catch(e:Exception){status=e.message?:"Не удалось создать файл"}
      },Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){
       Icon(Icons.Rounded.Lock,null);Text("Создать ZFS-файл")
      }
     }
    }
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)){
     Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
      Text("Открыть существующий файл",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
      Text("Выбери контейнер и введи пароль, использованный при создании.",color=Muted,fontSize=12.sp)
      Button(onClick={open.launch(arrayOf("*/*"))},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF303448))){
       Icon(Icons.Rounded.LockOpen,null);Text("Выбрать файл ZFS")
      }
     }
    }
    Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){
     Text("Состояние",color=Accent,fontWeight=FontWeight.Bold);Spacer(Modifier.height(5.dp));Text(status,color=Color.White,fontSize=13.sp)
    }}
    Text("ZFSF v1 · AES-256-GCM · PBKDF2-HMAC-SHA256 · 600 000 iterations",color=Muted,fontSize=10.sp)
   }
  }
 }
}
private object Zfs{
 private val magic=byteArrayOf(0x5A,0x46,0x53,0x46)
 private const val version=1
 private const val rounds=600_000
 private const val headerSize=38
 private val random=SecureRandom()
 fun encrypt(format:String,text:String,password:String):ByteArray{
  require(format in Formats){"Неизвестный тип"}
  val type=Formats.indexOf(format)+1
  val salt=ByteArray(16).also{random.nextBytes(it)}
  val iv=ByteArray(12).also{random.nextBytes(it)}
  val header=ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN).put(magic).put(version.toByte()).put(type.toByte()).putInt(rounds).put(salt).put(iv).array()
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,key(password,salt,rounds),GCMParameterSpec(128,iv));cipher.updateAAD(header)
  return header+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
 }
 fun decrypt(data:ByteArray,password:String):Pair<String,String>{
  require(data.size>headerSize+16){"Файл слишком короткий"}
  val b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);val m=ByteArray(4);b.get(m)
  require(m.contentEquals(magic)){"Это не контейнер ZFSF"}
  require(b.get().toInt()==version){"Версия не поддерживается"}
  val type=b.get().toInt();require(type in 1..Formats.size){"Неизвестный тип"}
  val n=b.int;require(n in 100_000..2_000_000){"Некорректные параметры KDF"}
  val salt=ByteArray(16).also{b.get(it)};val iv=ByteArray(12).also{b.get(it)}
  val header=data.copyOfRange(0,headerSize);val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,key(password,salt,n),GCMParameterSpec(128,iv));cipher.updateAAD(header)
  return Formats[type-1] to String(cipher.doFinal(data.copyOfRange(headerSize,data.size)),Charsets.UTF_8)
 }
 private fun key(password:String,salt:ByteArray,n:Int):SecretKeySpec{
  val spec=PBEKeySpec(password.toCharArray(),salt,n,256)
  return try{SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")}finally{spec.clearPassword()}
 }
}
