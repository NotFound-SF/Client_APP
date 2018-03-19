package cn.learn.real;

import java.nio.ByteOrder;

import javolution.io.Struct;

/**
 * Created by ST on 2018/1/26.
 */

public class DataFormat extends Struct{
    public final Unsigned32 userID      = new Unsigned32();
    public final Unsigned32 deviceID    = new Unsigned32();
    public final Unsigned8  operation   = new Unsigned8();               // 操作选项
    public final Unsigned8  warning     = new Unsigned8();               // 警告标志
    public final Unsigned8  ledSwitch   = new Unsigned8();               // LED开关
    public final Unsigned8  ledAuto     = new Unsigned8();               // 开灯模式下自动模式
    public final Unsigned8  powerSwitch = new Unsigned8();               // 电源开关
    public final Unsigned8  rainStatus  = new Unsigned8();               // 下雨状态
    public final Unsigned8  window      = new Unsigned8();               // 窗户开度
    public final Unsigned8  windowAuto  = new Unsigned8();               // 开窗模式下下雨自动关窗
    public final Unsigned16 temperature = new Unsigned16();              // 温度
    public final Unsigned16 current     = new Unsigned16();              // 电流大小


    @Override
    public boolean isPacked() {
        return true;                                                     // 对齐
    }

    // 小端对齐
    @Override
    public ByteOrder byteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }
}
