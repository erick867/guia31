package com.osito.inspector;
import android.accessibilityservice.*;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.util.Locale;

public class InspectorService extends AccessibilityService {
  private WindowManager wm;
  private TextView bubble;
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
      }catch(Exception ex){LogStore.add(this,"No apareció SCAN: "+ex);}
    });
  }
  private void hide(){
    handler.post(()->{if(bubble!=null){try{wm.removeView(bubble);}catch(Exception x){}bubble=null;}});
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
