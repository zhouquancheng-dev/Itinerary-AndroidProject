package com.example.common.base.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.example.common.util.ReflectionUtil
import java.lang.reflect.ParameterizedType

abstract class BaseVmBindFragment<VB : ViewBinding, VM : ViewModel> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = checkNotNull(_binding) {
            "ViewBinding is null. Ensure that you're accessing binding only between onViewCreated() and onDestroyView(). " +
                    "If you're accessing it in onDestroyView(), ensure it is before super.onDestroyView() is called."
        }

    private lateinit var viewModel: VM

    private var currentToast: Toast? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 使用反射初始化 ViewBinding
        _binding = ReflectionUtil.newViewBinding(inflater, javaClass)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[getViewModelClass()]
        initViews(savedInstanceState)
        initData()
        initListeners()
        setupObservers()
    }

    override fun onDestroyView() {
        _binding = null
        currentToast?.cancel()
        currentToast = null
        super.onDestroyView()
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun getViewModelClass(): Class<VM> {
        val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[1]
        return type as Class<VM>
    }

    // 初始化视图
    protected abstract fun initViews(savedInstanceState: Bundle?)

    // 初始化数据
    protected open fun initData() {}

    // 初始化监听器
    protected open fun initListeners() {}

    // 观察 ViewModel 的变化
    protected open fun setupObservers() {}

    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToastInternal(message, duration)
        } else {
            requireActivity().runOnUiThread {
                showToastInternal(message, duration)
            }
        }
    }

    private fun showToastInternal(message: String, duration: Int) {
        currentToast?.cancel()
        currentToast = Toast.makeText(requireActivity(), message, duration)
        currentToast?.show()
    }

    inline fun <reified T : Activity> navigateTo(bundle: Bundle? = null, flags: Int? = null) {
        val intent = Intent(requireActivity(), T::class.java).apply {
            bundle?.let { putExtras(it) }
            flags?.let { this.flags = it }
        }
        startActivity(intent)
    }

}
