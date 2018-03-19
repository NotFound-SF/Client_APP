package cn.learn.real;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;


/**
 * Created by ST on 2018/1/28.
 */

public class PhoneAdapter extends ArrayAdapter<PhoneNumber>{
    private int itemResourceID;

    public PhoneAdapter(Context context, int itemResourceID, List<PhoneNumber> objects) {
        super(context, itemResourceID, objects);
        this.itemResourceID = itemResourceID;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View        view;
        ViewHolder  viewHolder;
        PhoneNumber phoneNumber = getItem(position);

        if (null == convertView) {
            view = LayoutInflater.from(getContext()).inflate(itemResourceID, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) view.findViewById(R.id.phone_image);
            viewHolder.textView = (TextView) view.findViewById(R.id.phone_number);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.textView.setText(phoneNumber.getPhoneNum());
        viewHolder.imageView.setImageAlpha(phoneNumber.getImageId());

        return view;
    }

    class ViewHolder {
        ImageView imageView;
        TextView  textView;
    }
}




class PhoneNumber {
    private String phoneNum;
    private byte   order;                                     // 删除时需要用到的索引
    private int    imageId;

    public PhoneNumber(String phoneNum, int imageId, byte order) {
        this.order = order;
        this.imageId = imageId;
        this.phoneNum = phoneNum;
    }

    // 必须重写equals函数，判断对象是否相等的依据

    @Override
    public boolean equals(Object obj) {
        return order == ((PhoneNumber)obj).order;
    }

    public byte getOrder() {
        return order;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public int getImageId() {
        return imageId;
    }
}
