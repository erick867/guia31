package com.osito.inspector;
import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

final class LogStore {
  static String target(Context c) { return c.getSharedPreferences("config",0).getString("pkg","com.nianticlabs.pokemongo"); }
  static void target(Context c,String p) { c.getSharedPreferences("config",0).edit().putString("pkg",p).apply(); }
  static File file(Context c) {
    File d=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    if(d==null)d=c.getFilesDir();
    d.mkdirs(); return new File(d,"inspector_pgsharp.txt");
  }
  static synchronized void add(Context c,String s) {
    try {
      File f=file(c);
      if(f.length()>1024*1024) { File old=new File(f.getParentFile(),"inspector_anterior.txt");if(old.exists())old.delete();f.renameTo(old); }
      String d=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.getDefault()).format(new Date());
      try(FileOutputStream o=new FileOutputStream(f,true)) { o.write(("["+d+"] "+s+"\n").getBytes(StandardCharsets.UTF_8)); }
    } catch(Exception e) {}
  }
  static synchronized String read(Context c) {
    try {
      File f=file(c);
      if(!f.exists())return "Sin eventos todavía. Activá accesibilidad y tocá Teleport.";
      byte[] b=new byte[(int)Math.min(f.length(),1048576)];
      try(FileInputStream i=new FileInputStream(f)) { int n=i.read(b);return n<0?"":new String(b,0,n,StandardCharsets.UTF_8); }
    } catch(Exception e) { return e.toString(); }
  }
  static void clear(Context c) { file(c).delete(); }
}
