package com.elementalgeeks.labs.glassorientation;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.ARDrone.State;
import com.elementalgeeks.labs.glass.drone.R;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;


public class MainActivity extends Activity implements  SensorEventListener {	
	private long delay = 0;
	private double baseAzimuth = 0;
	private boolean flying = false;
	private long DELAY_LIMIT = 30;
	private int action = 0;
	
	private final static int HOVER = 0;
	private final static int UP = 1;
	private final static int DOWN = 2;
	private final static int LEFT = 3;
	private final static int RIGHT = 4;
	private final static int FORWARD = 5;
	private final static int BACKWARD = 6;
	private final static int SPIN_LEFT = 3;
	private final static int SPIN_RIGHT = 4;	
	
	private TextView statusText;
	private SensorManager sensorManager;
	private Sensor rotationVectorSensor;
	private GestureDetector gestureDetector;

	private final static String TAG = MainActivity.class.getName();	
	private static final long CONNECTION_TIMEOUT = 10 * 1000;	
	private static final int DATA_TIMEOUT = 10000;
	private static final int VIDEO_TIMEOUT = 60000;	
	private static ARDrone sDrone;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		statusText = (TextView)findViewById(R.id.statusText);
		gestureDetector = createGestureDetector(this);
		 
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");		
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
		
        if(connected()){
            sDrone.resumeNavData();
            sDrone.resumeVideo();
        }
	}
	
    @Override
    protected void onPause(){
        super.onPause();
        if(connected()){
            sDrone.pauseNavData();
            sDrone.pauseVideo();
        }
    }	
	
	@Override
	protected void onStop(){
		super.onStop();
    	if (flying) {
    		flying = !land();
    	}
		sensorManager.unregisterListener(this);
		disconnect();
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
    	if (flying) {
    		flying = !land();
    	}
	}
	
	
    private void disconnect(){
        if(connected()){
            try {
                sDrone.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "failed to stop drone", e);
            }
        }
    }
    
    private boolean disconnected(){
    	return sDrone == null;
    }
    
    private boolean connected(){
    	return !disconnected();
    }
    
	private void connect(){
        WifiManager manager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        
        if(manager.isWifiEnabled()){
        	statusText.setText("Connecting via " +  manager.getConnectionInfo().getSSID() + "...");
            (new DroneConnector()).execute(MainActivity.sDrone);
        }else{
        	statusText.setText("Connect to your drone's wifi first.");
        }
    }
	
	private boolean takeOff(){
		boolean success = false;
		
		try{
			sDrone.clearEmergencySignal();
			sDrone.trim();
			sDrone.takeOff();
            success = true;
		}catch(IOException e){
			Log.e(TAG, "Faliled to execute take off command.", e);
		}
		
		return success;
	}
	
	private boolean land(){
		boolean success = false;
		
		try{
            sDrone.land();
            success = true;
        }catch(Exception e){
            Log.e(TAG, "Faliled to execute land command.", e);
        }
		
		return success;
	}    
	
	
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (gestureDetector != null) {
            return gestureDetector.onMotionEvent(event);
        }
        return false;
    }	

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {	
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float[] mRotationMatrix = new float[16];
        mRotationMatrix[ 0] = 1;
        mRotationMatrix[ 4] = 1;
        mRotationMatrix[ 8] = 1;
        mRotationMatrix[12] = 1;
        
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(
                    mRotationMatrix , event.values);
            SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, mRotationMatrix);
            //SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, mRotationMatrix);

	        float[] orientation = new float[3];
	        SensorManager.getOrientation(mRotationMatrix, orientation);// Get the current orientation of the device
	        

	        
	        /* roll (left- right+)
	         * azimuth (heading)
	         * pitch (up- down+)
	         * */
	        double azimuth = round(normalizeAngle(Math.toDegrees(orientation[0])),2);
	        double pitch = round(normalizeAngle(Math.toDegrees(orientation[1])),2);
	        double roll = round(normalizeAngle(Math.toDegrees(orientation[2])),2);
	        
	        if (baseAzimuth == 0) {
	        	baseAzimuth = azimuth;	
	        } else {	       
	        	azimuth -= baseAzimuth;
	        }
	        
	        //ugly work around for a delay	        
	        if (delay <= DELAY_LIMIT) {
	        	delay++;
	        }
	        
	        String strAction = "";

	        if (flying && delay == DELAY_LIMIT) {	        	
	        	delay = 0;		        
	        	DELAY_LIMIT = 400;
	        	if (roll >= 5.0d && roll <= 40.0d) {
	        		action = RIGHT;
	        		right(1.0f);
	        		strAction = getString(R.string.msg_move) + " " + getString(R.string.msg_right);	        		
	        	} else if (roll >= 315.0d && roll <= 350.0d) {
	        		action = LEFT;
	        		left(1.0f);
	        		strAction = getString(R.string.msg_move) + " " + getString(R.string.msg_left);
	        	} else if (pitch >= 5.0d && pitch <= 50.0d) {
	        		action = DOWN;
	        		down(1.0f);
	        		strAction = getString(R.string.msg_move) + " " + getString(R.string.msg_down);
	        	} else if (pitch >= 315.0d && pitch <= 345.0d) {
	        		action = UP;
	        		up(1.0f);
	        		strAction = getString(R.string.msg_move) + " " + getString(R.string.msg_up);	    	        
	        	} if (azimuth >= 30.0d && azimuth <= 60.0d) {
	        		action = SPIN_RIGHT;
	        		turnRight(1.0f);
	        		strAction = getString(R.string.msg_spin) + " " + getString(R.string.msg_right);
	        	} else if (azimuth <= -30.0d  && azimuth >= -60.0d) {
	        		turnLeft(1.0f);
	        		action = SPIN_LEFT;
	        		strAction = getString(R.string.msg_spin) + " " + getString(R.string.msg_left);	        		
	        	} else {
	        		action = HOVER;
	        		DELAY_LIMIT = 30;
	        	}
		        Log.e("TAG",strAction + " " + DELAY_LIMIT);

		        statusText.setText(strAction);
		        //statusText.setText("\n" + azimuth + " " + baseAzimuth + " " + orig);
	        }
	    }
		
	}
	
    private GestureDetector createGestureDetector(final Context context) {
    	GestureDetector gestureDetector = new GestureDetector(context);
    	gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {

            	if (gesture == Gesture.SWIPE_LEFT) {
            		backward(10.0f);
            	} else if (gesture == Gesture.SWIPE_RIGHT) {            		
            		forward(10.0f);
            	} else if (gesture == Gesture.SWIPE_UP) {
            		baseAzimuth = 0;
            	} else if (gesture == Gesture.TAP) {
            		
        			if(sDrone == null || sDrone.getState() == State.DISCONNECTED){
        				statusText.setText("Not landing/taking off, drone is not connected..");
        				Log.w(TAG, "Not landing/taking off, drone is not connected.");
        				return false;
                    }
                	
                	if (flying) {
                		flying = !land();
                	} else {
                		flying = takeOff();
                	}
                	
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                	connect();
                    return true;
                }
                
                return false;
            }
        });
        return gestureDetector;
    }
    
	public static double normalizeAngle(double angle) {
		return (angle >= 0.0d && angle <= 180.0d)? angle : angle + 360;
	}
	
	public static double round(double unrounded, int scale) {
	    BigDecimal bd = new BigDecimal(unrounded);
	    BigDecimal rounded = bd.setScale(scale, BigDecimal.ROUND_HALF_EVEN);
	    return rounded.doubleValue();
	}
	
	private class DroneConnector extends AsyncTask<ARDrone, Integer, ARDrone> {
		
	    @Override
	    protected ARDrone doInBackground(ARDrone... drones){
	    	ARDrone drone = drones[0];
	    	
	    	try{
	    		drone = new ARDrone(InetAddress.getByAddress(ARDrone.DEFAULT_DRONE_IP), DATA_TIMEOUT, VIDEO_TIMEOUT);
	            drone.connect();
	            drone.clearEmergencySignal();
	            drone.trim();
	            drone.waitForReady(CONNECTION_TIMEOUT);
	            drone.playLED(1, 10, 4);
	            drone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY);
	            drone.setCombinedYawMode(true);
	            
	            sDrone = drone;
	        }catch(Exception e){
	            Log.e(TAG, "Failed to connect to drone.", e);
	            try{
	                drone.clearEmergencySignal();
	                drone.clearImageListeners();
	                drone.clearNavDataListeners();
	                drone.clearStatusChangeListeners();
	                drone.disconnect();
	                
	                drone = null;
	                sDrone = null;
	            }catch(Exception ex){
	                Log.e(TAG, "Failed to clear drone state.", ex);
	            }
	        }
	        return drone;
	    }
	    
	    protected void onPostExecute(ARDrone drone){
	        if(connected()){
	        	statusText.setText("Connected.");
	        }else{
	        	statusText.setText("Connection failed.");
	        }
	    }   
    }
	
	public void right(float tilt) {
		if (connected()) {
			try {
				sDrone.move(tilt, 0.0f, 0.0f, 0.0f);
	        }catch(IOException e){
	            Log.e(TAG, "Faliled to execute land command.", e);
	        } 
		}
	}
	
	public void left(float tilt) {
		right(tilt * -1);
	}
	
	public void backward(float tilt) {
		if (connected()) {
			try {
				sDrone.move(0.0f, tilt, 0.0f, 0.0f);
	        }catch(IOException e){
	            Log.e(TAG, "Faliled to execute land command.", e);
	        } 
		}
	}	
	
	public void forward(float tilt) {
		backward(tilt * -1);
	}
	
	public void up(float speed) {
		if (connected()) {
			try {
				sDrone.move(0.0f, 0.0f, speed, 0.0f);
	        }catch(IOException e){
	            Log.e(TAG, "Faliled to execute land command.", e);
	        } 
		}
	}	
	
	public void down(float speed) {
		up(speed * -1);
	}
	
	public void turnRight(float speed) {
		if (connected()) {
			try {
				sDrone.move(0.0f, 0.0f, 0.0f, speed);
	        }catch(IOException e){
	            Log.e(TAG, "Faliled to execute land command.", e);
	        } 
		}
	}
	
	public void turnLeft(float speed) {
		turnRight(speed * -1);
	}	
}
