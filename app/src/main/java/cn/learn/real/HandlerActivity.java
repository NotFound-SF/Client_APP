package cn.learn.real;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;


public class HandlerActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String  CONFIG_FILE = "userProfile";        // 配置文件名
    private static final String  LOGIN_FLAG = "loginFlag";           // 登录到了登录界面
    private static final String TAG = "HandlerActivity";
    private static final String USER_ID_KEY = "learn.userID";
    private static final String DEVICE_ID_KEY = "learn.deviceID";
    private static final String SERVER_ADDRESS = "192.168.1.105";    // 服务器地址
    private static final int SERVER_PORT = 2048;                     // 服务器端口
    private boolean mConnectedFlag = false;                          // 网络连接情况
    private boolean mMainThreadEndFlag = false;                      // 判断主线程退出标志
    private InputStream mSockInStream = null;                        // socket输入流
    private OutputStream mSockOutStream = null;                      // socket输出流
    private Thread mRecvThread = null;                       // 接受线程
    private Thread mAliveThread = null;                      // 向服务器发送存活脉搏
    private HandlerThread mSendThread = null;                // 发送线程
    private Handler mSendHandler = null;
    private Socket socket = null;                            // 记住添加网络权限
    private int  mUserID = 0;                                // 设备ID
    private int  mDeviceID = 0;                              // 用户ID
    private DataFormat mSetDataFormat = new DataFormat();    // 传输的数据结构体
    private DataFormat mGetDataFormat = new DataFormat();
    private SharedPreferences mConfig = null;
    private SharedPreferences.Editor mConfigEditor = null;

    // View类型

    private Switch   mLedSwitch        = null;
    private Switch   mPowerSwitch      = null;
    private TextView mCurrentText      = null;
    private TextView mWeatherText      = null;
    private SeekBar  mWindowSeekBar    = null;
    private Switch   mLightAutoSwitch  = null;
    private TextView mTemperatureText  = null;
    private Switch   mWindowAutoSwitch = null;



    public static Intent newIntent(Context packageContext, int uersID, int deviceID) {
        Intent intent = new Intent(packageContext, HandlerActivity.class);
        intent.putExtra(USER_ID_KEY, uersID);
        intent.putExtra(DEVICE_ID_KEY, deviceID);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handler);
        MyApplication.getInstance().addActivity(this);                          // 添加到列表

        // 将数据添加到发送单元的结构体当中
        mUserID = getIntent().getIntExtra(USER_ID_KEY, 0);
        mDeviceID = getIntent().getIntExtra(DEVICE_ID_KEY, 0);
        mSetDataFormat.userID.set(mUserID);
        mSetDataFormat.deviceID.set(mDeviceID);
        mGetDataFormat.userID.set(mUserID);
        mGetDataFormat.deviceID.set(mDeviceID);

        // 该项对于APP接收是没有意义的，主要是供单片机解析
        mSetDataFormat.operation.set((short)0xFF);                              // 单片机端解析为设置数据
        mGetDataFormat.operation.set((short)0x00);                              // 单片机解析为读取数据

        // 绑定将布局文件中的view到UI控件

        mLedSwitch        = (Switch)   findViewById(R.id.led_switch);
        mPowerSwitch      = (Switch)   findViewById(R.id.power_switch);
        mCurrentText      = (TextView) findViewById(R.id.current_text);
        mWeatherText      = (TextView) findViewById(R.id.weather_text);
        mWindowSeekBar    = (SeekBar)  findViewById(R.id.window_seek);
        mTemperatureText  = (TextView) findViewById(R.id.temperature_text);
        mLightAutoSwitch  = (Switch)   findViewById(R.id.auto_light_switch);
        mWindowAutoSwitch = (Switch)   findViewById(R.id.window_auto_switch);

        // 创建一个接受线程当数据到达时更新UI-----------------------------------------------------------------------------------
        mRecvThread = new Thread(new Runnable() {

            private boolean     dialog_flag = false;                                      // 检测对话框是否已经存在

            @Override
            public void run() {
                int         recv_len;                                                     // 读取到的字符长度
                int         data_size = mGetDataFormat.size();                            // 一帧数据长度

                Restart:
                while (!mMainThreadEndFlag) {
                    // 创建与服务器连接的socket,与输入输出流必须在子线程创建才能保证成功
                    try {
                        socket = new Socket();
                        // 连接到服务器，两秒内连接不成功就放弃，该函数可能会抛出异常
                        socket.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT), 2000);
                        mSockInStream = socket.getInputStream();
                        mSockOutStream = socket.getOutputStream();
                        mConnectedFlag = true;                                         // 表示网络连接成功
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HandlerActivity.this, "连接服务器成功",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

                        // 接受单片机端回传的数据
                        while (!mMainThreadEndFlag) {                                 // 接受到的数据可能会出错

                            recv_len = mGetDataFormat.read(mSockInStream);            // 阻塞等待读取数据

                            if (data_size == recv_len) {                              // 标书数据未发生错误
                                // 保证要设置的数据与单片机端回传的数据相同，需要互斥操作
                                mSetDataFormat = new DataFormat(mGetDataFormat);
                                mSetDataFormat.userID.set(mUserID);
                                mSetDataFormat.deviceID.set(mDeviceID);

                                // 根据读取到的数据更新UI
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {                               // 解析接收到的数据更新UI
                                        mCurrentText.setText(""+mGetDataFormat.current.get()+"A");           // 设置电流
                                        mTemperatureText.setText(""+mGetDataFormat.temperature.get()+"℃");  // 设置温度

                                        if (0x0F == mGetDataFormat.rainStatus.get())                            // 表示晴天
                                            mWeatherText.setText("晴");
                                        else if (0x00 == mGetDataFormat.rainStatus.get())
                                            mWeatherText.setText("雨");

                                        if (0x0F == mGetDataFormat.ledAuto.get())                            // 设置光控开关
                                            mLightAutoSwitch.setChecked(true);
                                        else if (0x00 == mGetDataFormat.ledAuto.get())
                                            mLightAutoSwitch.setChecked(false);

                                        if (0x0F == mGetDataFormat.ledSwitch.get())                          // 设置照明开关
                                            mLedSwitch.setChecked(true);
                                        else if (0x00 == mGetDataFormat.ledSwitch.get())
                                            mLedSwitch.setChecked(false);

                                        if (0x0F == mGetDataFormat.powerSwitch.get())                         // 设置电源开关
                                            mPowerSwitch.setChecked(true);
                                        else if (0x00 == mGetDataFormat.powerSwitch.get())
                                            mPowerSwitch.setChecked(false);

                                        if (0x0F == mGetDataFormat.windowAuto.get())                         // 设置自动开关窗开关
                                            mWindowAutoSwitch.setChecked(true);
                                        else if (0x00 == mGetDataFormat.windowAuto.get())
                                            mWindowAutoSwitch.setChecked(false);

                                        if (0x0F == mGetDataFormat.warning.get() && false == dialog_flag) {                         // 火灾报警信息
                                            dialog_flag = true;
                                            AlertDialog dialog = new AlertDialog.Builder(HandlerActivity.this)
                                                    .setIcon(R.mipmap.warning)                               // 设置标题的图片
                                                    .setTitle("火灾报警！")                                  // 设置对话框的标题
                                                    .setMessage("取消火灾警报？")                            // 设置对话框的内容
                                                      //设置对话框的按钮
                                                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            // 发送取消报警信息
                                                            //Toast.makeText(HandlerActivity.this, "点击了确定的按钮", Toast.LENGTH_SHORT).show();
                                                            dialog_flag = false;
                                                            dialog.dismiss();                                // 消失对话框
                                                         }
                                                    }).create();
                                            dialog.show();
                                        }

                                        // 设置拖动条
                                        // mWindowSeekBar
                                    }
                                });
                            } else if(-1 == recv_len){
                                throw new RuntimeException("netWork");              // 表示网络异常
                            }
                        }

                        return;                                                     // 退出子线程
                    } catch (Exception e) {
                        if (mConnectedFlag) {                                       // 如果先前连接成功就释放
                            try {
                                socket.close();
                            } catch (Exception e1) {
                            }
                            mConnectedFlag = false;                                 // 表示网络连接失败
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(HandlerActivity.this, "连接服务器失败",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            Log.e(TAG, "Create socket error");
                        }
                        try {
                            Thread.sleep(6000);                              // 睡眠6秒音每次相应时间间隔比较大
                        } catch (Exception e1) {
                        }
                        continue Restart;                                           // 重新启动连接
                    }
                }
            }
        });
        // 启动接收子进程
        mRecvThread.start();


        // 创建发送子进程，接收主线程发送的数据送往socket----------------------------------------------------------------------------------------
        mSendThread = new HandlerThread("Send Thread");
        mSendThread.start();                       // 开启发送进程
        // 将sendHandler绑定到send进程，所以handleMessage会在子线程执行
        mSendHandler = new Handler(mSendThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                // 读取数据并且发送
                if (mConnectedFlag) {
                    synchronized (HandlerActivity.class){           // 发送加锁
                        try {
                            mSetDataFormat.write(mSockOutStream);
                        } catch (Exception ew) {
                            Log.d(TAG, "alive failed");
                        }
                    }
                }
            }
        };

        // 创建存活脉搏子线程
        mAliveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DataFormat alive = new DataFormat();
                alive.userID.set(mUserID);
                alive.deviceID.set(mDeviceID);                          // 并非单纯的存活,而是周期性的数据请求
                alive.operation.set((short)0x00);                       // 表示向单片机请求数据

                while (!mMainThreadEndFlag) {
                    // 如果连接成功就不断发送脉搏信息
                    if (mConnectedFlag) {
                        synchronized (HandlerActivity.class){           // 发送加锁
                            try {
                                alive.write(mSockOutStream);
                            } catch (Exception ew) {
                                Log.d(TAG, "alive failed");
                            }
                        }
                    }
                    try {
                        Thread.sleep(2000);                    // 6s检测一次
                    } catch (Exception es) {

                    }
                }
            }
        });
        mAliveThread.start();
    }

    // 快捷键alt + shift + p 重写监听函数
    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.led_switch:                // 设置照明开关
                Toast.makeText(this, "led_switch", Toast.LENGTH_SHORT).show();
                break;

            case R.id.auto_light_switch:         // 自动照明设置
                Toast.makeText(this, "auto_light_switch", Toast.LENGTH_SHORT).show();
                break;

            case R.id.power_switch:              // 设置了电源
                Toast.makeText(this, "power_switch", Toast.LENGTH_SHORT).show();
                break;

            case R.id.window_auto_switch:       // 设置了下雨自动关窗
                Toast.makeText(this, "window_auto_switch", Toast.LENGTH_SHORT).show();
                break;
        }

        Log.e(TAG, "监听到View被触发");

        // 交由发送线程处理

    }

    // 添加菜单项
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.handler, menu);
        return true;
    }

    // 监听菜单项选择
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:                                      // 清除登录项
                // 从配置文件获取编辑框信息
                mConfig = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE);
                mConfigEditor = mConfig.edit();
                mConfigEditor.putBoolean(LOGIN_FLAG, false);
                mConfigEditor.apply();                             // 提交用户信息
                finish();                                          // 结束自己
                break;

            case R.id.cancel:

                break;
        }
        return true;
    }



    // 提醒应用退出
    private boolean mIsExit;
    @Override
    /**
     * 双击返回键退出
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mIsExit) {
                MyApplication.getInstance().exit();

            } else {
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
                mIsExit = true;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mIsExit = false;
                    }
                }, 2000);
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // 重写销毁函数
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainThreadEndFlag = true;                 //主线程退出
        mSendThread.quit();                    // 销毁子线程
        if (mConnectedFlag) {
            try {
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "onDestroy: close stream faild");
            }
        }
        Log.w(TAG, "Out success ");
    }
}

