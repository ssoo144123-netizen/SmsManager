package com.example.smsmanager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// لازم برای قابل انتخاب شدن به عنوان پیامک‌رسان پیش‌فرض
public class HeadlessSmsSendService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
