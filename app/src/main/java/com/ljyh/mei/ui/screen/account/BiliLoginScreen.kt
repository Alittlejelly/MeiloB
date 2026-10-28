package com.ljyh.mei.ui.screen.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.BiliQrLoginState
import com.ljyh.mei.data.repository.BiliRepository
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiliLoginViewModel @Inject constructor(
    private val biliRepository: BiliRepository,
) : ViewModel() {

    enum class Phase { Loading, QrReady, Scanned, Expired, Failed, Success }
    enum class PhonePhase { InputPhone, Captcha, CodeSent, Success }

    data class State(
        val phase: Phase = Phase.Loading,
        val qrBitmap: Bitmap? = null,
        val message: String? = null,
    )

    data class PhoneState(
        val phase: PhonePhase = PhonePhase.InputPhone,
        val tel: String = "",
        val message: String? = null,
        val sending: Boolean = false,
        val loggingIn: Boolean = false,
        val gt: String = "",
        val challenge: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _phoneState = MutableStateFlow(PhoneState())
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    private var qrcodeKey: String? = null
    private var polling = false

    private var captchaToken: String = ""
    private var captchaKey: String = ""

    init {
        viewModelScope.launch {
            // 已登录时不再展示登录页，直接返回（避免“登录后还能重复登录”的困惑）。
            if (biliRepository.isLoggedIn()) {
                _state.value = State(phase = Phase.Success)
                return@launch
            }
            generateQr()
        }
    }

    // ---- 扫码登录 ----

    fun generateQr() {
        viewModelScope.launch {
            polling = false
            _state.value = State(phase = Phase.Loading)
            when (val result = biliRepository.qrGenerate()) {
                is Resource.Success -> {
                    val (url, key) = result.data
                    qrcodeKey = key
                    _state.value = State(phase = Phase.QrReady, qrBitmap = decodeQr(url))
                    startPolling()
                }
                is Resource.Error -> _state.value = State(phase = Phase.Failed, message = result.message)
                Resource.Loading -> Unit
            }
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (polling) {
                val key = qrcodeKey ?: break
                when (val status = biliRepository.qrPoll(key)) {
                    is BiliQrLoginState.Confirmed -> {
                        polling = false
                        biliRepository.applyLoginCookies(status.cookies)
                        biliRepository.refreshAccount()
                        _state.value = _state.value.copy(phase = Phase.Success)
                        break
                    }
                    BiliQrLoginState.Scanned ->
                        _state.value = _state.value.copy(phase = Phase.Scanned)
                    BiliQrLoginState.Expired -> {
                        polling = false
                        _state.value = _state.value.copy(phase = Phase.Expired)
                        break
                    }
                    is BiliQrLoginState.Error -> {
                        polling = false
                        _state.value = State(phase = Phase.Failed, message = status.message)
                        break
                    }
                    BiliQrLoginState.Waiting -> Unit
                }
                delay(1500)
            }
        }
    }

    private fun decodeQr(url: String): Bitmap? = runCatching {
        val size = 512
        val matrix = QRCodeWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    // ---- 手机号短信登录 ----

    fun updateTel(tel: String) {
        _phoneState.value = _phoneState.value.copy(tel = tel.filter { it.isDigit() }.take(11))
    }

    /** 获取验证码：先取 geetest 参数，弹出滑块验证后再发送短信。 */
    fun requestCode() {
        val current = _phoneState.value
        if (current.sending || current.tel.length != 11) return
        viewModelScope.launch {
            _phoneState.value = current.copy(sending = true, message = null)
            try {
                when (val result = biliRepository.loginCaptchaToken()) {
                    is Resource.Success -> {
                        val (token, geetest) = result.data
                        captchaToken = token
                        if (geetest != null) {
                            _phoneState.value = _phoneState.value.copy(
                                phase = PhonePhase.Captcha,
                                gt = geetest.first,
                                challenge = geetest.second,
                            )
                        } else {
                            doSendSms(challenge = "", validate = "", seccode = "")
                        }
                    }
                    is Resource.Error -> _phoneState.value =
                        _phoneState.value.copy(message = result.message ?: "获取验证码失败")
                    Resource.Loading -> Unit
                }
            } finally {
                _phoneState.value = _phoneState.value.copy(sending = false)
            }
        }
    }

    /** 极验滑块完成回调。 */
    fun onCaptchaResult(challenge: String, validate: String, seccode: String) {
        viewModelScope.launch {
            doSendSms(challenge, validate, seccode)
        }
    }

    fun onCaptchaCancelled() {
        _phoneState.value = _phoneState.value.copy(phase = PhonePhase.InputPhone)
    }

    private suspend fun doSendSms(challenge: String, validate: String, seccode: String) {
        val tel = _phoneState.value.tel
        _phoneState.value = _phoneState.value.copy(sending = true, message = null)
        try {
            when (val result = biliRepository.sendLoginSms(tel, captchaToken, challenge, validate, seccode)) {
                is Resource.Success -> {
                    captchaKey = result.data
                    _phoneState.value = _phoneState.value.copy(phase = PhonePhase.CodeSent, message = null)
                }
                is Resource.Error -> _phoneState.value =
                    _phoneState.value.copy(message = result.message ?: "发送验证码失败")
                Resource.Loading -> Unit
            }
        } finally {
            _phoneState.value = _phoneState.value.copy(sending = false)
        }
    }

    fun confirmSms(code: String) {
        val current = _phoneState.value
        if (current.loggingIn || code.isBlank()) return
        viewModelScope.launch {
            _phoneState.value = current.copy(loggingIn = true, message = null)
            try {
                when (val status = biliRepository.loginWithSmsCode(current.tel, code, captchaKey)) {
                    is BiliQrLoginState.Confirmed -> {
                        biliRepository.applyLoginCookies(status.cookies)
                        biliRepository.refreshAccount()
                        _phoneState.value = _phoneState.value.copy(phase = PhonePhase.Success)
                    }
                    is BiliQrLoginState.Error ->
                        _phoneState.value = _phoneState.value.copy(message = status.message)
                    else -> Unit
                }
            } finally {
                _phoneState.value = _phoneState.value.copy(loggingIn = false)
            }
        }
    }
}

/** B 站登录页：扫码 + 手机号短信两种方式。 */
@Composable
fun BiliLoginScreen(
    viewModel: BiliLoginViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val state by viewModel.state.collectAsState()
    val phoneState by viewModel.phoneState.collectAsState()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    var mode by remember { mutableStateOf(0) } // 0 = 扫码, 1 = 手机号

    LaunchedEffect(state.phase, phoneState.phase) {
        if (state.phase == BiliLoginViewModel.Phase.Success ||
            phoneState.phase == BiliLoginViewModel.PhonePhase.Success
        ) {
            delay(500)
            navController.popBackStack()
        }
    }

    IosPinnedListPage(
        title = "登录哔哩哔哩",
        onNavigateBack = navController::navigateUp,
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item(key = "mode-tabs") {
            ModeTabs(selected = mode, onSelect = { mode = it })
        }

        if (mode == 0) {
            item(key = "qr-body") {
                QrLoginBody(state = state, onRetry = { viewModel.generateQr() })
            }
        } else {
            item(key = "phone-body") {
                PhoneLoginBody(
                    phoneState = phoneState,
                    onTelChange = viewModel::updateTel,
                    onRequestCode = viewModel::requestCode,
                    onConfirm = viewModel::confirmSms,
                )
            }
        }
    }

    if (phoneState.phase == BiliLoginViewModel.PhonePhase.Captcha) {
        GeetestDialog(
            gt = phoneState.gt,
            challenge = phoneState.challenge,
            onCancel = viewModel::onCaptchaCancelled,
            onResult = { challenge, validate, seccode ->
                viewModel.onCaptchaResult(challenge, validate, seccode)
            },
        )
    }
}

@Composable
private fun ModeTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
    ) {
        listOf("扫码登录", "手机号登录").forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun QrLoginBody(state: BiliLoginViewModel.State, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val phase = state.phase) {
            BiliLoginViewModel.Phase.Loading, BiliLoginViewModel.Phase.Success -> {
                Text(
                    if (phase == BiliLoginViewModel.Phase.Success) "登录成功" else "正在生成二维码…",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            BiliLoginViewModel.Phase.Failed -> {
                Text(
                    state.message ?: "加载失败，请检查网络",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                TextButton(onClick = onRetry) { Text("重新生成") }
            }
            else -> {
                val qr = state.qrBitmap
                if (qr == null) {
                    Text("正在生成二维码…", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "B站登录二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(220.dp),
                        )
                        if (phase == BiliLoginViewModel.Phase.Expired) {
                            Column(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.92f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("二维码已过期", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(10.dp))
                                TextButton(onClick = onRetry) { Text("重新生成") }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        when (phase) {
                            BiliLoginViewModel.Phase.Scanned -> "已扫码，请在手机上确认登录"
                            else -> "使用哔哩哔哩 App 扫码登录"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneLoginBody(
    phoneState: BiliLoginViewModel.PhoneState,
    onTelChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var smsCode by remember { mutableStateOf("") }
    val codeSent = phoneState.phase == BiliLoginViewModel.PhonePhase.CodeSent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextField(
            value = phoneState.tel,
            onValueChange = onTelChange,
            placeholder = { Text("手机号", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // 获取验证码内嵌在验证码框右侧，不再单独占一行夹在两个输入框之间。
        TextField(
            value = smsCode,
            onValueChange = { smsCode = it.filter { ch -> ch.isDigit() }.take(6) },
            placeholder = { Text("短信验证码", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) },
            singleLine = true,
            enabled = codeSent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(
                    onClick = onRequestCode,
                    enabled = !codeSent && phoneState.tel.length == 11 && !phoneState.sending,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        when {
                            codeSent -> "已发送"
                            phoneState.sending -> "发送中…"
                            else -> "获取验证码"
                        },
                        fontSize = 13.sp,
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onConfirm(smsCode) },
            enabled = codeSent && smsCode.isNotBlank() && !phoneState.loggingIn,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(if (phoneState.loggingIn) "登录中…" else "登录")
        }

        phoneState.message?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "验证码通过 B 站官方接口发送，仅用于登录本设备。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** 极验滑块验证弹窗：WebView 加载 gt.js，完成后通过 JS 桥回传 validate/seccode。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GeetestDialog(
    gt: String,
    challenge: String,
    onCancel: () -> Unit,
    onResult: (challenge: String, validate: String, seccode: String) -> Unit,
) {
    var pendingResult by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(pendingResult) {
        pendingResult?.let { (c, v, s) ->
            onResult(c, v, s)
        }
    }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 极验 popup 模式会在页面内自绘带遮罩的完整弹窗，因此让 WebView 全屏透明，
        // 验证窗口按手机实际尺寸渲染，不再被小卡片裁切。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 极验校验来源域名，必须以 bilibili.com 为基准源，否则滑块加载不出来。
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/131.0.0.0 Mobile Safari/537.36"
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onCaptcha(json: String) {
                                    runCatching {
                                        val obj = org.json.JSONObject(json)
                                        pendingResult = Triple(
                                            obj.optString("challenge"),
                                            obj.optString("validate"),
                                            obj.optString("seccode"),
                                        )
                                    }
                                }
                            },
                            "BiliBridge",
                        )
                        loadDataWithBaseURL(
                            "https://www.bilibili.com/",
                            buildGeetestHtml(gt, challenge),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // 全屏 WebView 会吞掉页面空白处的点击，提供一个明确的取消入口。
            // 必须放底部：窗口 decorFitsSystemWindows=false 时顶部与状态栏/刘海重叠，
            // 触摸被系统手势拦截，按钮会"看得见按不到"。
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 28.dp, vertical = 6.dp),
            ) {
                Text("取消验证", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

private fun buildGeetestHtml(gt: String, challenge: String): String {
    val gtJson = org.json.JSONObject.quote(gt)
    val challengeJson = org.json.JSONObject.quote(challenge)
    return """<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { display:flex; flex-direction:column; align-items:center; justify-content:center;
      min-height:100vh; background:transparent; font-family:-apple-system,sans-serif; }
    #captcha { width: 100%; max-width: 320px; }
    .err { color:#d32f2f; text-align:center; margin-top:10px; font-size:14px; }
  </style>
</head>
<body>
  <div id="captcha"></div>
  <div class="err" id="err-msg"></div>
  <script src="https://static.geetest.com/static/js/gt.0.4.9.js"></script>
  <script>
    function start() {
      initGeetest({
        gt: $gtJson,
        challenge: $challengeJson,
        offline: false,
        new_captcha: true,
        // bind 模式把滑块内联渲染进 #captcha（由 flex 居中）。popup 模式的
        // position:fixed 浮层在 decorFitsSystemWindows=false 的窗口里会锚到
        // 状态栏后面，验证条顶在屏幕最上方无法点击。
        product: 'bind',
        width: '100%',
        https: true
      }, function(captchaObj) {
        captchaObj.appendTo('#captcha');
        captchaObj.onReady(function() { captchaObj.verify(); });
        captchaObj.onSuccess(function() {
          var r = captchaObj.getValidate();
          window.BiliBridge.onCaptcha(JSON.stringify({
            challenge: r.geetest_challenge,
            validate: r.geetest_validate,
            seccode: r.geetest_seccode
          }));
        });
        captchaObj.onError(function() {
          document.getElementById('err-msg').textContent = '验证出错，请取消后重试';
        });
      });
    }
    function waitForBridge() {
      if (window.BiliBridge) { start(); } else { setTimeout(waitForBridge, 100); }
    }
    waitForBridge();
  </script>
</body>
</html>"""
}
