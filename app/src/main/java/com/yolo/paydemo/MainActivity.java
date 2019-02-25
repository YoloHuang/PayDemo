package com.yolo.paydemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.alipay.sdk.app.PayTask;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.yolo.paydemo.wxapi.WXPayEntryActivity;

import java.util.Map;

/**
 * @author yolo.huang
 * @date 2019/2/24
 */

public class MainActivity extends AppCompatActivity implements WXPayEntryActivity.WXPayListenter {

    IWXAPI iwxapi;
    public static final String DEFAULT_DESCRIBE = "测试订单";
    public static final Float DEFAULT_AMOUNT = 1F;
    public static final int SDK_PAY_FLAG = 1, SDK_AUTH_FLAG = 2;
    RelativeLayout wxPay, aliPay;
    EditText amount, describe;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        initView();
        initData();
    }


    private void initData() {
        requestPermission();
        iwxapi = WXAPIFactory.createWXAPI(this, PayConstants.WX_APP_ID);
    }

    @Override
    protected void onStart() {
        super.onStart();
        WXPayEntryActivity.setmListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        WXPayEntryActivity.setmListener(null);
    }


    private void initView() {
        amount = findViewById(R.id.amount);
        describe = findViewById(R.id.describe);
        wxPay = findViewById(R.id.rl_wechatPay);
        aliPay = findViewById(R.id.rl_aliPay);
        wxPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wxCanPay()) {
                    jumpToWXPay();
                }
            }
        });
        aliPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToAliPay();
            }
        });
    }

    private void jumpToAliPay() {

        Runnable payRunnable = new Runnable() {

            @Override
            public void run() {
                PayTask alipay = new PayTask(MainActivity.this);
                String orderInfo = AliPayUtil.getKidoOrderInfo(getPayBean(), alipay.getVersion());
                Map<String, String> result = alipay.payV2(orderInfo, true);
                Log.i("msp", result.toString());
                Message msg = new Message();
                msg.what = SDK_PAY_FLAG;
                msg.obj = result;
                mHandler.sendMessage(msg);
            }
        };

        Thread payThread = new Thread(payRunnable);
        payThread.start();

    }

    private void jumpToWXPay() {

        Runnable wxPayRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, Object> orderInfo = WXPayUtil.getWXOrderInfo(getPayBean(), MainActivity.this);
                    PayReq req = new PayReq();
                    req.appId = (String) orderInfo.get("appid");
                    req.partnerId = (String) orderInfo.get("partnerid");
                    req.prepayId = (String) orderInfo.get("prepayid");
                    req.nonceStr = (String) orderInfo.get("noncestr");
                    req.timeStamp = (String) orderInfo.get("timestamp");
                    req.packageValue = (String) orderInfo.get("package");
                    req.sign = (String) orderInfo.get("sign");
                    iwxapi.sendReq(req);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Thread payThread = new Thread(wxPayRunnable);
        payThread.start();
    }

    private PayBean getPayBean() {
        PayBean payBean = new PayBean();
        if (!TextUtils.isEmpty(amount.getText().toString())) {
            payBean.setAmount(Float.valueOf(amount.getText().toString()));
        } else {
            payBean.setAmount(DEFAULT_AMOUNT);
        }
        if (!TextUtils.isEmpty(describe.getText().toString())) {
            payBean.setDescribe(describe.getText().toString());
        } else {
            payBean.setDescribe(DEFAULT_DESCRIBE);
        }
        payBean.setTime(System.currentTimeMillis());
        payBean.setNotify_url("notify_url");
        return payBean;
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @SuppressWarnings("unused")
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDK_PAY_FLAG: {
                    @SuppressWarnings("unchecked")
                    PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                    /**
                     对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                     */
                    String resultInfo = payResult.getResult();// 同步返回需要验证的信息
                    String resultStatus = payResult.getResultStatus();
                    // 判断resultStatus 为9000则代表支付成功
                    if (TextUtils.equals(resultStatus, "9000")) {
                        // 该笔订单是否真实支付成功，需要依赖服务端的异步通知。
                        paymentSucceed();
                    } else {
                        // 该笔订单真实的支付结果，需要依赖服务端的异步通知。
                        paymentFailed();
                    }
                    break;
                }
                default:
                    break;
            }
        }
    };


    /**
     * 判断当前手机是否支持维信支付
     *
     * @return
     */
    private boolean wxCanPay() {
        try {
            if (!iwxapi.isWXAppInstalled()) {
                Toast.makeText(MainActivity.this, "请安装微信客户端", Toast.LENGTH_SHORT).show();
                return false;
            } else if (!iwxapi.isWXAppSupportAPI()) {
                Toast.makeText(MainActivity.this, "当前微信版本不支持支付", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "请安装最新微信客户端", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 获取权限使用的 RequestCode
     */
    private static final int PERMISSIONS_REQUEST_CODE = 1002;

    /**
     * 检查支付宝 SDK 所需的权限，并在必要的时候动态获取。
     * 在 targetSDK = 23 以上，READ_PHONE_STATE 和 WRITE_EXTERNAL_STORAGE 权限需要应用在运行时获取。
     * 如果接入支付宝 SDK 的应用 targetSdk 在 23 以下，可以省略这个步骤。
     */
    private void requestPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, PERMISSIONS_REQUEST_CODE);

        } else {
            Toast.makeText(this, "支付宝 SDK 已有所需的权限", Toast.LENGTH_SHORT);
        }
    }

    /**
     * 权限获取回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {

                // 用户取消了权限弹窗
                if (grantResults.length == 0) {
                    Toast.makeText(this, "无法获取支付宝 SDK 所需的权限, 请到系统设置开启", Toast.LENGTH_SHORT);
                    return;
                }

                // 用户拒绝了某些权限
                for (int x : grantResults) {
                    if (x == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(this, "无法获取支付宝 SDK 所需的权限, 请到系统设置开启", Toast.LENGTH_SHORT);
                        return;
                    }
                }

                // 所需的权限均正常获取
                Toast.makeText(this, "支付宝 SDK 所需的权限已经正常获取", Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    public void paymentSucceed() {
        Toast.makeText(this, "paymentSucceed", Toast.LENGTH_LONG).show();

    }

    @Override
    public void paymentCanceled() {
        Toast.makeText(this, "paymentCanceled", Toast.LENGTH_LONG).show();
    }

    @Override
    public void paymentFailed() {
        Toast.makeText(this, "paymentFailed", Toast.LENGTH_LONG).show();
    }
}
