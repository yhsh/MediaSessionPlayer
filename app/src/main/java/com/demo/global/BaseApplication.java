package com.demo.global;

import android.app.Application;
import android.content.Context;

/**
 * author:杨旭东
 * 创建时间:2021/9/26
 */
public class BaseApplication extends Application {

    public static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
    }
}
