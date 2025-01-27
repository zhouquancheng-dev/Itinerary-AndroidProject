package com.example.common

import android.app.Application
import android.app.UiModeManager
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cn.jiguang.verifysdk.api.JVerificationInterface
import com.aleyn.router.LRouter
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig
import com.alibaba.sdk.android.httpdns.ranking.IPRankingBean
import com.blankj.utilcode.util.LogUtils
import com.example.common.config.AppConfig
import com.example.common.data.Constants.JG_TAG
import com.example.common.data.DatastoreKey.IS_PRIVACY_AGREE
import com.example.common.listener.im.sdk.V2TIMSDKListener
import com.example.common.util.sp.DataStoreUtils
import com.example.common.util.sp.DataStoreUtils.getBooleanSync
import com.hjq.toast.Toaster
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.imsdk.v2.V2TIMLogListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKConfig
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

abstract class BaseApplication : Application() {

    private val appJob by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    abstract fun isDebug(): Boolean

    @AppCompatDelegate.NightMode
    abstract fun getSystemNightMode(): Int

    inner class ApplicationLifecycleObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // 应用进入前台
        }

        override fun onStop(owner: LifecycleOwner) {
            // 应用进入后台
        }
    }

    companion object {
        private var instance: BaseApplication? = null

        /**
         * 全局context
         */
        @JvmStatic
        fun getApplication(): BaseApplication {
            return instance ?: throw IllegalStateException("Application is not created yet!")
        }

        /**
         * 获取当前是否为夜间模式
         */
        @JvmStatic
        fun isModeNightYes(): Boolean {
            val uiModeManager = getApplication().getSystemService(UI_MODE_SERVICE) as UiModeManager
            return uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppCompatDelegate.setDefaultNightMode(getSystemNightMode())
        observeLifecycle()

        // 并行初始化各个方法
        appJob.launch {
            initData()

            val initJobs = mutableListOf(
                async { initNormalSdks() },
                async(Dispatchers.Main) { initBugLy() }
            )

            // 如果用户已经同意隐私政策，则初始化需要隐私协议的SDK
            if (getBooleanSync(IS_PRIVACY_AGREE)) {
                initJobs += async {
                    initPrivacyRequiredSDKs()
                }
            }

            // 等待所有初始化任务完成
            initJobs.awaitAll()
        }
    }

    private fun observeLifecycle() {
        // 监听整个应用程序过程的生命周期
        ProcessLifecycleOwner.get().lifecycle.addObserver(ApplicationLifecycleObserver())
    }

    /**
     * 初始化数据
     */
    open fun initData() {
        // DataStore
        DataStoreUtils.init(this)
        LogUtils.getConfig().setLogSwitch(isDebug()).setLog2FileSwitch(false)
        // Toaster
        Toaster.init(this)
        Toaster.setView(if (isModeNightYes()) R.layout.toast_global_view_white else R.layout.toast_global_view_black)
        // MmKv
        MMKV.initialize(this)
        // LRouter
        LRouter.init(this)
        LRouter.setLogSwitch(isDebug())
    }

    /**
     * 初始化不会调用隐私相关的SDK
     */
    private fun initNormalSdks() {
        initAliHttpDNS()
    }

    /**
     * 初始化需要用户同意隐私政策的第三方SDK
     */
    open fun initPrivacyRequiredSDKs() {
        initJG()
        initTIM()
    }

    /**
     * bug 上报
     */
    private fun initBugLy() {
        CrashReport.initCrashReport(this, "1e43689b76", false)
    }

    /**
     * 初始化极光SDK
     */
    private fun initJG() {
        JVerificationInterface.setDebugMode(isDebug())
        JVerificationInterface.init(this) { code, result ->
            Log.i(JG_TAG, if (code == 8000) "极光SDK初始化成功" else "返回码: $code 信息: $result")
        }
    }

    /**
     * 初始化阿里云 https dns 加速
     */
    private fun initAliHttpDNS() {
        // 初始化配置，调用即可，不必处理返回值。
        val config = InitConfig.Builder()
            // 配置是否启用https，默认http
            .setEnableHttps(true)
            // 配置服务请求的超时时长，毫秒，默认2秒，最大5秒
            .setTimeoutMillis(2 * 1000)
            // 配置是否启用本地缓存，默认不启用
            .setEnableCacheIp(true)
            // 配置是否允许返回过期IP，默认允许
            .setEnableExpiredIp(true)
            // 配置ipv4探测域名
            .setIPRankingList(listOf(IPRankingBean("api.zyuxr.top", 9090)))
            // 配置接口来自定义缓存的ttl时间
            .configCacheTtlChanger { host, _, ttl ->
                if (TextUtils.equals(host, "api.zyuxr.top")) {
                    ttl * 10
                } else ttl
            }
            .build()

        HttpDns.init("113753", config)
    }

    /**
     * Tencent IM SDK
     * 使用无UI集成时需先初始化SDK
     */
    private fun initTIM() {
        val config = V2TIMSDKConfig().apply {
            logLevel = V2TIMSDKConfig.V2TIM_LOG_DEBUG
            logListener = object : V2TIMLogListener() {
                override fun onLog(logLevel: Int, logContent: String?) {

                }
            }
        }
        // V2TIMSDKListener 事件监听器
        V2TIMManager.getInstance().addIMSDKListener(V2TIMSDKListener())
        // 初始化SDK
        V2TIMManager.getInstance().initSDK(this, AppConfig.TENCENT_IM_APP_ID, config)
    }
}