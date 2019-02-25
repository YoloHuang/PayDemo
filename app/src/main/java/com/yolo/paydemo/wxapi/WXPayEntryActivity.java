package com.yolo.paydemo.wxapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.yolo.paydemo.PayConstants;

/**
 * Created by yolo.huang on 2018/9/17.
 */

public class WXPayEntryActivity extends Activity implements IWXAPIEventHandler {


    private IWXAPI api;

    private static WXPayListenter mListener;

    public static void setmListener(WXPayListenter listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        api = WXAPIFactory.createWXAPI(this, PayConstants.WX_APP_ID, false);

        try {
            api.handleIntent(getIntent(), this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        api.handleIntent(intent, this);
    }


    @Override
    public void onReq(BaseReq baseReq) {

    }

    @Override
    public void onResp(BaseResp baseResp) {
        //有时候支付结果还需要发送给服务器确认支付状态
        if (baseResp.getType() == ConstantsAPI.COMMAND_PAY_BY_WX) {
            if (mListener != null) {
                if (baseResp.errCode == 0) {
                    mListener.paymentSucceed();
                } else if (baseResp.errCode == -2) {
                    mListener.paymentCanceled();
                } else {
                    mListener.paymentFailed();
                }
            }
            finish();
        }

    }

    public interface WXPayListenter {
        void paymentSucceed();

        void paymentCanceled();

        void paymentFailed();
    }

}
