package com.android.purebilibili.app

import android.app.Application
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager

class PureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化网络模块上下文
        NetworkModule.init(this)
        // 初始化 Token 管理
        TokenManager.init(this)
    }
}