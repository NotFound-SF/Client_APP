package cn.learn.real;

import java.nio.ByteOrder;

import javolution.io.Struct;

/**
 * Created by ST on 2018/3/18.
 */

class InfoStruct extends Struct {

/*------------------------------------------------------ 必须定义为静态内部类 --------------------------------------------*/

    public static class PhoneInfo extends Struct {
        public final Unsigned8    m_phone_l = new Unsigned8();                        // 电话号码长度
        public final UTF8String   m_phone   = new UTF8String(12);             // 字符格式电话号码

        // 设置对齐模式
        @Override
        public boolean isPacked() {
            return true;                                                               // 对齐
        }

        // 小端对齐
        @Override
        public ByteOrder byteOrder() {
            return ByteOrder.LITTLE_ENDIAN;
        }
    }

    public final Unsigned8[]  m_wifi_pwd    = array(new Unsigned8[32]);               // wifi密码项不需要关心,占位
    public final Unsigned8    m_wifi_ssid_l = new Unsigned8();                        // wifi热点名字长度
    public final UTF8String   m_wifi_ssid   = new UTF8String(31);            // 字符型wifi热点名字
    public final PhoneInfo[]  m_phone_info  = array(new PhoneInfo[32]);               // 必须被定义为内部类


    /*------------------------------------------  重写函数  --------------------------------------------------*/
    // 设置对齐模式
    @Override
    public boolean isPacked() {
        return true;                                                                 // 对齐
    }

    // 小端对齐
    @Override
    public ByteOrder byteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }
}
