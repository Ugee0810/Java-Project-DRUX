package me.jfenn.alarmio.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;
import com.afollestad.aesthetic.AestheticActivity;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import me.jfenn.alarmio.Alarmio;
import me.jfenn.alarmio.R;
import me.jfenn.alarmio.data.PreferenceData;
import me.jfenn.alarmio.dialogs.AlertDialog;
import me.jfenn.alarmio.fragments.BaseFragment;
import me.jfenn.alarmio.fragments.HomeFragment;
import me.jfenn.alarmio.fragments.SplashFragment;
import me.jfenn.alarmio.fragments.StopwatchFragment;
import me.jfenn.alarmio.fragments.TimerFragment;
import me.jfenn.alarmio.receivers.TimerReceiver;

public class MainActivity extends AestheticActivity implements FragmentManager.OnBackStackChangedListener, Alarmio.ActivityListener {

    public static final String EXTRA_FRAGMENT = "me.jfenn.alarmio.MainActivity.EXTRA_FRAGMENT";
    public static final int FRAGMENT_TIMER = 0;
    public static final int FRAGMENT_STOPWATCH = 2;
    private Alarmio alarmio;
    private WeakReference<BaseFragment> fragmentRef;

    // 블루투스
    public static Context mContext;
    boolean cheak = true;
    public static final int REQUEST_ENABLE_BT = 10;
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    String deviceName = "";
    long now = System.currentTimeMillis();
    Date date = new Date(now);
    SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    String strNow = sdfNow.format(date);
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;
    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';
    Thread mWorkerThread = null;
    byte[] readBuffer;
    int readBufferPosition;
    EditText mEditReceive, mEditSend;
    // 블루투스

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        alarmio = (Alarmio) getApplicationContext();
        alarmio.setListener(this);

        // 블루투스 사용
        mContext = this;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            return;
        }
        checkBluetooth();
        // 블루투스 사용

        if (savedInstanceState == null) {
            BaseFragment fragment = createFragmentFor(getIntent());
            if (fragment == null)
                return;

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment, fragment)
                    .commit();

            fragmentRef = new WeakReference<>(fragment);
        } else {
            BaseFragment fragment;

            if (fragmentRef == null || (fragment = fragmentRef.get()) == null)
                fragment = new HomeFragment();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment, fragment)
                    .commit();

            fragmentRef = new WeakReference<>(fragment);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // background permissions info
        if (Build.VERSION.SDK_INT >= 23 && !PreferenceData.INFO_BACKGROUND_PERMISSIONS.getValue(this, false)) {
            AlertDialog alert = new AlertDialog(this);
            alert.setTitle(getString(R.string.info_background_permissions_title));
            alert.setContent(getString(R.string.info_background_permissions_body));
            alert.setListener((dialog, ok) -> {
                if (ok) {
                    PreferenceData.INFO_BACKGROUND_PERMISSIONS.setValue(MainActivity.this, true);
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                }
            });
            alert.show();
        }
    }//onCreate 닫기

    // 블루투스
    void checkBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show();
            finish();   // 블루투스 미지원 기기일 경우 강제 종료
        }
        else {  // 반대로 블루투스 지원하면
            if(!mBluetoothAdapter.isEnabled()) { // 블루투스 지원하지만 비활성 상태인 경우
                Toast.makeText(getApplicationContext(), "현재 블루투스가 비활성 상태입니다.", Toast.LENGTH_LONG).show();
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); // REQUEST_ENABLE_BT : 블루투스 활성 상태의 변경 결과를 App 으로 알려줄 때 식별자로 사용(0이상)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                selectDevice(); // 블루투스를 지원하고 활성 상태일 경우 디바이스 선택
            }
        }
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        readBufferPosition = 0;      // 버퍼 내 수신 문자 저장 위치
        readBuffer = new byte[1024]; // 수신 버퍼
        // 문자열 수신 쓰레드
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // interrupt() 메소드를 이용 스레드를 종료시키는 예제이다.
                // interrupt() 메소드는 하던 일을 멈추는 메소드이다.
                // isInterrupted() 메소드를 사용하여 멈추었을 경우 반복문을 나가서 스레드가 종료하게 된다.
                while(!Thread.currentThread().isInterrupted()) {
                    try {
                        // InputStream.available() : 다른 스레드에서 blocking 하기 전까지 읽은 수 있는 문자열 개수를 반환
                        int byteAvailable = mInputStream.available();   // 수신 데이터 확인
                        if(byteAvailable > 0) {                         // 데이터가 수신된 경우.
                            byte[] packetBytes = new byte[byteAvailable];
                            // read(buf[]) : 입력스트림에서 buf[] 크기만큼 읽어서 저장 없을 경우에 -1 리턴
                            mInputStream.read(packetBytes);
                            for(int i=0; i<byteAvailable; i++) {
                                byte b = packetBytes[i];
                                if(b == mCharDelimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    // System.arraycopy(복사할 배열, 복사시작점, 복사된 배열, 붙이기 시작점, 복사할 개수)
                                    // readBuffer 배열을 처음 부터 끝까지 encodedBytes 배열로 복사
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        // 수신된 문자열 데이터에 대한 처리.
                                        @Override
                                        public void run() {
                                            // mStrDelimiter = '\n';
                                            mEditReceive.setText(mEditReceive.getText().toString() + data+ mStrDelimiter);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (Exception e) { // 데이터 수신 중 오류 발생
                        Toast.makeText(getApplicationContext(), "데이터 수신 중 오류가 발생 했습니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    BluetoothDevice getDeviceFromBondedList(String name) {  //BluetoothDevice : 페어링 된 기기 목록을 얻어옴.
        BluetoothDevice selectedDevice = null;
        //getBondedDevices 함수가 반환하는 페어링 된 기기 목록은 Set 형식이며,
        //Set 형식에서는 n 번째 원소를 얻어오는 방법이 없으므로 주어진 이름과 비교해서 찾는다.
        for(BluetoothDevice device : mDevices) {    // getName() : 디바이스의 블루투스 어댑터 이름을 반환
            if(name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    public void selectDevice() {
        // 블루투스 디바이스는 연결해서 사용하기 전에 먼저 페어링 되어야만 한다
        // getBondedDevices() : 페어링된 장치 목록 얻어오는 함수.
        mDevices = mBluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();

        if(mPairedDeviceCount == 0 ) {  // 페어링된 장치가 없는 경우.
            Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다. 먼저 기기와 페어링을 해주세요.", Toast.LENGTH_LONG).show();
        }
        // 페어링된 장치가 있는 경우.
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");

        // 각 디바이스는 이름과 서로 다른 주소를 가진다. 페어링 된 디바이스들을 표시한다.
        List<String> listItems = new ArrayList<String>();
        for(BluetoothDevice device : mDevices) {
            // device.getName() : 단말기의 블루투스 어댑터 이름을 반환
            listItems.add(device.getName());
        }
        listItems.add("뒤로가기");    // 취소 항목 추가
        // CharSequence : 변경 가능한 문자열
        // toArray : List 형태로 넘어온것 배열로 바꿔서 처리하기 위한 toArray() 함수
        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);
        // toArray 함수를 이용해서 size 만큼 배열이 생성 되었다.
        listItems.toArray(new CharSequence[listItems.size()]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if(item == mPairedDeviceCount) {    // 연결할 장치를 선택하지 않고 '취소'를 누른 경우
                    Toast.makeText(getApplicationContext(), "연결할 장치를 선택하지 않았습니다.", Toast.LENGTH_LONG).show();
                }
                else {  // 연결할 장치를 선택한 경우, 선택한 장치와 연결을 시도
                    connectToSelectedDevice(items[item].toString());
                    deviceName=items[item].toString();
                }
            }
        });
        builder.setCancelable(false);    //뒤로 가기 버튼 사용 금지
        android.app.AlertDialog alert = builder.create();
        alert.show();
    }

    void connectToSelectedDevice(String selectedDeviceName) {   // 블루투스 디바이스의 원격 블루투스 기기를 나타냄
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        try {
            // 소켓 생성, RFCOMM 채널을 통한 연결.
            // createRfcommSocketToServiceRecord(uuid) : 이 함수를 사용하여 원격 블루투스 장치와 통신할 수 있는 소켓을 생성
            // 이 메소드가 성공하면 스마트폰과 페어링 된 디바이스간 통신 채널에 대응하는 BluetoothSocket 오브젝트를 리턴
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);
            mSocket.connect();  // 소켓이 생성 되면 connect() 함수를 호출함으로써 두기기의 연결은 완료된다.
            // 데이터 송수신을 위한 스트림 얻기.
            // BluetoothSocket 오브젝트는 두개의 Stream을 제공한다.
            // 1. 데이터를 보내기 위한 OutputStream
            // 2. 데이터를 받기 위한 InputStream
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
        } catch(Exception e) {  //블루투스 연결 중 오류 발생
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK) { }   // 블루투스가 활성 상태로 변경됨
                else if(resultCode == RESULT_CANCELED )
                { }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    // 블루투스

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(isActionableIntent(intent)) {
            FragmentManager manager = getSupportFragmentManager();
            BaseFragment newFragment = createFragmentFor(intent);
            BaseFragment fragment = fragmentRef != null ? fragmentRef.get() : null;

            if(newFragment == null || newFragment.equals(fragment)) // check that fragment isn't already displayed
                return;

            if(newFragment instanceof HomeFragment && manager.getBackStackEntryCount() > 0) // clear the back stack
                manager.popBackStack(manager.getBackStackEntryAt(0).getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);

            FragmentTransaction transaction = manager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_up_sheet, R.anim.slide_out_up_sheet, R.anim.slide_in_down_sheet, R.anim.slide_out_down_sheet)
                    .replace(R.id.fragment, newFragment);

            if(fragment instanceof HomeFragment && !(newFragment instanceof HomeFragment))
                transaction.addToBackStack(null);

            fragmentRef = new WeakReference<>(newFragment);
            transaction.commit();
        }
    }

    @Nullable
    private BaseFragment createFragmentFor(Intent intent) {
        BaseFragment fragment = fragmentRef != null ? fragmentRef.get() : null;
        int fragmentId = intent.getIntExtra(EXTRA_FRAGMENT, -1);

        switch(fragmentId) {
            case FRAGMENT_STOPWATCH:
                if(fragment instanceof StopwatchFragment)
                    return fragment;
                return new StopwatchFragment();

            case FRAGMENT_TIMER:
                if(intent.hasExtra(TimerReceiver.EXTRA_TIMER_ID)) {
                    int id = intent.getIntExtra(TimerReceiver.EXTRA_TIMER_ID, 0);
                    if(alarmio.getTimers().size() <= id || id < 0) 
                        return fragment;

                    Bundle args = new Bundle();
                    args.putParcelable(TimerFragment.EXTRA_TIMER, alarmio.getTimers().get(id));

                    BaseFragment newFragment = new TimerFragment();
                    newFragment.setArguments(args);
                    return newFragment;
                }
                return fragment;
                default:
                    if (Intent.ACTION_MAIN.equals(intent.getAction()) || intent.getAction() == null)
                        return new SplashFragment();
                    Bundle args = new Bundle();
                    args.putString(HomeFragment.INTENT_ACTION, intent.getAction());
                    BaseFragment newFragment = new HomeFragment();
                    newFragment.setArguments(args);
                    return newFragment;
        }
    }

    private boolean isActionableIntent(Intent intent) {
        return intent.hasExtra(EXTRA_FRAGMENT)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && (AlarmClock.ACTION_SHOW_ALARMS.equals(intent.getAction())
                || AlarmClock.ACTION_SET_TIMER.equals(intent.getAction()))
                || AlarmClock.ACTION_SET_ALARM.equals(intent.getAction())
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && (AlarmClock.ACTION_SHOW_TIMERS.equals(intent.getAction()))));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(alarmio != null)
            alarmio.setListener(null);
        alarmio = null;
        try {
            mWorkerThread.interrupt();  //데이터 수신 쓰레드 종료
            mInputStream.close();
            mSocket.close();
        } catch(Exception e) {}
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState != null ? outState : new Bundle()); 
    }

    @Override
    protected void onPause() {
        super.onPause();
        alarmio.stopCurrentSound();
    }

    @Override
    public void onBackStackChanged() {
        BaseFragment fragment = (BaseFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        fragmentRef = new WeakReference<>(fragment);
    }

    @Override
    public void requestPermissions(String... permissions) {
        ActivityCompat.requestPermissions(this, permissions, 0);
    }

    @Override
    public FragmentManager gettFragmentManager() {
        return getSupportFragmentManager();
    }
}