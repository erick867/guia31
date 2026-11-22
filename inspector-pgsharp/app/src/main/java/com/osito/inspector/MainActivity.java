package com.osito.inspector;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private EditText pkg;
  private TextView status, log;
  int d(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  TextView text(String v,int size) { TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(Color.rgb(25,45,65));t.setPadding(0,d(8),0,d(8));return t; }
  void btn(LinearLayout col,String label,View.OnClickListener action) { Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(action);col.addView(b); }
  public void onCreate(Bundle b) {
    super.onCreate(b);
    ScrollView sc=new ScrollView(this);LinearLayout col=new LinearLayout(this);col.setOrientation(1);
    col.setBackgroundColor(Color.rgb(247,250,253));col.setPadding(d(18),d(20),d(18),d(30));sc.addView(col);setContentView(sc);
    col.addView(text("🐼 Inspector PGSharp",25));
    col.addView(text("Detecta eventos, IDs y controles que Android expone. No puede ver directamente los métodos internos de otra app.",14));
    status=text("",17);col.addView(status);
    btn(col,"1. Activar servicio en Accesibilidad",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    col.addView(text("Paquete de la aplicación a inspeccionar:",14));
    pkg=new EditText(this);pkg.setSingleLine(true);pkg.setText(LogStore.target(this));col.addView(pkg);
    btn(col,"2. Guardar paquete",v->{
      String p=pkg.getText().toString().trim();
      if(!p.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) {Toast.makeText(this,"Paquete incorrecto",1).show();return;}
      LogStore.target(this,p);LogStore.add(this,"Objetivo: "+p);refresh();
    });
    col.addView(text("3. Abrí PGSharp, pulsá Teleport y luego el botón flotante SCAN. Después volvé acá y exportá el registro.",14));
    btn(col,"Actualizar eventos",v->refresh());
    btn(col,"Exportar registro TXT",v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"inspector_pgsharp.txt");startActivityForResult(i,42);});
    btn(col,"Compartir registro",v->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,LogStore.read(this));startActivity(Intent.createChooser(i,"Enviar diagnóstico"));});
    btn(col,"Borrar registro local",v->new AlertDialog.Builder(this).setTitle("Borrar registro").setPositiveButton("Borrar",(a,z)->{LogStore.clear(this);refresh();}).setNegativeButton("Cancelar",null).show());
    col.addView(text("Últimos eventos:",18));
    log=text("",12);log.setTextIsSelectable(true);log.setTypeface(android.graphics.Typeface.MONOSPACE);col.addView(log);
    col.addView(text("Privacidad: sin permiso de Internet. Los datos permanecen en tu teléfono, salvo que vos los exportes.",12));
    refresh();
  }
  void refresh(){
    if(status==null)return;
    String en=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    boolean active=en!=null&&en.toLowerCase().contains("com.osito.inspector");
    status.setText(active?"● Inspector activo":"○ Activá Inspector en Accesibilidad");
    if(log!=null){String s=LogStore.read(this);log.setText(s.length()>20000?s.substring(s.length()-20000):s);}
  }
  protected void onResume(){super.onResume();refresh();}
  protected void onActivityResult(int r,int code,Intent data){
    super.onActivityResult(r,code,data);
    if(r==42 && code==RESULT_OK && data!=null) {
      Uri u=data.getData();if(u==null)return;
      try(OutputStream o=getContentResolver().openOutputStream(u)){
        if(o!=null)o.write(LogStore.read(this).getBytes(StandardCharsets.UTF_8));
        Toast.makeText(this,"Registro exportado",0).show();
      }catch(Exception e){Toast.makeText(this,"Error: "+e.getMessage(),1).show();}
    }
  }
}
