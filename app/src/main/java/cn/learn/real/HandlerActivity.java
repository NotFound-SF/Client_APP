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
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


public class HandlerActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String  CONFIG_FILE = "userProfile";        // 配置文件名
    private static final String  LOGIN_FLAG = "loginFlag";           // 登录到了登录界面
    private static final String TAG = "HandlerActivity";
    private static final String USER_ID_KEY = "learn.userID";
    private static final String DEVICE_ID_KEY = "learn.deviceID";
    private static final String SERVER_ADDRESS = "192.168.2.106";    // 服务器地址
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

    int len;                                                 // 读取到的字符长度



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
        mGetDataFormat.userID.set(mUserID);
        mSetDataFormat.deviceID.set(mDeviceID);
        mGetDataFormat.deviceID.set(mDeviceID);
        // 该项对于APP接收是没有意义的，主要是供单片机解析
        mSetDataFormat.operation.set((short)0xFF);                              // 单片机端解析为设置数据
        mGetDataFormat.operation.set((short)0x00);                              // 单片机解析为读取数据

        // 创建一个接受线程当数据到达时更新UI-----------------------------------------------------------------------------------
        mRecvThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Restart:
                while (!mMainThreadEndFlag) {
                    // 创建与服务器连接的socket,与输入输出流必须在子线程创建才能保证成功
                    try {
                        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
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

                        // 循环读取数据
                        while (!mMainThreadEndFlag) {                                 // 可能读会出现数据混乱
                            if (mGetDataFormat.size() == (len = mGetDataFormat.read(mSockInStream))) {
                                // 根据读取到的数据更新UI
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {                               // 解析接收到的数据更新UI
                                       // mRecvText.setText(new String(buf, 0, len));
                                    }
                                });
                            } else if(-1 == len){
                                throw new RuntimeException("netWork");              // 表示网络异常
                            }
                        }
                        return;
                    } catch (Exception e) {
                        if (mConnectedFlag) {                                       // 如果先前连接成功就释放
                            try {
                                socket.close();
                            } catch (Exception e1) {
                            }
                            mConnectedFlag = false;                                  // 表示网络连接失败
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
                            Thread.sleep(1500);                            // 睡眠1秒
                        } catch (Exception e1) {
                        }
                        continue Restart;                                         // 重新启动连接
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
                alive.deviceID.set(0);                                  // 目标ID为0表示存活信息(应该添加数据请求)
                alive.operation.set((short)0x00);                       // 表示请求数据

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


//        // 绑定布局
//        mRecvText = (TextView) findViewById(R.id.output);
//        mInputEdit = (EditText) findViewById(R.id.input);
//        mSendButton = (Button) findViewById(R.id.buttonSend);
//        mWarning = (Button) findViewById(R.id.buttonDialog);

//        // 设置监听器
//        mSendButton.setOnClickListener(this);
//        mWarning.setOnClickListener(this);
    }

    // 快捷键alt + shift + p 重写监听函数
    @Override
    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.buttonSend:
//                mInString = mInputEdit.getText().toString().trim();
//                if (!TextUtils.isEmpty(mInString)) {                // 编辑框非空
//                    mSendHandler.sendEmptyMessage(1);
//                } else {
//                    mRecvText.setText("");
//                }
//                break;
//
//            case R.id.buttonDialog:
//                AlertDialog dialog = new AlertDialog.Builder(this)
//                        .setIcon(R.mipmap.warning)                                //设置标题的图片
//                        .setTitle("火灾报警！")                               //设置对话框的标题
//                        .setMessage("取消火灾警报？")                             //设置对话框的内容
//                        //设置对话框的按钮
//                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                // 发送取消报警信息
//                                //Toast.makeText(HandlerActivity.this, "点击了确定的按钮", Toast.LENGTH_SHORT).show();
//                                dialog.dismiss();
//                            }
//                        }).create();
//                dialog.show();
//                break;
//        }
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

