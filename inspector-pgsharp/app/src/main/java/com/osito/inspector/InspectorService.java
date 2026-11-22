package com.osito.inspector;
import android.accessibilityservice.*;
import android.view.accessibility.*;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.*;
import android.widget.*;
import android.os.*;
import android.hardware.HardwareBuffer;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.util.Locale;

public class InspectorService extends AccessibilityService {
  private WindowManager wm;
  private TextView bubble, pinButton;
  private long pinClickStarted=0;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private long lastChanged=0;
  private static final int LIMIT=180;
  public void onServiceConnected(){
    super.onServiceConnected();
    wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    AccessibilityServiceInfo i=getServiceInfo();
    if(i!=null){
      i.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
      i.eventTypes=AccessibilityEvent.TYPE_VIEW_CLICKED | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
      setServiceInfo(i);
    }
    LogStore.add(this,"Servicio Inspector activo para "+LogStore.target(this));
  }
  public void onAccessibilityEvent(AccessibilityEvent e){
    if(e==null)return;
    String p=e.getPackageName()==null?"":e.getPackageName().toString();
    int type=e.getEventType();
    if(!LogStore.target(this).equals(p)){
      if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!p.equals(getPackageName()))hide();
      return;
    }
    show();
    if(pinClickStarted>0 && System.currentTimeMillis()-pinClickStarted<4000
      && type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
      && String.valueOf(e.getClassName()).contains("AlertDialog")){
        AccessibilityNodeInfo dialog=getRootInActiveWindow();
        String title = findTitle(dialog,0);
        LogStore.add(this,"PIN resultado: ventana AlertDialog abierta; titulo="+title);
        pinClickStarted=0;
    }
    if(type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
      long t=System.currentTimeMillis();
      if(t-lastChanged<1400)return;lastChanged=t;
    }
    StringBuilder s=new StringBuilder("EVENTO "+AccessibilityEvent.eventTypeToString(type)+" paquete="+p+" clase="+safe(e.getClassName()));
    AccessibilityNodeInfo n=e.getSource();
    if(n!=null) {
      s.append("\nCONTROL: ").append(desc(n));
      for(int depth=0;depth<4;depth++) { n=n.getParent();if(n==null)break;s.append("\nPADRE ").append(depth+1).append(": ").append(desc(n)); }
    }else s.append("\nAndroid no expuso el nodo ni su ID.");
    LogStore.add(this,s.toString());
  }
  private String safe(CharSequence c){if(c==null)return "-";String s=c.toString().replace('\n',' ').replace('\r',' ');return s.length()>130?s.substring(0,130):s;}
  private String desc(AccessibilityNodeInfo n) {
    Rect r=new Rect();n.getBoundsInScreen(r);
    String id=safe(n.getViewIdResourceName());
    boolean secret=n.isPassword() || id.toLowerCase(Locale.ROOT).matches(".*(password|passwd|pin|otp|token|secret).*");
    StringBuilder s=new StringBuilder("clase="+safe(n.getClassName())+" id="+id+" texto="+(secret?"[OCULTO]":safe(n.getText()))+" descripcion="+(secret?"[OCULTA]":safe(n.getContentDescription()))+" area="+r.toShortString()+" pulsable="+n.isClickable());
    for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())s.append(" accion=").append(a.getId()).append(":").append(safe(a.getLabel()));
    return s.toString();
  }
  int d(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

  private boolean hasViewId(AccessibilityNodeInfo n,String id,int depth){
    if(n==null||depth>18)return false;
    if(id.equals(n.getViewIdResourceName()))return true;
    for(int i=0;i<n.getChildCount();i++)
      if(hasViewId(n.getChild(i),id,depth+1))return true;
    return false;
  }
  private String findTitle(AccessibilityNodeInfo n,int depth){
    if(n==null || depth>16)return "no detectado";
    if("android:id/alertTitle".equals(n.getViewIdResourceName()))return safe(n.getText());
    for(int i=0;i<n.getChildCount();i++){
      String t=findTitle(n.getChild(i),depth+1);
      if(!"no detectado".equals(t))return t;
    }
    return "no detectado";
  }
  private AccessibilityNodeInfo findPin(AccessibilityNodeInfo n,int depth,int[] seen){
    if(n==null || depth>22 || ++seen[0]>1000)return null;
    CharSequence p=n.getPackageName();
    if(p!=null && LogStore.target(this).equals(p.toString())
       && n.isClickable() && n.isEnabled() && "android.widget.LinearLayout".contentEquals(n.getClassName())){
       Rect b=new Rect();n.getBoundsInScreen(b);
       android.util.DisplayMetrics m=getResources().getDisplayMetrics();
       float cx=b.centerX()/(float)m.widthPixels;
       float bw=b.width()/(float)m.widthPixels,bh=b.height()/(float)m.heightPixels;
       // El boton Teleport es el SEXTO elemento del panel (indice 5).
       // Probar su posicion DENTRO de la barra, no el porcentaje total de pantalla.
       // La prueba anterior seleccionaba el CUARTO elemento (Mapa).
       if(cx>0.02f && cx<0.11f && bw>0.04f && bw<0.13f && bh>0.02f && bh<0.06f){
          AccessibilityNodeInfo parent=n.getParent();
          if(parent!=null && "android.widget.LinearLayout".contentEquals(parent.getClassName())){
            Rect a=new Rect();parent.getBoundsInScreen(a);
            float relativeY=a.height()>0?(b.centerY()-a.top)/(float)a.height():-1;
            float relativeH=a.height()>0?b.height()/(float)a.height():-1;
            if(a.height()>(int)(m.heightPixels*.2f)
              && Math.abs(a.centerX()-b.centerX())<m.widthPixels*.04f
              && relativeY>0.52f && relativeY<0.58f
              && relativeH>0.06f && relativeH<0.14f){
              LogStore.add(this,"TELEPORT: sexto control encontrado (relY="+relativeY+") "+desc(n)+" PADRE "+desc(parent));
              return n;
            }
          }
       }
    }
    for(int i=0;i<n.getChildCount();i++){
       AccessibilityNodeInfo child=findPin(n.getChild(i),depth+1,seen);
       if(child!=null)return child;
    }
    return null;
  }
  private void testPin(){
    AccessibilityNodeInfo candidate=findPin(getRootInActiveWindow(),0,new int[1]);
    if(candidate==null){
      java.util.List<AccessibilityWindowInfo> windows=getWindows();
      if(windows!=null)for(AccessibilityWindowInfo w:windows){
        if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
        AccessibilityNodeInfo root=w.getRoot();
        candidate=findPin(root,0,new int[1]);
        if(candidate!=null)break;
      }
    }
    if(candidate==null){
      LogStore.add(this,"PIN: no se encontró el control del panel con los datos de la prueba anterior.");
      Toast.makeText(this,"Pin no encontrado: usá SCAN",Toast.LENGTH_LONG).show();
      return;
    }
    try {
       boolean clicked=candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
       LogStore.add(this,"TELEPORT ACTION_CLICK="+clicked+"; esperando verificar titulo correcto...");
       if(clicked){
         pinClickStarted=System.currentTimeMillis();
         handler.postDelayed(()->{
            AccessibilityNodeInfo root=getRootInActiveWindow();
            String title=findTitle(root,0);
            if("Teletransportarse".equalsIgnoreCase(title)){
               LogStore.add(this,"RESULTADO TELEPORT: CORRECTO, ventana Teletransportarse detectada.");
               Toast.makeText(this,"TELEPORT correcto",Toast.LENGTH_LONG).show();
            }else if(hasViewId(root,"me.underw.hp:id/hl_mbmap_layout",0)){
               LogStore.add(this,"RESULTADO TELEPORT: ERROR, se abrio Mapa, no Teletransportarse.");
               Toast.makeText(this,"Se abrio Mapa, revisar log",Toast.LENGTH_LONG).show();
            }else{
               LogStore.add(this,"RESULTADO TELEPORT: ventana sin confirmar; titulo="+title);
            }
         },850);
       }
       Toast.makeText(this,clicked?"Toque enviado al sexto boton":"No fue posible pulsar Teleport",Toast.LENGTH_SHORT).show();
    }catch(Exception ex){LogStore.add(this,"PIN error al pulsar: "+ex);Toast.makeText(this,"Error al pulsar PIN",Toast.LENGTH_SHORT).show();}
  }

  private void show(){
    if(bubble!=null || wm==null)return;
    handler.post(()->{
      if(bubble!=null)return;
      try{
        TextView b=new TextView(this);
        b.setText("SCAN");b.setTextSize(13);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(Color.rgb(20,103,175));bg.setStroke(d(2),Color.WHITE);b.setBackground(bg);
        b.setOnClickListener(v->scan());
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(d(65),d(65),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;p.y=d(145);p.x=d(10);
        wm.addView(b,p);bubble=b;
        TextView pin=new TextView(this);
        pin.setText("PIN");pin.setTextSize(15);pin.setTextColor(Color.WHITE);pin.setGravity(Gravity.CENTER);
        GradientDrawable orange=new GradientDrawable();orange.setShape(GradientDrawable.OVAL);orange.setColor(Color.rgb(209,104,20));orange.setStroke(d(2),Color.WHITE);pin.setBackground(orange);
        WindowManager.LayoutParams pp=new WindowManager.LayoutParams(d(65),d(65),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        pp.gravity=Gravity.TOP|Gravity.END;pp.y=d(220);pp.x=d(10);
        pin.setOnClickListener(v->testPin());wm.addView(pin,pp);pinButton=pin;
      }catch(Exception ex){LogStore.add(this,"No apareció SCAN: "+ex);}
    });
  }
  private void hide(){
    handler.post(()->{
      if(bubble!=null){try{wm.removeView(bubble);}catch(Exception x){}bubble=null;}
      if(pinButton!=null){try{wm.removeView(pinButton);}catch(Exception x){}pinButton=null;}
    });
  }
  private void scan(){
    AccessibilityNodeInfo root=getRootInActiveWindow();
    if(root==null)LogStore.add(this,"SCAN: sin árbol de accesibilidad; control posiblemente dibujado en el juego.");
    else if(!LogStore.target(this).equals(safe(root.getPackageName())))LogStore.add(this,"SCAN: ventana de otra aplicación.");
    else{
      StringBuilder s=new StringBuilder("SCAN: JERARQUÍA DE BOTONES\n");int[] num={0};dump(root,s,0,num);
      LogStore.add(this,s.toString());
    }
    Toast.makeText(this,"Escaneo registrado",Toast.LENGTH_SHORT).show();
    if(Build.VERSION.SDK_INT>=30){
      if(bubble!=null)bubble.setVisibility(View.INVISIBLE);
      handler.postDelayed(this::screenshot,200);
    }
  }
  private void dump(AccessibilityNodeInfo n,StringBuilder out,int depth,int[] num){
    if(n==null||depth>18||num[0]>=LIMIT)return;
    num[0]++;for(int j=0;j<depth;j++)out.append("  ");
    out.append(num[0]).append(" ").append(desc(n)).append("\n");
    for(int j=0;j<n.getChildCount()&&num[0]<LIMIT;j++)dump(n.getChild(j),out,depth+1,num);
  }
  private void restore(){handler.post(()->{if(bubble!=null)bubble.setVisibility(View.VISIBLE);});}
  private void screenshot(){
    if(Build.VERSION.SDK_INT<30){restore();return;}
    try{
      takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
        public void onSuccess(ScreenshotResult result){
          try{
            HardwareBuffer buf=result.getHardwareBuffer();
            Bitmap b=Bitmap.wrapHardwareBuffer(buf,result.getColorSpace());
            if(b==null){buf.close();LogStore.add(InspectorService.this,"Captura no disponible");return;}
            Bitmap copy=b.copy(Bitmap.Config.ARGB_8888,false);buf.close();
            if(copy!=null){save(copy);copy.recycle();}
          }catch(Exception ex){LogStore.add(InspectorService.this,"Error captura "+ex);}
          finally{restore();}
        }
        public void onFailure(int code){LogStore.add(InspectorService.this,"Captura no permitida: "+code);restore();}
      });
    }catch(Exception ex){LogStore.add(this,"Error al solicitar captura "+ex);restore();}
  }
  private void save(Bitmap bmp){
    ContentValues c=new ContentValues();
    String name="PGSharp_"+System.currentTimeMillis()+".png";
    c.put(MediaStore.Images.Media.DISPLAY_NAME,name);c.put(MediaStore.Images.Media.MIME_TYPE,"image/png");c.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/InspectorPGSharp");c.put(MediaStore.Images.Media.IS_PENDING,1);
    android.net.Uri u=null;
    try{
      u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c);
      if(u==null)throw new IllegalStateException("No se pudo crear imagen");
      try(OutputStream o=getContentResolver().openOutputStream(u)){if(o==null||!bmp.compress(Bitmap.CompressFormat.PNG,100,o))throw new Exception("Error PNG");}
      ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,ready,null,null);
      LogStore.add(this,"Captura guardada: Pictures/InspectorPGSharp/"+name);
    }catch(Exception ex){if(u!=null)getContentResolver().delete(u,null,null);LogStore.add(this,"No pudo guardar imagen: "+ex);}
  }
  public void onInterrupt(){LogStore.add(this,"Inspector interrumpido");}
  public void onDestroy(){hide();super.onDestroy();}
}
