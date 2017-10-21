package tomatojuice.sakura.ne.jp.vocalocharactercompassforwear;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.List;

public class CompassMain extends WearableActivity implements SensorEventListener{
	
	private SensorManager mSensorManager;	// センサマネージャ
	private Sensor mAccelerometer; // 加速度センサ
	private Sensor mMagneticField; // 磁気センサ
	private List<Sensor> list;
	private boolean mValidMagneticFiled = false; // 磁気センサの更新判定
	private float[]	mAccelerometerValue = new float[3]; // 加速度センサの値
	private float[] mMagneticFieldValue = new float[3]; // 磁気センサの値
	private float[] rotate = new float[16]; // 傾斜(傾き)行列
	private float[] inclination = new float[16]; // 回転行列
	private float[] orientation = new float[3]; // 方位行列
	private Display disp;
	private DisplayMetrics metrics;
	private int dispDir;
	private SurfaceView surfaceview;
	private CompassView compassview;
	private TextView textazimuth;
	private SharedPreferences pref;
	public static String STR = new String();
	public static int WIDTH,HEIGHT,PREF_INT;


//	private static final SimpleDateFormat AMBIENT_DATE_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
//	private BoxInsetLayout mContainerView;
//	private TextView mTextView;
//	private TextView mClockView;

	public static int getWidth(){
		return  WIDTH;
	}
	
	public static int getHeight(){
		return HEIGHT;
	}
	
	public void setWidth(int width){
		WIDTH = width;
	}
	
	public void setHeight(int height){
		HEIGHT = height;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 画面を常時点灯
		super.onCreate(savedInstanceState);

		setAmbientEnabled();

		// 画面サイズの取得
		metrics = new DisplayMetrics();
		disp = getWindowManager().getDefaultDisplay();
		disp.getMetrics(metrics);
		setWidth(metrics.widthPixels);
		setHeight(metrics.heightPixels);
		Log.i("onCreate、WIDTHの値", WIDTH+"です");
		Log.i("onCreate、HEIGHTの値", HEIGHT+"です");

//		mContainerView = (BoxInsetLayout) findViewById(R.id.container);
//		mTextView = (TextView) findViewById(R.id.text);
//		mClockView = (TextView) findViewById(R.id.clock);

		// センサーを取り出す
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

		list = mSensorManager.getSensorList(Sensor.TYPE_ALL ); // 端末で利用出来るセンサー一覧取得
		for(Sensor s :list){
			Log.i("センサーの名前",s.getName());
			STR += s.getName()+ "\n";
			}

		setTheme();

		textazimuth = (TextView)findViewById(R.id.text_azimuth);

	} // onCreate

	private void setTheme(){
		pref = getSharedPreferences("pref", MODE_PRIVATE);
		PREF_INT = pref.getInt("key", 0);
		Log.i("PREF_INT", "PREF_INTの値 : "+ PREF_INT);
		switch(PREF_INT){
		case 0:
				setTheme(R.style.MikuTheme);
				setContentView(R.layout.miku_main);
			break;
		case 1:
				setTheme(R.style.GumiTheme);
				setContentView(R.layout.gumi_main);
			break;
		default:
			break;
		} // switch

		surfaceview = (SurfaceView)findViewById(R.id.compassView);
		compassview = new CompassView(this,surfaceview);
		
	} // setTheme

	@Override
	protected void onPause() { // 一時停止
		super.onPause();
		Log.i("onPause", "onPauseの呼び出し");
		mSensorManager.unregisterListener(this); // センサー解除
	}

	@Override
	protected void onResume() { // 再開時
		super.onResume();
		Log.i("onResume", "onResumeの呼び出し");
		// メーターと磁気センサーを再登録
		mSensorManager.registerListener(this,mAccelerometer,SensorManager.SENSOR_DELAY_UI);
		mSensorManager.registerListener(this,mMagneticField,SensorManager.SENSOR_DELAY_UI);
	}

	@Override
	protected void onStop() { // 停止時
		super.onStop();
		mSensorManager.unregisterListener(this);
	}

	@Override
	protected void onDestroy() { // 破棄時
		super.onDestroy();
		mSensorManager.unregisterListener(this); // センサー解除
		CompassMain.STR = "";
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		float x = ev.getX();
		float y = ev.getY();
		switch (ev.getAction()){
			case MotionEvent.ACTION_DOWN:
				Log.d("dispatchTouchEvent","ACTION_DOWN");
				Intent intent_pref = new Intent(this,CompassPreference.class);
				startActivity(intent_pref);
				intent_pref.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				finish();
				break;
			default:
				break;
		}
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { // センサーの精度変更 
		// 今回は未使用
	}

	@Override
	public void onSensorChanged(SensorEvent event) { // センサーの値変更時の処理

		switch (event.sensor.getType()) { // センサー毎の処理
			case Sensor.TYPE_ACCELEROMETER:
				mAccelerometerValue = event.values.clone();
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				mMagneticFieldValue = event.values.clone();
				mValidMagneticFiled = true;
				break;
		} // switch

		if (mValidMagneticFiled) { // 角度を出す準備
			// 回転行列を生成。 第1引数=「回転行列」、第2引数=「傾き」、第3引数=「加速度」、第4引数=「地磁気」
			SensorManager.getRotationMatrix(rotate, inclination,mAccelerometerValue,mMagneticFieldValue);
			getOrientation(rotate, orientation); // 方向を求める
			
			float degreeDir = (float)Math.toDegrees(orientation[0]); // デグリー角に変換
//			Log.i("onSensorChanged", "角度:" + degreeDir);

			if(degreeDir < 0){ // 180°以降は-1～-179の表示になるので、0～360°で方位角を出す
				textazimuth.setText(String.valueOf(360 + (int)degreeDir) + "°");
			}else{
				textazimuth.setText(String.valueOf((int)degreeDir) + "°");
			}

			CompassView.setArrowDir(degreeDir); // 方位磁針を回転描画
		} // if文ここまで
	} // onSensorChanged

	public void getOrientation(float[] rotate, float[] out) { // 画面回転中の方位取得メソッ

		float[] outR = new float[16];
		float[] outR2 = new float[16];

		dispDir = disp.getRotation(); // 自然な"方向からの画面の回転を返す。戻り値=ROTATION_0,90,180,270

		switch(dispDir){ // 回転の値によって処理
			case Surface.ROTATION_0:
				SensorManager.getOrientation(rotate, out);
				break;
			case Surface.ROTATION_90:
				SensorManager.remapCoordinateSystem(rotate, SensorManager.AXIS_Y,SensorManager.AXIS_MINUS_X, outR);
				break;
			case Surface.ROTATION_180:
				SensorManager.remapCoordinateSystem(rotate, SensorManager.AXIS_Y,SensorManager.AXIS_MINUS_X, outR2);
				SensorManager.remapCoordinateSystem(outR2, SensorManager.AXIS_Y,SensorManager.AXIS_MINUS_X, outR);
				break;
			case Surface.ROTATION_270:
				SensorManager.remapCoordinateSystem(outR, SensorManager.AXIS_MINUS_Y,SensorManager.AXIS_MINUS_X, outR);
				break;
		}

		if(dispDir != Surface.ROTATION_0){
			SensorManager.getOrientation(outR, out);
		}

	} // getOrientation


	@Override
	public void onEnterAmbient(Bundle ambientDetails) {
		super.onEnterAmbient(ambientDetails);
		updateDisplay();
	}

	@Override
	public void onUpdateAmbient() {
		super.onUpdateAmbient();
		updateDisplay();
	}

	@Override
	public void onExitAmbient() {
		updateDisplay();
		super.onExitAmbient();
	}

	private void updateDisplay() {
		if (isAmbient()) {
//			mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
//			mTextView.setTextColor(getResources().getColor(android.R.color.white));
//			mClockView.setVisibility(View.VISIBLE);
//
//			mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
		} else {
//			mContainerView.setBackground(null);
//			mTextView.setTextColor(getResources().getColor(android.R.color.black));
//			mClockView.setVisibility(View.GONE);
		}
	}

}
