package com.yolo.paydemo;

/**
 * Created by yolo.huang on 2018/9/21.
 */

public class PayConstants {

    /**
     * RSA_PRIVATE 和 RSA2_PRIVATE 是支付宝私钥，分别对应着RSA和RSA2签名算法。
     * 目前新建应用只能使用RSA2签名算法，老应用还是可以使用RSA签名算法
     */
    public static final String RSA2_PRIVATE = "XXXXXXX";
    public static final String RSA_PRIVATE = "XXXXXXX";
    /**
     * 支付宝APP_ID
     */
    public static final String ALI_APP_ID = "XXXXXX";

    /**
     * 支付结果回调接口
     */
    public static final String NOTIFY_URL = "http://XXXXXXX";


    /**
     * 微信appid，微信商户号
     */
    public static String WX_APP_ID = "XXXXXX";
    public static String WX_CHD_ID = "XXXXXXX";
    /**
     * 微信统一下单接口
     */
    public static final String UNIFIED_ORDER_URL = "https://api.mch.weixin.qq.com/pay/unifiedorder";
    public static String TRADE_TYPE = "APP";
    /**
     * 微信KEY
     */
    public static String WX_KEY = "XXXXXX";


}
