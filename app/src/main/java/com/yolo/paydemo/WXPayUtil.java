package com.yolo.paydemo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;

import kotlin.text.Charsets;


/**
 * @author yolo.huang
 * @date 2018/9/21
 */

public class WXPayUtil {

    public static final String TAG = WXPayUtil.class.getSimpleName();

    /**
     * 获取微信订单信息
     * 先生成下单信息，然后访问微信的订单生成URL，获取到微信服务器订单信息(主要需要的是统一订单号)
     * 对微信服务器返回的订单信息进行签名验证，若没有问题则将服务器返回的订单信息转换为JSONObject返回
     *
     * @param orderParamsBean 包括订单金额，订单描述，订单详细内容，创建订单时间戳
     * @param context         上下文
     * @return
     */
    public static Map<String, Object> getWXOrderInfo(PayBean orderParamsBean, Context context) {

        String orderId = getOutTradeNo();
        //生成本地下单信息
        SortedMap<String, Object> parameters = prepareOrder(getIPAddress(context), orderId, orderParamsBean);
        //对本地下单信息签名
        parameters.put("sign", createSign(Charsets.UTF_8.toString(), parameters));
        //将本地下单信息进行XML格式转换，因为获取微信订单，输入值格式为XML格式
        String requestXML = getRequestXml(parameters);
        String responseStr = "";
        //获取微信统一订单信息
        try {
            responseStr = httpsRequest(
                    PayConstants.UNIFIED_ORDER_URL, "POST", requestXML);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> resultMap = readStringXmlOut(responseStr);
        //对服务器返回的统一订单信息进行签名验证
        if (!checkIsSignValidFromResponseString(resultMap)) {
            Log.e(TAG, "微信统一下单失败,签名可能被篡改 " + responseStr);
            return null;
        }
        // 解析结果 resultStr

        if (resultMap != null && "FAIL".equals(resultMap.get("return_code"))) {
            Log.e(TAG, "微信统一下单失败,订单编号: " + orderId + " 失败原因:"
                    + resultMap.get("return_msg"));
            return null;
        }
        /**
         * 这里有个坑爹的地方，微信返回的统一订单信息，跟调用微信支付所需要的订单信息并不一样
         * 需要更换key和增加新的值，然后排序后签名来获取到调用微信支付所需要的订单信息
         */
        return getPayResultMap(resultMap);

    }

    /**
     * 提取微信服务器返回的下单数据，组合成调用微信支付所需的map
     * 对map进行排序签名
     *
     * @param resultMap
     * @return
     */
    private static Map<String, Object> getPayResultMap(Map<String, Object> resultMap) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("appid", resultMap.get("appid"));
        map.put("partnerid", resultMap.get("mch_id"));
        map.put("prepayid", resultMap.get("prepay_id"));
        map.put("package", "Sign=WXPay");
        map.put("noncestr", resultMap.get("nonce_str"));
        map.put("timestamp", getTimeStamp());
        //对map进行排序
        SortedMap<String, Object> sortedMap = sortMap(map);
        //对map进行签名，并将签名加入map
        sortedMap.put("sign", createSign(Charsets.UTF_8.toString(), sortedMap));
        return sortedMap;
    }


    /**
     * 生成订单信息
     *
     * @param ip      当前手机IP
     * @param orderId 当前生成的外部订单号
     * @return
     */
    public static SortedMap<String, Object> prepareOrder(String ip, String orderId, PayBean payBean) {

        Map<String, Object> oparams = new LinkedHashMap<String, Object>();
        oparams.put("appid", PayConstants.WX_APP_ID);// 服务号的应用号
        oparams.put("body", payBean.getDescribe());// 商品描述
        oparams.put("mch_id", PayConstants.WX_CHD_ID);// 商户号
        oparams.put("nonce_str", CreateNoncestr());// 16随机字符串(大小写字母加数字)
        oparams.put("out_trade_no", orderId);// 商户订单号
        oparams.put("total_fee", payBean.getAmount());// 支付金额 单位分 注意:前端负责传入分
        oparams.put("spbill_create_ip", ip);// IP地址
        oparams.put("notify_url", payBean.getNotify_url()); // 微信回调地址
        oparams.put("trade_type", PayConstants.TRADE_TYPE);// 支付类型 app
        return sortMap(oparams);
    }


    /**
     * 默认16 位随机字符串
     *
     * @return
     */
    public static String CreateNoncestr() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String res = "";
        for (int i = 0; i < 16; i++) {
            Random rd = new Random();
            res += chars.charAt(rd.nextInt(chars.length() - 1));
        }
        return res;
    }


    /**
     * 获取时间戳
     *
     * @return
     */
    private static long getTimeStamp() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 对map根据key进行排序 ASCII 顺序
     *
     * @param
     * @return
     */
    public static SortedMap<String, Object> sortMap(Map<String, Object> map) {

        List<Map.Entry<String, Object>> infoIds = new ArrayList<Map.Entry<String, Object>>(
                map.entrySet());
        // 排序
        Collections.sort(infoIds, new Comparator<Map.Entry<String, Object>>() {
            @Override
            public int compare(Map.Entry<String, Object> o1,
                               Map.Entry<String, Object> o2) {
                // return (o2.getValue() - o1.getValue());//value处理
                return (o1.getKey()).toString().compareTo(o2.getKey());
            }
        });
        // 排序后
        SortedMap<String, Object> sortmap = new TreeMap<String, Object>();
        for (int i = 0; i < infoIds.size(); i++) {
            String[] split = infoIds.get(i).toString().split("=");
            sortmap.put(split[0], split[1]);
        }
        return sortmap;
    }

    /**
     * 获取外部订单号，要求外部订单号必须唯一，所以采用随机数跟时间戳混合的形式来保证唯一性
     *
     * @return
     */
    private static String getOutTradeNo() {
        SimpleDateFormat format = new SimpleDateFormat("MMddHHmmss", Locale.getDefault());
        Date date = new Date();
        String key = format.format(date);

        Random r = new Random();
        key = key + r.nextInt();
        key = key.substring(0, 15);
        return key;
    }

    /**
     * 获取当前手机IP
     *
     * @param context 上下文
     * @return
     */
    public static String getIPAddress(Context context) {
        NetworkInfo info = ((ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//当前使用2G/3G/4G网络
                try {
                    //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }

            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//当前使用无线网络
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());//得到IPV4地址
                return ipAddress;
            }
        } else {
            //当前无网络连接,请在设置中打开网络
        }
        return null;
    }

    /**
     * 将得到的int类型的IP转换为String类型
     *
     * @param ip
     * @return
     */
    public static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    /**
     * 签名工具
     *
     * @param characterEncoding 编码格式 UTF-8
     * @param parameters        请求参数
     * @return
     */
    public static String createSign(String characterEncoding,
                                    Map<String, Object> parameters) {
        StringBuffer sb = new StringBuffer();
        Iterator<Map.Entry<String, Object>> it = parameters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) it.next();
            String key = (String) entry.getKey();
            Object value = entry.getValue();//去掉带sign的项
            if (null != value && !"".equals(value) && !"sign".equals(key)
                    && !"key".equals(key)) {
                sb.append(key + "=" + value + "&");
            }
        }
        sb.append("key=" + PayConstants.WX_KEY);
        //注意sign转为大写
        return MD5Encode(sb.toString(), characterEncoding).toUpperCase();
    }

    /**
     * MD5
     *
     * @param origin
     * @param charsetname
     * @return
     */
    public static String MD5Encode(String origin, String charsetname) {
        String resultString = null;
        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("MD5");
            if (charsetname == null || "".equals(charsetname)) {
                resultString = byteArrayToHexString(md.digest(resultString
                        .getBytes()));
            } else {
                resultString = byteArrayToHexString(md.digest(resultString
                        .getBytes(charsetname)));
            }
        } catch (Exception exception) {
        }
        return resultString;
    }


    public static String byteArrayToHexString(byte b[]) {
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            resultSb.append(byteToHexString(b[i]));
        }
        return resultSb.toString();
    }

    public static String byteToHexString(byte b) {
        int n = b;
        if (n < 0) {
            n += 256;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        return hexDigits[d1] + hexDigits[d2];
    }

    public static final String hexDigits[] = {"0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};


    /**
     * 将请求参数转换为xml格式的string
     *
     * @param parameters 请求参数
     * @return
     */
    public static String getRequestXml(SortedMap<String, Object> parameters) {
        StringBuffer sb = new StringBuffer();
        sb.append("<xml>");
        Iterator<Map.Entry<String, Object>> iterator = parameters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator.next();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if ("attach".equalsIgnoreCase(key) || "body".equalsIgnoreCase(key)
                    || "sign".equalsIgnoreCase(key)) {
                sb.append("<" + key + ">" + "<![CDATA[" + value + "]]></" + key + ">");
            } else {
                sb.append("<" + key + ">" + value + "</" + key + ">");
            }
        }
        sb.append("</xml>");
        return sb.toString();
    }


    /**
     * 发送https请求
     *
     * @param requestUrl    请求地址
     * @param requestMethod 请求方式（GET、POST）
     * @param outputStr     提交的数据
     * @return 返回微信服务器响应的信息
     * @throws Exception
     */
    public static String httpsRequest(String requestUrl, String requestMethod,
                                      String outputStr) throws Exception {
        try {

            URL url = new URL(requestUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            // 设置请求方式（GET/POST）
            conn.setRequestMethod(requestMethod);
            conn.setRequestProperty("content-type",
                    "application/x-www-form-urlencoded");
            // 当outputStr不为null时向输出流写数据
            if (null != outputStr) {
                OutputStream outputStream = conn.getOutputStream();
                // 注意编码格式
                outputStream.write(outputStr.getBytes("UTF-8"));
                outputStream.close();
            }
            // 从输入流读取返回内容
            InputStream inputStream = conn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(
                    inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(
                    inputStreamReader);
            String str = null;
            StringBuffer buffer = new StringBuffer();
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            // 释放资源
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
            inputStream = null;
            conn.disconnect();
            return buffer.toString();
        } catch (ConnectException ce) {
            Log.e(TAG, "连接超时：{}" + ce);
            throw new RuntimeException("链接异常" + ce);
        } catch (Exception e) {
            Log.e(TAG, "https请求异常：{}" + e);
            throw new RuntimeException("https请求异常" + e);
        }


    }

    /**
     * 检验API返回的数据里面的签名是否合法，避免数据在传输的过程中被第三方篡改
     *
     * @param map API返回的下单数据
     * @return API签名是否合法
     * @throws
     * @throws
     * @throws
     */
    public static boolean checkIsSignValidFromResponseString(Map<String, Object> map) {

        try {
            String signFromAPIResponse = map.get("sign").toString();
            if ("".equals(signFromAPIResponse) || signFromAPIResponse == null) {
                Log.d(TAG, "API返回的数据签名数据不存在，有可能被第三方篡改!!!");
                return false;
            }

            //清掉返回数据对象里面的Sign数据（不能把这个数据也加进去进行签名），然后用签名算法进行签名
            map.put("sign", "");
            //将API返回的数据根据用签名算法进行计算新的签名，用来跟API返回的签名进行比较
            String signForAPIResponse = createSign(Charsets.UTF_8.toString(), map);
            Log.d(TAG, "服务器回包里面的签名是:" + signFromAPIResponse + "==服务器回包数据签名是：" + signForAPIResponse);
            if (!signForAPIResponse.equals(signFromAPIResponse)) {
                //签名验不过，表示这个API返回的数据有可能已经被篡改了
                Log.d(TAG, "API返回的数据签名验证不通过，有可能被第三方篡改!!!");
                return false;
            }
            Log.d(TAG, "恭喜，API返回的数据签名验证通过!!!");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param xml
     * @return Map
     * @description 将xml字符串转换成map
     */
    public static Map<String, Object> readStringXmlOut(String xml) {
        Map<String, Object> map = new HashMap<String, Object>();
        Document doc = null;
        try {
            doc = DocumentHelper.parseText(xml); // 将字符串转为XML
            Element rootElt = doc.getRootElement(); // 获取根节点
            @SuppressWarnings("unchecked")
            List<Element> list = rootElt.elements();// 获取根节点下所有节点
            for (Element element : list) { // 遍历节点
                map.put(element.getName(), element.getText()); // 节点的name为map的key，text为map的value
            }
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

}
