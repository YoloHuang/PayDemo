## 关于接入微信、支付宝支付，看这篇就够了

### 前言
由于公司项目需要，安排我负责接入微信、支付宝支付功能。从最开始的申请账号到最后的功能完成，全程参与其中。现在功能完成了，正好写篇总结文档。顺便写了个Android端的demo，把整个功能都整合了一下。里面包括获取订单，签名，验证，调起支付，支付完成同步回调整个流程。配合总结文档食用最佳，[欢迎star](https://github.com/YoloHuang/PayDemo) ~

### 接入支付宝支付
支付宝接入相对而言比较简单，按照官方文档和demo基本没什么大问题。先看下支付宝支付的流程图。
![image](https://github.com/YoloHuang/picture/blob/master/paydemo/alipay2.png)
#### 体验demo
如果你已经按照[官方教程](https://docs.open.alipay.com/204/105297/)完成了接入支付宝的准备工作，那么用申请的appid和生成的公钥私钥替换demo中`PayConstants`的相关属性，就可以直接体验支付宝支付了。这其中需要注意RSA_PRIVATE 和 RSA2_PRIVATE 都是支付宝私钥，分别对应着RSA和RSA2签名算法。
目前新建应用只能使用RSA2签名算法，老应用还是可以使用RSA签名算法，不过在调用支付时需要标注是否是使用RSA2签名算法。

```
    /**
     * RSA_PRIVATE 和支付宝私钥
     */
    public static final String RSA2_PRIVATE = "XXXXXXX";
    /**
     * 支付宝APP_ID
     */
    public static final String ALI_APP_ID = "2018083061113973";
```

#### 接入支付SDK
下载最新版的[支付SDK](https://docs.open.alipay.com/54/104509)，将其复制到你项目的libs文件夹中。在项目的build.gradle中添加如下代码：

```
allprojects {
    repositories {

        // 添加下面的内容
        flatDir {
            dirs 'libs'
        }

        // ... jcenter() 等其他仓库
    }
}
```
在APP的build.gradle中，添加支付SDK的aar包依赖：

```
    compile (name: 'alipaySdk-15.5.9-20181123210601', ext: 'aar')
```
在AndroidManifest中添加以下权限：

```

    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <uses-permission android:name="android.permission.INTERNET" />

    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```
以上就完成了接入SDK的工作。这其中需要注意，WRITE_EXTERNAL_STORAGE和READ_PHONE_STATE权限需要在代码中动态获取。


#### 获取签名订单
按照接入流程图所示，第一步是先获取签名订单。为了防止应用的支付宝私钥暴露，这一步都是在服务器中完成。demo中为了方便方便体验，直接写在了客户端中。如果是开发线上APP，不建议这么做。支付宝获取签名订单需要以下几个步骤：
 * 将请求参数按照key=value&key=value方式拼接的未签名原始字符串。
 * 再对原始字符串进行签名
 * 最后对请求字符串的所有一级value（biz_content作为一个value）进行encode，编码格式按请求串中的charset为准，没传charset按UTF-8处理，获得最终的请求字符串

按照官方的[请求参数说明](https://docs.open.alipay.com/204/105465/)，demo中的代码示例如下：

```
    /**
     * 生成map格式订单
     *
     * @param app_id  支付平台中你APP的app_id
     * @param rsa2    是否是使用RSA2签名算法
     * @param payBean 包括订单金额，订单描述，订单详细内容，创建订单时间戳
     * @param version 接入的支付宝SDK版本号
     * @return
     */
    public static Map<String, String> buildOrderParamMap(String app_id, boolean rsa2, PayBean payBean, String version) {
        Map<String, String> keyValues = new HashMap<>();
        //支付宝的amount单位为元
        float amount = (payBean.getAmount()) / 100;

        keyValues.put("app_id", app_id);
        /**
         * biz_content参数包括所有的订单信息
         */
        keyValues.put("biz_content", "{\"timeout_express\":\"30m\",\"product_code\":\"QUICK_MSECURITY_PAY\",\"total_amount\":\"" + amount + "\",\"subject\":\"" + payBean.getBody() + "\",\"body\":\"我是测试数据\",\"out_trade_no\":\"" + getOutTradeNo() + "\"}");

        keyValues.put("charset", "utf-8");

        keyValues.put("method", "alipay.trade.app.pay");

        keyValues.put("sign_type", rsa2 ? "RSA2" : "RSA");

        keyValues.put("notify_url",payBean.getNotify_url());

        keyValues.put("timestamp", getDateToString(payBean.getTime(), "yyyy-MM-dd HH:mm:ss"));

        keyValues.put("version", version);

        return keyValues;
    }

    
        /**
     * 将map转换为string，构造原始支付订单参数信息
     *
     * @param map 支付订单参数
     * @return
     */
    public static String buildOrderParam(Map<String, String> map) {
        List<String> keys = new ArrayList<String>(map.keySet());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = map.get(key);
            sb.append(buildKeyValue(key, value, true));
            sb.append("&");
        }

        String tailKey = keys.get(keys.size() - 1);
        String tailValue = map.get(tailKey);
        sb.append(buildKeyValue(tailKey, tailValue, true));

        return sb.toString();
    }
```

先根据用户支付的订单参数，如订单价格、订单描述等，构建原始map格式订单。这其中需要注意，所有的订单参数（total_amount，timeout_express，subject）都一起存入biz_content中。然后将map格式原始订单参数，进行排序，转换为string格式。这其中还需要传入一个notify_url参数，后面会用到。


```
    /**
     * 将订单信息签名，encode
     *
     * @param orderParam    待签名的订单信息
     * @param rsaKey 支付宝私钥
     * @param rsa2   是否使用RSA2签名算法
     * @return
     */
    public static String getSign(String orderParam, String rsaKey, boolean rsa2) {
        //将排序后的序列进行签名
        String oriSign = sign(orderParam, rsaKey, rsa2);
        String encodedSign = "";

        try {
            encodedSign = URLEncoder.encode(oriSign, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "sign=" + encodedSign;
    }
    

    /**
     * 签名
     *
     * @param content    待签名信息
     * @param privateKey 支付宝私钥
     * @param rsa2       是否使用RSA2签名算法
     * @return
     */
    public static String sign(String content, String privateKey, boolean rsa2) {
        try {
            PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(
                    Base64.decode(privateKey));
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, "BC");
            PrivateKey priKey = keyFactory.generatePrivate(priPKCS8);

            java.security.Signature signature = java.security.Signature
                    .getInstance(getAlgorithms(rsa2));

            signature.initSign(priKey);
            signature.update(content.getBytes(DEFAULT_CHARSET));

            byte[] signed = signature.sign();

            return Base64.encode(signed);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
```
然后将排序后的订单信息进行签名和encode，获取最终请求支付的字符串。

#### 请求支付
支付宝请求支付，只需要调用alipay.payV2方法。

```
        Runnable payRunnable = new Runnable() {

            @Override
            public void run() {
                PayTask alipay = new PayTask(MainActivity.this);
                String  orderInfo = AliPayUtil.getKidoOrderInfo(getPayBean(),alipay.getVersion());
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
```
其中，同步支付结果通过message传递出来。这其中需要注意，支付结果必须通过服务器端返回的异步结果来进行最终确认。不能仅根据本地结果来显示给用户。

```
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
```

前面传入的支付参数中，有一个notify_url。支付宝服务器会把异步支付结果通过调用这个notify_url来返回给我们服务器，然后通过服务器返回给APP，APP通过与本地结果对比，来反馈给用户支付结果。

以上，整个支付流程就走完了。

### 微信支付
微信支付接入起来稍微有点复杂。[官方文档](https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=9_1)上面也有相应介绍。先看下官方流程图。  
![image](https://github.com/YoloHuang/picture/blob/master/paydemo/wxpay.png)     

总结下就是：
* 先准备本地下单信息，对下单信息进行排序、签名和XML格式化。
* 用本地下单信息调用微信统一下单接口，获取统一下单信息。并对该信息进行签名验证。
* 将微信返回的统一下单信息进行解析，并转化为调用微信支付所需格式。
* 调用微信支付，同步返回支付结果。
* 微信服务器调用传入的notify_url，异步返回支付结果。本地服务器将结果返回给APP，反馈给用户。

#### 接入微信SDK
首先在[官网](https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=11_1)上下载SDK，并将其复制到libs文件夹中。在APP的build.gradle中添加如下代码（一般Android studio会自动生成这段代码）:

```
    implementation fileTree(dir: 'libs', include: ['*.jar'])

```
微信支付的本地结果回调，需要在项目中新建wxapi文件夹，在其中添加WXPayEntryActivity，并继承IWXAPIEventHandler接口。当支付完成后，结果会同步到WXPayEntryActivity的onResp(BaseResp baseResp)方法中。

```
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
        //支付结果还需要发送给服务器确认支付状态
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
```

在AndroidManifest中添加WXPayEntryActivity

```
        <activity
            android:name=".wxapi.WXPayEntryActivity"
            android:exported="true"
            android:launchMode="singleTop" />
```
以上就完成了接入SDK的工作。如果已经接入过微信分享SDK，那么下载SDK和依赖这一步就可以跳过了，只需要再添加WXPayEntryActivity就行了。


#### 准备本地下单信息
自我感觉，这一步是最麻烦的。首先我们需要本地订单信息进行排序和签名。

```
    
    /**
     * 生成订单信息
     *
     * @param ip      当前手机IP
     * @param orderId 当前生成的外部订单号
     * @return
     */
    public static SortedMap<String, Object> prepareOrder(String ip, String orderId,PayBean payBean) {

        Map<String, Object> oparams = new LinkedHashMap<String, Object>();
        oparams.put("appid", PayConstants.WX_APP_ID);// 服务号的应用号
        oparams.put("body",payBean.getDescribe());// 商品描述
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
```
这一部分，上面代码中已经备注的很详细，就不展开解释了。签名之后，将本地下单信息进行xml格式化。

```
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
```
这其中需要注意，attach，body，sign三个参数加了CDATA标签，用于说明数据不被XML解析器解析。以上我们就准备好了本地下单信息，可以直接调用微信统一下单接口，获取统一下单订单信息了。

#### 获取统一下单信息

先调用接口获取统一下单信息。我这里使用的是最基本的httpsRequest。

```
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
```
服务器返回的下单信息是xml格式，我们需要先进行格式转换。

```
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

```
检验API返回的数据里面的签名是否合法，避免数据在传输的过程中被第三方篡改。

```
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
```
最后，还需要对返回的订单信息进行整理，来得到我们需要的最终调用微信支付所需要的数据。

```
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
```

这其中需要注意，我们还是需要将最终的订单信息进行重新排序，然后重新签名。这里的签名方式一定要与统一下单接口使用的一致。具体可以参照[官方签名文档](https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=4_3)。

#### 调用支付
总算是走到这一步了，维信调用支付需要先判断当前手机是否有安装维信，维信版本是否支持支付功能。

```
    /**
     * 判断当前手机是否支持维信支付
     * @return
     */
    private boolean wxCanPay(){
        try{
            if(!iwxapi.isWXAppInstalled()){
                Toast.makeText(MainActivity.this,"请安装微信客户端", Toast.LENGTH_SHORT).show();
                return false;
            }else if(!iwxapi.isWXAppSupportAPI()){
                Toast.makeText(MainActivity.this, "当前微信版本不支持支付", Toast.LENGTH_SHORT).show();
                return false;
            }
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "请安装最新微信客户端", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
```
调用微信支付

```
        Runnable wxPayRunnable = new Runnable() {
            @Override
            public void run() {
                try{
                    Map<String,Object> orderInfo = WXPayUtil.getWXOrderInfo(getPayBean(),MainActivity.this);
                    PayReq req = new PayReq();
                    req.appId = (String)orderInfo.get("appid");
                    req.partnerId = (String)orderInfo.get("partnerid");
                    req.prepayId = (String)orderInfo.get("prepayid");
                    req.nonceStr = (String)orderInfo.get("noncestr");
                    req.timeStamp = (String)orderInfo.get("timestamp");
                    req.packageValue = (String)orderInfo.get("package");
                    req.sign = (String)orderInfo.get("sign");
                    iwxapi.sendReq(req);
                }catch (Exception e ){
                    e.printStackTrace();
                }


            }
        };
        Thread payThread = new Thread(wxPayRunnable);
        payThread.start();
```
按照第一步接入准备中所说，支付结果会返回到WXPayEntryActivity的onResp(BaseResp baseResp)。我们需要获取到结果后，finish掉这个activity。然后在支付结果页面等待服务器返回异步支付结果。

### 总结
接入两个支付，收获还是有的。设计到支付相关，安全是最重要的。所以签名加密等所有操作都是在服务器端进行的，最终落实到APP端，也只是写个界面，调个接口而已。但是相关的流程和逻辑还是有必要理一理，总结一下的。最后再附上demo的地址，[欢迎大家star](https://github.com/YoloHuang/PayDemo)
