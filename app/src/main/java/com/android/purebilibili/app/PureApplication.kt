package com.android.purebilibili.app

import android.app.Application
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.SearchRepository // 👈 导入

class PureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        TokenManager.init(this)

        // 👇👇👇 移除 DatabaseModule.init，改为初始化 Repository 👇👇👇
        SearchRepository.init(this)
    }
}