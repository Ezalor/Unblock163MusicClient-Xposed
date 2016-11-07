package bin.xposed.Unblock163MusicClient;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.findMethodsByExactParameters;
import static de.robv.android.xposed.XposedHelpers.newInstance;

class CloudMusicPackage {
    static final String PACKAGE_NAME = "com.netease.cloudmusic";
    static String version;
    private static WeakReference<List<String>> allClassList = new WeakReference<>(null);
    private static ClassLoader classLoader;


    static void init(XC_LoadPackage.LoadPackageParam lpparam, Application application) throws PackageManager.NameNotFoundException {
        classLoader = lpparam.classLoader;
        version = application.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
    }


    private static List<String> getAllClasses() throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        List<String> list = allClassList.get();
        if (list == null) {
            list = MultiDexHelper.getAllClasses(NeteaseMusicApplication.getApplication());
            allClassList = new WeakReference<>(list);
        }
        return list;
    }

    static class MusicInfo {
        static Class CLASS = findClass("com.netease.cloudmusic.meta.MusicInfo", classLoader);
        static String source;


        static String get3rdSourceString(Object musicInfo) throws JSONException, PackageManager.NameNotFoundException, IllegalAccessException {
            if (source == null) {
                source = Utility.getModuleResources().getString(R.string.source);
            }
            long musicId = (long) callMethod(musicInfo, "getMatchedMusicId");
            int br = (int) callMethod(musicInfo, "getCurrentBitRate");
            if (br > 0) {
                String dir = CloudMusicPackage.NeteaseMusicApplication.getMusicCacheDir();
                String start = String.format("%s-%s", musicId, br);
                String end = ".xp!";
                File file = Utility.findFirstFile(dir, start, end);
                if (file != null) {
                    String jsonStr = Utility.readFile(file);
                    Song song = new Song(new JSONObject(jsonStr));
                    if (song.isMatchedSong()) {
                        return String.format("(%s%s：%s - %s)",
                                source,
                                song.matchedPlatform,
                                song.matchedArtistName,
                                song.matchedSongName);
                    }
                }
            }
            return null;
        }
    }

    static class Mam {
        static String prefix;

        static void setPrefix(String prefix) {
            Mam.prefix = prefix;
        }

        static Class findMamClass(Class clazz) {
            assert prefix != null;
            return findClass(prefix + clazz.getName(), classLoader);
        }
    }

    static class CAC {
        static void refreshMyPlaylist() throws IllegalAccessException, PackageManager.NameNotFoundException, IOException {
            Class caInterface = version.startsWith("3.0") ?
                    findClass("com.netease.cloudmusic.b.a", classLoader) :
                    findClass("com.netease.cloudmusic.c.a", classLoader);
            String caPackage = caInterface.getPackage().getName();

            for (String curStr : getAllClasses()) {
                if (curStr.startsWith(caPackage + ".")) {
                    Class curClass = XposedHelpers.findClass(curStr, classLoader);
                    if (curClass.getInterfaces().length > 0 && curClass.getInterfaces()[0] == caInterface) {
                        Object singleton = XposedHelpers.findFirstFieldByExactType(curClass, caInterface).get(null);
                        callMethod(singleton, "a", 1000, 0); // 参数分别为 limit, offset, 然而服务器会忽略
                        break;
                    }
                }
            }
        }
    }

    static class NeteaseMusicUtils {
        static Class CLASS = findClass("com.netease.cloudmusic.utils.NeteaseMusicUtils", classLoader);

        static String generateUrl(long fid) {
            return (String) XposedHelpers.callStaticMethod(CLASS, "a", fid);
        }
    }

    static class NeteaseMusicApplication {
        static Class CLASS = findClass("com.netease.cloudmusic.NeteaseMusicApplication", classLoader);
        static Field f_singleton;
        static String musicCacheDir;


        static Application getApplication() throws IllegalAccessException {
            if (f_singleton == null)
                f_singleton = XposedHelpers.findFirstFieldByExactType(NeteaseMusicApplication.CLASS, NeteaseMusicApplication.CLASS);

            return (Application) f_singleton.get(null);
        }

        static void showToast(final String text) throws IllegalAccessException {
            final Application application = getApplication();
            if (application != null) {
                android.os.Handler h = new android.os.Handler(application.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(application, text, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }


        static String getMusicCacheDir() {
            // com.netease.cloudmusic.c.a(String) : String
            if (musicCacheDir == null) {
                String path = Environment.getExternalStorageDirectory().getPath();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    try {
                        File[] extDirs = NeteaseMusicApplication.getApplication().getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS);
                        if ((extDirs != null) && (extDirs.length > 1) && (extDirs[1] != null) && ((path + File.separator).equals(extDirs[1].getAbsolutePath() + File.separator))) {
                            return path;
                        }
                    } catch (Exception e) {
                        return path;
                    }
                }
                musicCacheDir = path + "/netease/cloudmusic/Cache/Music";
            }
            return musicCacheDir;
        }
    }

    static class HttpEapi {
        static final String ARGS_FIELD_NAME = "__args";
        static Class CLASS;
        static Method m_startResuest;
        static Method m_getCookieString;

        static {
            try {
                List<String> list = new ArrayList<>(getAllClasses());
                Collections.sort(list);
                Collections.reverse(list);
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]$");

                @SuppressWarnings("deprecation")
                String cookieStore = org.apache.http.client.CookieStore.class.getName();
                for (String curStr : list) {
                    if (pattern.matcher(curStr).find()) {
                        Class curClass = XposedHelpers.findClass(curStr, classLoader);
                        Field cookieStoreField = Utility.findFirstField(curClass, null, null, cookieStore);
                        if (cookieStoreField != null) {
                            Method[] methods = findMethodsByExactParameters(curClass, curClass);
                            if (methods.length > 0) {
                                HttpEapi.CLASS = curClass;
                                m_startResuest = methods[0];
                                String cookieStoreWithPrefix = cookieStoreField.getType().getName();
                                String prefix = cookieStoreWithPrefix.substring(0, cookieStoreWithPrefix.indexOf(cookieStore));
                                CloudMusicPackage.Mam.setPrefix(prefix);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        static String post(String path, Map dataMap) throws InvocationTargetException, IllegalAccessException {
            Object eapiObject = newInstance(HttpEapi.CLASS, path, dataMap);
            m_startResuest.invoke(eapiObject);
            return (String) HttpBase.m_getResponseText.invoke(eapiObject);
        }

        static String getDefaultCookie() throws UnsupportedEncodingException, JSONException, InvocationTargetException, IllegalAccessException {
            if (version.startsWith("3.0")) {
                @SuppressWarnings({"deprecation", "unchecked"})
                List<Cookie> cookies = (List<Cookie>) findMethodsByExactParameters(HttpEapi.CLASS, List.class)[0].invoke(null);
                return Utility.serialCookies(cookies);
            } else {
                if (m_getCookieString == null) {
                    Method[] methods = XposedHelpers.findMethodsByExactParameters(CLASS, String.class, String.class);
                    for (Method method : methods) {
                        if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                            m_getCookieString = method;
                            break;
                        }
                    }
                }
                return (String) m_getCookieString.invoke(null, "music.163.com");
            }
        }

        static void setArgs(Object httpEapiObject, Object[] args) {
            XposedHelpers.setAdditionalInstanceField(httpEapiObject, CloudMusicPackage.HttpEapi.ARGS_FIELD_NAME, args);
        }

        private static Object[] getArgs(Object httpEapiObject) {
            return (Object[]) XposedHelpers.getAdditionalInstanceField(httpEapiObject, ARGS_FIELD_NAME);
        }

        static String getPath(Object httpEapiObject) {
            return (String) getArgs(httpEapiObject)[0];
        }

        @SuppressWarnings("unchecked")
        static Map<String, String> getRequestMap(Object httpEapiObject) {
            return (Map<String, String>) getArgs(httpEapiObject)[1];
        }
    }

    static class HttpBase {
        static Class CLASS = HttpEapi.CLASS.getSuperclass();
        static Field f_responseObject;
        static Method m_setAdditionHeader;
        static Method m_getResponseCode;
        static Method m_getResponseText;
        static Method m_startRequest;

        static {
            try {
                //noinspection deprecation
                f_responseObject = XposedHelpers.findFirstFieldByExactType(CLASS, Mam.findMamClass(org.apache.http.HttpResponse.class));
                m_setAdditionHeader = XposedHelpers.findMethodsByExactParameters(CLASS, void.class, String.class, String.class)[0];
                m_getResponseCode = XposedHelpers.findMethodsByExactParameters(CLASS, int.class)[0];
                m_getResponseText = XposedHelpers.findMethodsByExactParameters(CLASS, String.class)[0];
                m_startRequest = XposedHelpers.findMethodsByExactParameters(CLASS, void.class)[0];
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Object _httpBase;

        void doRequest(String method, String urlString, Map<String, String> postData, Map<String, String> additionHeaders) throws Throwable {
            if (method != null && urlString != null) {
                // new
                _httpBase = XposedHelpers.newInstance(CloudMusicPackage.HttpBase.CLASS, urlString, postData, method.toUpperCase());

                // set addition header
                if (additionHeaders != null) {
                    for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                        setAdditionHeader(entry.getKey(), entry.getValue());
                    }
                }

                // start request
                startRequest();
            }
        }

        void setAdditionHeader(String key, String value) throws InvocationTargetException, IllegalAccessException {
            m_setAdditionHeader.invoke(_httpBase, key, value);
        }

        void startRequest() throws SocketException, SocketTimeoutException, InvocationTargetException, IllegalAccessException {
            m_startRequest.invoke(_httpBase);
        }

        Object getResponseObject() throws IllegalAccessException {
            return f_responseObject.get(_httpBase);
        }

        int getResponseCode() throws InvocationTargetException, IllegalAccessException {
            return (int) m_getResponseCode.invoke(_httpBase);
        }

        String getResponseText() throws InvocationTargetException, IllegalAccessException {
            return (String) m_getResponseText.invoke(_httpBase);
        }

        long getFileSize() throws IllegalAccessException {
            Object response = getResponseObject();
            // get whole size for music file
            if ((boolean) callMethod(response, "containsHeader", "Content-Range")) {
                Object header = callMethod(response, "getFirstHeader", "Content-Range");
                String value = (String) callMethod(header, "getValue");
                return Integer.parseInt(Utility.getLastPartOfString(value, "/"));
            } else
                return (long) callMethod(callMethod(response, "getEntity"), "getContentLength");
        }

        String getRedirectLocation() throws IllegalAccessException {
            Object response = getResponseObject();
            if ((boolean) callMethod(response, "containsHeader", "Location")) {
                Object header = callMethod(response, "getFirstHeader", "Location");
                return (String) callMethod(header, "getValue");
            }
            return null;
        }
    }

    static class UIAA {
        static Class CLASS = findClass("com.netease.cloudmusic.ui.a.a", classLoader);
        static Method m_qualityBox;

        static Method getQualityBoxMethod() {
            if (m_qualityBox == null) {
                Method[] methods = UIAA.CLASS.getDeclaredMethods();
                for (int i = methods.length - 1; i >= 0; i--) {
                    Method m = methods[i];
                    Class[] paras = m.getParameterTypes();
                    if (paras.length == 6
                            && paras[0] == Context.class
                            && paras[1] == Object.class
                            && paras[2] == Object.class
                            && paras[3] == int[].class
                            && paras[4] == int.class) {
                        m_qualityBox = m;
                        break;
                    }
                }
            }
            return m_qualityBox;
        }
    }

    static class PlayerActivity {
        static Field f_musicInfo;
        static Method m_likeBottomOnClick;

        static Object getMusicInfo(Object activity) throws IllegalAccessException {
            if (f_musicInfo == null)
                f_musicInfo = XposedHelpers.findFirstFieldByExactType(activity.getClass(), MusicInfo.CLASS);

            return f_musicInfo.get(activity);
        }


        static Method getLikeBottomOnClickMethod() {
            if (m_likeBottomOnClick == null) {
                String className = version.startsWith("3.0") ? "com.netease.cloudmusic.activity.PlayerMusicActivity$5"
                        : findClass("com.netease.cloudmusic.activity.PlayerActivity", classLoader).getSuperclass().getName() + "$4";

                m_likeBottomOnClick = findMethodExact(className, classLoader, "onClick", View.class);
            }

            return m_likeBottomOnClick;
        }
    }
}