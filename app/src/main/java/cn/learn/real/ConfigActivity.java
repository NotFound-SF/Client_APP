package cn.learn.real;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ConfigActivity extends AppCompatActivity {

    private static final byte   APP_REQUEST = (byte)(0x12);                   // 表向单片机请求WIFI及电话信息
    private static final byte   WIFI_SET    = (byte)(0x60);                   // 表示向单片机设置wifi热点信息
    private static final byte   PHONE_DEL   = (byte)(0x61);                   // 表示删除一条电话号码
    private static final byte   PHONE_CLEAN = (byte)(0x62);                   // 表示要清空电话信息
    private static final byte   PHONE_ADD   = (byte)(0x63);                   // 表示追加一条电话信息
    private static final String TAG = "ConfigActivity";

    private TextView             mWifiText = null;                             // wifi显示view
    private ListView             listView = null;
    private PhoneNumber          phoneNumber = null;
    private PhoneAdapter         phoneAdapter = null;
    private List<PhoneNumber>    phoneNumberList = new ArrayList<>();
    private InfoStruct infoStruct = new InfoStruct();           // 用于存储单片机端发送的数据


    private Socket socket = null;
    private InputStream mSockInStream = null;                        // socket输入流
    private OutputStream mSockOutStream = null;                      // socket输出流
    private boolean mConnectedFlag = false;                          // 网络连接情况
    private boolean threadEndUi    = false;                          // UI线程退出标志
    private static final int SERVER_PORT = 2046;                     // 服务器端口
    private static final String SERVER_ADDRESS = "192.168.4.1";      // 服务器地址
    private HandlerThread mSendThread = null;                        // 发送线程
    private Handler mSendHandler = null;


    public static Intent newIntent(Context packageContext) {
        Intent intent = new Intent(packageContext, ConfigActivity.class);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        // 绑定到布局文件
        mWifiText = (TextView) findViewById(R.id.wifi_text);
        listView = (ListView) findViewById(R.id.phone_list);

        // 显示电话列标普
        phoneAdapter = new PhoneAdapter(this, R.layout.item_phone, phoneNumberList);
        listView.setAdapter(phoneAdapter);



        // 创建接收线程socket以及更细UI----------------------------------------------------------------------------------------------------
        new Thread(new Runnable() {
            @Override
            public void run() {
                int   readLen;                                            // 读取到的字节长度
                int   dat_len;
                int   infoSize = infoStruct.size();                       // 存储结构体长度

                mConnectedFlag = false;                                   // 表示还未连接成功

                ConnectRestart:
                while (!threadEndUi) {                                    // UI线程结束时结束该线程
                    // 创建与服务器连接的socket,与输入输出流必须在子线程创建才能保证成功
                    try {
                       socket = new Socket();                     // 创建一个未连接的socket

                        // 连接到服务器，两秒内连接不成功就放弃，该函数可能会抛出异常
                        socket.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT), 2000);
                        mSockInStream = socket.getInputStream();          // 取得输入流，可能抛出IO异常
                        mSockOutStream = socket.getOutputStream();        // 取得输出流，可能抛出IO异常
                        mConnectedFlag = true;                            // 执行到该步，表示网络连接成功

                        // 通过吐司提醒用户连接成功
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ConfigActivity.this, "连接本地设备成功",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

                        // 接收数据并且更新UI
                        while (!threadEndUi) {

                            if ((readLen = infoStruct.read(mSockInStream)) == infoSize) {    // 读取到数据

                                // 解析结构体中的wifi数据
                                dat_len = infoStruct.m_wifi_ssid_l.get();
                                if (dat_len > 0 && dat_len <= 30 ) {                         // wifi热点名字信息大于0
                                    runOnUiThread(new Runnable() {                           // 更新UI
                                            @Override
                                            public void run() {
                                                mWifiText.setText(infoStruct.m_wifi_ssid.get());
                                            }
                                    });
                                }

                                // 解析结构体中的电话信息，根据需要追加到适配器
                                for (int index = 0; index < 32; index++) {
                                    dat_len = infoStruct.m_phone_info[index].m_phone_l.get();
                                    if ( dat_len > 0 && dat_len <= 11) {                         // 表示电话号码存在
                                        phoneNumber = new PhoneNumber(infoStruct.m_phone_info[index].m_phone.get(), R.drawable.phone, (byte)index); // 最后一个参数是Flash中的顺序
                                        if (!phoneNumberList.contains(phoneNumber)) {            // 如果不包含该电话才显示
                                            phoneNumberList.add(phoneNumber);                    // 添加到适配器
                                        }
                                    }
                                }

                                // 更新UI界面的电话号码
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        phoneAdapter.notifyDataSetChanged();         // 刷新界面
                                    }
                                });


                            } else if(-1 == readLen){
                                throw new RuntimeException("netWork");              // 表示网络异常,让其重连
                            }
                        }
                        return;                                                     // 线程结束出口
                    }catch (Exception e) {                                          // 表明连接失败
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
                                    Toast.makeText(ConfigActivity.this, "连接硬件设备失败",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            Log.e(TAG, "Create socket error");
                        }
                        try {
                            Thread.sleep(6000);                               // 睡眠6秒音每次相应时间间隔比较大
                        } catch (Exception e1) {
                        }
                        continue ConnectRestart;                                     // 重新启动连接
                    }
                }
            }
        }).start();



        // 创建发送线程----------------------------------------------------------------------------------------------------------------------
        mSendThread = new HandlerThread("Send Thread");
        mSendThread.start();                       // 开启发送进程
        // 将sendHandler绑定到send进程，所以handleMessage会在子线程执行
        mSendHandler = new Handler(mSendThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                // 读取数据并且发送
                if (mConnectedFlag) {
                    synchronized (ConfigActivity.class) {                                    // 发送加锁
                        try {
                            byte[] realMsg = null;

                            switch (msg.what) {
                                // APP设置单片机wifi信息
                                case WIFI_SET:
                                    byte[] headWifi = new byte[3];
                                    headWifi[0] = WIFI_SET;                                  // 消息类型
                                    headWifi[1] = (byte) msg.arg1;                           // wifi名长度
                                    headWifi[2] = (byte) msg.arg2;                           // wifi密码长度
                                    // 将头和体拼接成一条真实的消息
                                    realMsg = unitByteArray(headWifi, ((String) msg.obj).getBytes());
                                    break;

                                // APP向单片机追加一条电话号码
                                case PHONE_ADD:
                                    byte[] headPhoneAdd = new byte[2];
                                    headPhoneAdd[0] = PHONE_ADD;
                                    headPhoneAdd[1] = (byte) msg.arg1;                      // 电话号码长度
                                    // 将头和体拼接成一条真实的消息
                                    realMsg = unitByteArray(headPhoneAdd, ((String) msg.obj).getBytes());
                                    break;

                                // APP向单片机请求删除一条指定序列的电话号码
                                case PHONE_DEL:
                                    byte[] headPhoneDel = new byte[2];
                                    headPhoneDel[0] = PHONE_DEL;
                                    headPhoneDel[1] = (byte) msg.arg1;                     // 要删除电话号码的索引
                                    realMsg = headPhoneDel;
                                    break;

                                // APP向单片机请求清空所有的电话号码序列
                                case PHONE_CLEAN:
                                    byte[] headPhoneClean = new byte[1];
                                    headPhoneClean[0] = PHONE_CLEAN;
                                    realMsg = headPhoneClean;
                                    break;
                            }
                            // 发送到单片机
                            mSockOutStream.write(realMsg);
                            mSockOutStream.flush();
                        } catch (Exception ew) {
                            Log.d(TAG, "alive failed");
                        }
                    }
                }
            }
        };

        // 创建存活脉搏子线程，也是请求数据2s请求一次
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!threadEndUi) {
                    // 如果连接成功就不断发送脉搏信息
                    if (mConnectedFlag) {
                        synchronized (ConfigActivity.class){           // 发送加锁
                            try {
                                mSockOutStream.write(APP_REQUEST);     // 发送数据请求
                                mSockOutStream.flush();
                            } catch (Exception ew) {
                                Log.d(TAG, "alive failed");
                            }
                        }
                    }
                    try {
                        Thread.sleep(2000);                    // 3s请求一次
                    } catch (Exception es) {

                    }
                }
            }
        }).start();


        // 监听wifi设置---------------------------------------------------------------------------------------------------------------------
        mWifiText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);
                final View view = View.inflate(ConfigActivity.this, R.layout.dialog_wifi, null);
                builder.setTitle("WIFI连接设置")
                .setView(view)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 从对话框取数据
                        EditText wifiSSID = (EditText) view.findViewById(R.id.wifi_ssid);
                        EditText wifiPWD  = (EditText) view.findViewById(R.id.wifi_password);
                        String wifiName = wifiSSID.getText().toString().trim();
                        String wifiPassword = wifiPWD.getText().toString().trim();
                        // 创建一条消息
                        Message msg = new Message();
                        msg.what = WIFI_SET;                                    // 表示wifi设置
                        msg.arg1 = wifiName.length();                           // 得到字符串长度，我们不准输入中文
                        msg.arg2 = wifiPassword.length();                       // 密码长度
                        msg.obj = wifiName+wifiPassword;                        // 顺序很重要
                        mSendHandler.sendMessage(msg);                          // 发送消息
                        // 更新显示
                        mWifiText.setText(wifiName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
            }
        });


        // 长按删除电话号码----------------------------------------------------------------------------------------------------------------
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);
                builder.setTitle("提示")
                        .setMessage("删除该电话号码？")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Message msgPhoneDel = new Message();
                                msgPhoneDel.what = PHONE_DEL;
                                msgPhoneDel.arg1 = phoneNumberList.get(position).getOrder();    // 单片机内电话索引
                                mSendHandler.sendMessage(msgPhoneDel);
                                // 更新UI
                                phoneNumberList.remove(position);
                                phoneAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();

                return false;
            }
        });


        // 监听lisView刷新事件----------------------------------------------------------------------------------------------


    }

    // 添加菜单项追加电话号码和清空电话号码----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.config, menu);
        return true;
    }

    // 监听菜单项选择添加电话号码
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.phone_add:                                      // 添加电话号码
                AlertDialog.Builder builder_add = new AlertDialog.Builder(ConfigActivity.this);
                final View view = View.inflate(ConfigActivity.this, R.layout.dialog_phone, null);
                builder_add.setTitle("添加电话号码")
                        .setView(view)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 从对话框编辑框获取数据
                                EditText phoneEdit = (EditText) view.findViewById(R.id.phone_edit);
                                String phoneNum = phoneEdit.getText().toString().trim();

                                // 将信息封装成消息发送给发送线程
                                Message msgAdd = new Message();
                                msgAdd.what = PHONE_ADD;
                                msgAdd.arg1 = phoneNum.length();
                                msgAdd.obj = phoneNum;
                                mSendHandler.sendMessage(msgAdd);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                break;

            case R.id.phone_clean:
                AlertDialog.Builder builder_clean = new AlertDialog.Builder(ConfigActivity.this);
                builder_clean.setTitle("提示")
                        .setMessage("清空列表电话？")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Message msgPhoneClean = new Message();
                                msgPhoneClean.what = PHONE_CLEAN;
                                mSendHandler.sendMessage(msgPhoneClean);
                                // 更新UI
                                phoneNumberList.clear();
                                phoneAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();

            case R.id.phone_cancel:

                break;
        }
        return true;
    }



    // 该函数用于拼接字符串----------------------------------------------------------------------------------------------
    public static byte[] unitByteArray(byte[] byte1,byte[] byte2){
        byte[] unitByte = new byte[byte1.length + byte2.length];
        System.arraycopy(byte1, 0, unitByte, 0, byte1.length);
        System.arraycopy(byte2, 0, unitByte, byte1.length, byte2.length);
        return unitByte;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        threadEndUi = true;                          // 告诉子线程UI线程退出了
    }
}
