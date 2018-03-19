package cn.learn.real;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity  implements View.OnClickListener{
    private static final String  CONFIG_FILE = "userProfile";          // 配置文件名
    private static final String  LOGIN_FLAG = "loginFlag";             // 登录到了登录界面
    private static final String  REMEMBER_FLAG = "rememberFlag";       // 是否记住信息键
    private static final String  USER_ID = "userID";                   // 用户ID字段
    private static final String  DEVICE_ID = "deviceID";               // 设备ID字段

    private TextView   mConfigText = null;
    private EditText   mUserID = null;
    private EditText   mDeviceID  = null;
    private Button     mLoginButton = null;
    private CheckBox   mRemember = null;
    private TextView   mAboutAPP = null;
    private SharedPreferences mConfig = null;
    private SharedPreferences.Editor mConfigEditor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MyApplication.getInstance().addActivity(this);                          // 添加到队列

        // 关联到布局文件
        mUserID      = (EditText) findViewById(R.id.userID);
        mDeviceID    = (EditText) findViewById(R.id.deviceID);
        mRemember    = (CheckBox) findViewById(R.id.rememberCheck);
        mAboutAPP    = (TextView) findViewById(R.id.textAbout);
        mLoginButton = (Button) findViewById(R.id.loginButton);
        mConfigText  = (TextView) findViewById(R.id.config);

        // 设置监听器
        mAboutAPP.setOnClickListener(this);
        mLoginButton.setOnClickListener(this);
        mConfigText.setOnClickListener(this);

        // 从配置文件获取编辑框信息
        mConfig = getSharedPreferences(CONFIG_FILE, MODE_PRIVATE);
        boolean isLogin = mConfig.getBoolean(LOGIN_FLAG, false);
        boolean isRemember = mConfig.getBoolean(REMEMBER_FLAG, false);
        if (isLogin && isRemember) {
            // 跳转到操作界面
            String pUserID   = mConfig.getString(USER_ID, "");
            String pDeviceID = mConfig.getString(DEVICE_ID, "");
            mUserID.setText(pUserID);
            mDeviceID.setText(pDeviceID);
            mRemember.setChecked(true);
            int userID = Integer.parseInt(pUserID);
            int deviceID = Integer.parseInt(pDeviceID);
            Intent intent = HandlerActivity.newIntent(MainActivity.this,
                    userID, deviceID);
            startActivity(intent);
        } else if (isRemember) {
            String pUserID   = mConfig.getString(USER_ID, "");
            String pDeviceID = mConfig.getString(DEVICE_ID, "");
            mUserID.setText(pUserID);
            mDeviceID.setText(pDeviceID);
            mRemember.setChecked(true);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.loginButton:
                String pUserIn = mUserID.getText().toString().trim();
                String pDeviceIn = mDeviceID.getText().toString().trim();
                // 判断输入框是否为空
                if (TextUtils.isEmpty(pUserIn) || TextUtils.isEmpty(pDeviceIn)) {
                    Toast.makeText(MainActivity.this, "用户ID或者设备ID为空",
                            Toast.LENGTH_SHORT).show();
                } else {
                    mConfigEditor = mConfig.edit();
                    if (mRemember.isChecked()) {
                        mConfigEditor.putString(USER_ID, pUserIn);
                        mConfigEditor.putString(DEVICE_ID, pDeviceIn);
                        mConfigEditor.putBoolean(REMEMBER_FLAG, true);
                        mConfigEditor.putBoolean(LOGIN_FLAG, true);
                    } else {
                        mConfigEditor.clear();
                    }
                    mConfigEditor.apply();                             // 提交用户信息
                    // 跳转到操作界面
                    int userID = Integer.parseInt(pUserIn);
                    int deviceID = Integer.parseInt(pDeviceIn);
                    Intent intent = HandlerActivity.newIntent(MainActivity.this,
                            userID, deviceID);
                    startActivity(intent);
                }
                break;

            case R.id.config:
                // 跳转到配置界面
                Intent intentAbout = ConfigActivity.newIntent(MainActivity.this);
                startActivity(intentAbout);
                break;

            case R.id.textAbout:
                // 跳转到About界面
                Intent intentConfig = AboutActivity.newIntent(MainActivity.this);
                startActivity(intentConfig);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mRemember.isChecked()) {                             // 退出时未选择
            mConfigEditor = mConfig.edit();
            mConfigEditor.clear();
            mConfigEditor.apply();                                // 提交
        }
    }
}

