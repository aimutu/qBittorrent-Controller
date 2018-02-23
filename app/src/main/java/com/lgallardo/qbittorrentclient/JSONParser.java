/*
 *   Copyright (c) 2014-2015 Luis M. Gallardo D.
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the GNU Lesser General Public License v3.0
 *   which accompanies this distribution, and is available at
 *   http://www.gnu.org/licenses/lgpl.html
 *
 */
package com.lgallardo.qbittorrentclient;

import android.net.Uri;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;

public class JSONParser {
    private static final int TIMEOUT_ERROR = 1;
    private static final int NO_PEER_CERTIFICATE = 2;
    static InputStream is = null;
    private JSONObject jObj = null;
    private JSONArray jArray = null;
    private String json = "";
    private String hostname;
    private String subfolder;
    private int port;
    private String protocol;
    private String username;
    private String password;
    private int connection_timeout;
    private int data_timeout;
    private String cookie;

    private File localTrustStoreFile;
    private String keystore_path;
    private String keystore_password;

    // constructor
    public JSONParser() {
        this("", "", "", 0, "", "", "", "", 10, 20);
    }

    public JSONParser(String hostname, String subfolder, int port, String username, String password) {
        this(hostname, subfolder, "http", port, "", "", username, password, 10, 20);
    }

    public JSONParser(String hostname, String subfolder, String protocol, int port, String keystore_path, String keystore_password, String username, String password, int connection_timeout, int data_timeout) {

        this.hostname = hostname;
        this.subfolder = subfolder;
        this.protocol = protocol;
        this.port = port;
        this.keystore_path = keystore_path;
        this.keystore_password = keystore_password;
        this.username = username;
        this.password = password;
        this.connection_timeout = connection_timeout;
        this.data_timeout = data_timeout;

    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public JSONObject getJSONFromUrl(String url) throws JSONParserStatusCodeException {

        // if server is publish in a subfolder, fix url
        if (subfolder != null && !subfolder.equals("")) {
            url = subfolder + "/" + url;
        }

        HttpResponse httpResponse;
        DefaultHttpClient httpclient;

        HttpParams httpParameters = new BasicHttpParams();

        // Set the timeout in milliseconds until a connection is established.
        // The default value is zero, that means the timeout is not used.
        int timeoutConnection = connection_timeout * 1000;

        // Set the default socket timeout (SO_TIMEOUT)
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = data_timeout * 1000;

        // Set http parameters
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        HttpProtocolParams.setUserAgent(httpParameters, "qBittorrent for Android");
        HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(httpParameters, HTTP.UTF_8);

        // Making HTTP request
        HttpHost targetHost = new HttpHost(this.hostname, this.port, this.protocol);

        // httpclient = new DefaultHttpClient(httpParameters);
        // httpclient = new DefaultHttpClient();
        httpclient = getNewHttpClient();

        httpclient.setParams(httpParameters);


        try {

            AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);

            httpclient.getCredentialsProvider().setCredentials(authScope, credentials);

            // set http parameters

            url = protocol + "://" + hostname + ":" + port + "/" + url;

//            Log.d("Debug", "url:" + url);

            HttpGet httpget = new HttpGet(url);

            if (this.cookie != null) {
                httpget.setHeader("Cookie", this.cookie);
            }


            Log.d("Debug", "Subfolder: >" + this.subfolder + "<");


            // Fix for CSRF in API requests
            httpget.setHeader("Referer", this.protocol + "://" + this.hostname + ":" + this.port);
            httpget.setHeader("Host", this.hostname + ":" + this.port);


//            Header h[] = httpget.getAllHeaders();
//            for(int i=0; i< h.length; i++){
//
//                Log.d("Debug", h[i].getName() + ": " + h[i].getValue());
//            }

            httpResponse = httpclient.execute(targetHost, httpget);

            StatusLine statusLine = httpResponse.getStatusLine();

            int mStatusCode = statusLine.getStatusCode();

            if (mStatusCode != 200) {
                httpclient.getConnectionManager().shutdown();
                throw new JSONParserStatusCodeException(mStatusCode);
            }

            HttpEntity httpEntity = httpResponse.getEntity();
            is = httpEntity.getContent();

            // Build JSON
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            is.close();
            json = sb.toString();

            // try parse the string to a JSON object
            jObj = new JSONObject(json);

        } catch (JSONException e) {
            Log.e("JSON Parser", "Error parsing data " + e.toString());
        } catch (UnsupportedEncodingException e) {
            Log.e("JSON", "UnsupportedEncodingException: " + e.toString());

        } catch (ClientProtocolException e) {
            Log.e("JSON", "ClientProtocolException: " + e.toString());
            e.printStackTrace();
        } catch (SSLPeerUnverifiedException e) {
            Log.e("JSON", "SSLPeerUnverifiedException: " + e.toString());
            throw new JSONParserStatusCodeException(NO_PEER_CERTIFICATE);
        } catch (IOException e) {
            Log.e("JSON", "IOException: " + e.toString());
            // e.printStackTrace();
            httpclient.getConnectionManager().shutdown();
            throw new JSONParserStatusCodeException(TIMEOUT_ERROR);
        } catch (JSONParserStatusCodeException e) {
            httpclient.getConnectionManager().shutdown();
            throw new JSONParserStatusCodeException(e.getCode());
        } catch (Exception e) {
            Log.e("JSON", "Generic: " + e.toString());
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }

        // return JSON String
        return jObj;
    }

    public JSONArray getJSONArrayFromUrl(String url) throws JSONParserStatusCodeException {

        // if server is published in a subfolder, fix url
        if (subfolder != null && !subfolder.equals("")) {
            url = subfolder + "/" + url;
        }

        HttpResponse httpResponse;
        DefaultHttpClient httpclient;

        HttpParams httpParameters = new BasicHttpParams();

        // Set the timeout in milliseconds until a connection is established.
        // The default value is zero, that means the timeout is not used.
        int timeoutConnection = connection_timeout * 1000;

        // Set the default socket timeout (SO_TIMEOUT)
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = data_timeout * 1000;

        // Set http parameters
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        HttpProtocolParams.setUserAgent(httpParameters, "qBittorrent for Android");
        HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(httpParameters, HTTP.UTF_8);

        // Making HTTP request
        HttpHost targetHost = new HttpHost(hostname, port, protocol);

        httpclient = getNewHttpClient();

        httpclient.setParams(httpParameters);

        try {

            AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);

            httpclient.getCredentialsProvider().setCredentials(authScope, credentials);

            url = protocol + "://" + hostname + ":" + port + "/" + url;

            HttpGet httpget = new HttpGet(url);

            if (this.cookie != null) {
                httpget.setHeader("Cookie", this.cookie);
            }

            // Fix for CSRF in API requests
            httpget.setHeader("Referer", this.protocol + "://" + this.hostname + ":" + this.port);
            httpget.setHeader("Host", this.hostname + ":" + this.port);


//            Header h[] = httpget.getAllHeaders();
//            for(int i=0; i< h.length; i++){
//
//                Log.d("Debug", h[i].getName() + ":" + h[i].getValue());
//            }

            httpResponse = httpclient.execute(targetHost, httpget);

            StatusLine statusLine = httpResponse.getStatusLine();

            int mStatusCode = statusLine.getStatusCode();

            if (mStatusCode != 200) {
                httpclient.getConnectionManager().shutdown();
                throw new JSONParserStatusCodeException(mStatusCode);
            }

            HttpEntity httpEntity = httpResponse.getEntity();
            is = httpEntity.getContent();

            // Build JSON

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            is.close();
            json = sb.toString();

            jArray = new JSONArray(json);
        } catch (JSONException e) {
            Log.e("JSON Parser", "Error parsing data " + e.toString());
        } catch (UnsupportedEncodingException e) {
        } catch (ClientProtocolException e) {
            Log.e("JSON", "Client: " + e.toString());
            e.printStackTrace();
        } catch (SSLPeerUnverifiedException e) {
            Log.e("JSON", "SSLPeerUnverifiedException: " + e.toString());
            throw new JSONParserStatusCodeException(NO_PEER_CERTIFICATE);
        } catch (IOException e) {
            Log.e("JSON", "IO: " + e.toString());
            // e.printStackTrace();
            throw new JSONParserStatusCodeException(TIMEOUT_ERROR);
        } catch (JSONParserStatusCodeException e) {
            throw new JSONParserStatusCodeException(e.getCode());
        } catch (Exception e) {
            Log.e("JSON", "Generic: " + e.toString());
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources

            httpclient.getConnectionManager().shutdown();
        }

        // return JSON String
        return jArray;
    }

    public String postCommand(String command, String hash) throws JSONParserStatusCodeException {

        return postCommand(command, hash, null);

    }

    public String postCommand(String command, String hash, String[] params) throws JSONParserStatusCodeException {

        String key = "hash";

        String urlContentType = "application/x-www-form-urlencoded";

        String limit = "";
        String tracker = "";

        String boundary = null;

        String fileId = "";

        String filePriority = "";


        String result = "";


        StringBuilder fileContent = null;

        HttpResponse httpResponse;
        DefaultHttpClient httpclient;

        String url = "";

        String label = "";

        String path2Set = null;
        String label2Set = null;

        if ("start".equals(command) || "startSelected".equals(command)) {
            url = "command/resume";
        }

        if ("pause".equals(command) || "pauseSelected".equals(command)) {
            url = "command/pause";
        }

        if ("delete".equals(command) || "deleteSelected".equals(command)) {
            url = "command/delete";
            key = "hashes";
        }

        if ("deleteDrive".equals(command) || "deleteDriveSelected".equals(command)) {
            url = "command/deletePerm";
            key = "hashes";
        }

        if ("addTorrent".equals(command)) {
            url = "command/download";
            key = "urls";

            if (params != null) {

                path2Set = params[0];
                label2Set = params[1];
            }
        }

        if ("addTorrentAPI7".equals(command)) {
            url = "command/download";
            key = "urls";

            boundary = "-----------------------" + (new Date()).getTime();

            urlContentType = "multipart/form-data; boundary=" + boundary;

            if (params != null) {

                path2Set = params[0];
                label2Set = params[1];
            }
        }

        if ("addTracker".equals(command)) {
            url = "command/addTrackers";
            key = "hash";
        }


        if ("addTorrentFile".equals(command)) {
            url = "command/upload";
            key = "urls";

            boundary = "-----------------------" + (new Date()).getTime();

            urlContentType = "multipart/form-data; boundary=" + boundary;

            if (params != null) {
                path2Set = params[0];
                label2Set = params[1];
            }
        }

        if ("pauseall".equals(command)) {
            url = "command/pauseall";
        }

        if ("pauseAll".equals(command)) {
            url = "command/pauseAll";
        }


        if ("resumeall".equals(command)) {
            url = "command/resumeall";
        }

        if ("resumeAll".equals(command)) {
            url = "command/resumeAll";
        }

        if ("increasePrio".equals(command)) {
            url = "command/increasePrio";
            key = "hashes";
        }

        if ("decreasePrio".equals(command)) {
            url = "command/decreasePrio";
            key = "hashes";
        }

        if ("maxPrio".equals(command)) {
            url = "command/topPrio";
            key = "hashes";
        }

        if ("minPrio".equals(command)) {
            url = "command/bottomPrio";
            key = "hashes";
        }


        if ("setFilePrio".equals(command)) {
            url = "command/setFilePrio";

            String[] tmpString = hash.split("&");
            hash = tmpString[0];
            fileId = tmpString[1];
            filePriority = tmpString[2];

        }

        if ("setQBittorrentPrefefrences".equals(command)) {
            url = "command/setPreferences";
            key = "json";
        }

        if ("setUploadRateLimit".equals(command)) {

            url = "command/setTorrentsUpLimit";
            key = "hashes";

            String[] tmpString = hash.split("&");
            hash = tmpString[0];

            try {
                limit = tmpString[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                limit = "-1";
            }
        }

        if ("setDownloadRateLimit".equals(command)) {
            url = "command/setTorrentsDlLimit";
            key = "hashes";

            String[] tmpString = hash.split("&");
            hash = tmpString[0];

            try {
                limit = tmpString[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                limit = "-1";
            }

        }

        if ("recheckSelected".equals(command)) {
            url = "command/recheck";
        }

        if ("toggleFirstLastPiecePrio".equals(command)) {
            url = "command/toggleFirstLastPiecePrio";
            key = "hashes";
        }

        if ("toggleSequentialDownload".equals(command)) {
            url = "command/toggleSequentialDownload";
            key = "hashes";

        }

        if ("toggleAlternativeSpeedLimits".equals(command)) {
            url = "command/toggleAlternativeSpeedLimits";
            key = "hashes";
        }

        if ("setLabel".equals(command)) {
            url = "command/setLabel";
            key = "hashes";

            String[] tmpString = hash.split("&");
            hash = tmpString[0];

            try {
                label = tmpString[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                label = "";
            }

        }

        if ("setCategory".equals(command)) {
            url = "command/setCategory";
            key = "hashes";

            String[] tmpString = hash.split("&");
            hash = tmpString[0];

            try {
                label = tmpString[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                label = "";
            }
        }


        if ("alternativeSpeedLimitsEnabled".equals(command)) {

            url = "command/alternativeSpeedLimitsEnabled";
            key = "hashes";
        }


        // if server is publish in a subfolder, fix url
        if (subfolder != null && !subfolder.equals("")) {
            url = subfolder + "/" + url;
        }

        HttpParams httpParameters = new BasicHttpParams();

        // Set the timeout in milliseconds until a connection is established.
        // The default value is zero, that means the timeout is not used.
        int timeoutConnection = connection_timeout * 1000;

        // Set the default socket timeout (SO_TIMEOUT)
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = data_timeout * 1000;

        // Set http parameters
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        HttpProtocolParams.setUserAgent(httpParameters, "qBittorrent for Android");
        HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(httpParameters, HTTP.UTF_8);


        // Making HTTP request
        HttpHost targetHost = new HttpHost(this.hostname, this.port, this.protocol);

        // httpclient = new DefaultHttpClient();
        httpclient = getNewHttpClient();

        httpclient.setParams(httpParameters);

        try {

            AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());

            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);

            httpclient.getCredentialsProvider().setCredentials(authScope, credentials);

            url = protocol + "://" + hostname + ":" + port + "/" + url;

            HttpPost httpget = new HttpPost(url);

            if ("addTorrent".equals(command)) {

                hash = encodeHash(hash);

            }

            if ("addTracker".equals(command)) {


                String[] tmpString = hash.split("&");
                hash = tmpString[0];

                URI hash_uri = new URI(hash);
                hash = hash_uri.toString();

                try {
                    tracker = tmpString[1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    tracker = "";
                }

            }


            // In order to pass the hash we must set the pair name value
            BasicNameValuePair bnvp = new BasicNameValuePair(key, hash);

            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            nvps.add(bnvp);

            // Add limit
            if (!limit.equals("")) {
                nvps.add(new BasicNameValuePair("limit", limit));
            }

            // Set values for setting file priority
            if ("setFilePrio".equals(command)) {

                nvps.add(new BasicNameValuePair("id", fileId));
                nvps.add(new BasicNameValuePair("priority", filePriority));
            }


            // Add label
            if (label != null && !label.equals("")) {

                label = Uri.decode(label);

                if ("setLabel".equals(command)) {
                    nvps.add(new BasicNameValuePair("label", label));
                } else {
                    nvps.add(new BasicNameValuePair("category", label));
                }

            }

            // Add tracker
            if (tracker != null && !tracker.equals("")) {
                nvps.add(new BasicNameValuePair("urls", tracker));
            }


            if (path2Set != null && !(path2Set.equals(""))) {
                nvps.add(new BasicNameValuePair("savepath", path2Set));
            }

            if (label2Set != null && !(label2Set.equals(""))) {
                nvps.add(new BasicNameValuePair("label", label2Set));
                nvps.add(new BasicNameValuePair("category", label2Set));
            }


            String entityValue = URLEncodedUtils.format(nvps, HTTP.UTF_8);

            // This replaces encoded char "+" for "%20" so spaces can be passed as parameter
            entityValue = entityValue.replaceAll("\\+", "%20");
            entityValue = entityValue.replaceAll("\\[", "%5B");
            entityValue = entityValue.replaceAll("\\]", "%5D");

            StringEntity stringEntity = new StringEntity(entityValue, HTTP.UTF_8);
            stringEntity.setContentType(URLEncodedUtils.CONTENT_TYPE);

            httpget.setEntity(stringEntity);

            // Set content type and urls
            if ("addTorrent".equals(command) || "increasePrio".equals(command) || "decreasePrio".equals(command) || "maxPrio".equals(command) || "setFilePrio".equals(command) || "toggleAlternativeSpeedLimits".equals(command) || "alternativeSpeedLimitsEnabled".equals(command) || "setLabel".equals(command) || "setCategory".equals(command) || "addTracker".equals(command)) {
                httpget.setHeader("Content-Type", urlContentType);
            }


            // Set cookie
            if (this.cookie != null) {
                httpget.setHeader("Cookie", this.cookie);
            }

            // Fix for CSRF in API requests
            httpget.setHeader("Referer", this.protocol + "://" + this.hostname + ":" + this.port);
            httpget.setHeader("Host", this.hostname + ":" + this.port);

//            Header h[] = httpget.getAllHeaders();
//            for(int i=0; i< h.length; i++){
//
//                Log.d("Debug", h[i].getName() + ":" + h[i].getValue());
//            }


            // Set content type and urls
            if ("addTorrentAPI7".equals(command)) {

                httpget.setHeader("Content-Type", urlContentType);

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

                // Add boundary
                builder.setBoundary(boundary);

                // Add torrent urls
                builder.addTextBody("urls", hash, ContentType.TEXT_PLAIN);

                if (path2Set != null && !(path2Set.equals(""))) {
                    builder.addTextBody("savepath", path2Set, ContentType.TEXT_PLAIN);
                }

                if (label2Set != null && !(label2Set.equals(""))) {
                    builder.addTextBody("label", label2Set, ContentType.TEXT_PLAIN);
                    builder.addTextBody("category", label2Set, ContentType.TEXT_PLAIN);
                }

                // Build entity
                HttpEntity entity = builder.build();

                // Set entity to http post
                httpget.setEntity(entity);

            }


            if ("addTorrentFile".equals(command)) {

                httpget.setHeader("Content-Type", urlContentType);

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

                // Add boundary
                builder.setBoundary(boundary);

                // Add torrent file as binary
                File file = new File(hash);
                // FileBody fileBody = new FileBody(file);
                // builder.addPart("file", fileBody);

                builder.addBinaryBody("upfile", file, ContentType.DEFAULT_BINARY, hash);

                if (path2Set != null && !(path2Set.equals(""))) {
                    builder.addTextBody("savepath", path2Set, ContentType.TEXT_PLAIN);
                }

                if (label2Set != null && !(label2Set.equals(""))) {
                    builder.addTextBody("label", label2Set, ContentType.TEXT_PLAIN);
                    builder.addTextBody("category", label2Set, ContentType.TEXT_PLAIN);
                }

                // Build entity
                HttpEntity entity = builder.build();

                // Set entity to http post
                httpget.setEntity(entity);

            }

            httpResponse = httpclient.execute(targetHost, httpget);

            StatusLine statusLine = httpResponse.getStatusLine();

            int mStatusCode = statusLine.getStatusCode();

            if (mStatusCode != 200) {
                httpclient.getConnectionManager().shutdown();
                throw new JSONParserStatusCodeException(mStatusCode);
            }

            HttpEntity httpEntity = httpResponse.getEntity();

            result = EntityUtils.toString(httpEntity);

            return result;


        } catch (UnsupportedEncodingException e) {

        } catch (ClientProtocolException e) {
            Log.e("Debug", "Client: " + e.toString());
            e.printStackTrace();
        } catch (SSLPeerUnverifiedException e) {
            Log.e("JSON", "SSLPeerUnverifiedException: " + e.toString());
            throw new JSONParserStatusCodeException(NO_PEER_CERTIFICATE);
        } catch (IOException e) {
            Log.e("Debug", "IO: " + e.toString());
            httpclient.getConnectionManager().shutdown();
            throw new JSONParserStatusCodeException(TIMEOUT_ERROR);
        } catch (JSONParserStatusCodeException e) {
            httpclient.getConnectionManager().shutdown();
            throw new JSONParserStatusCodeException(e.getCode());
        } catch (Exception e) {
            Log.e("Debug", "Generic: " + e.toString());
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }

        return null;

    }

    // https
    public DefaultHttpClient getNewHttpClient() {
        try {

            KeyStore localTrustStore = KeyStore.getInstance("BKS");

            InputStream in = null;

            try {

                localTrustStoreFile = new File(keystore_path);
                in = new FileInputStream(localTrustStoreFile);

                localTrustStore.load(in, keystore_password.toCharArray());
            } catch (Exception e) {
            } finally {
                if (in != null) {
                    in.close();
                }
            }

            MySSLSocketFactory sf = new MySSLSocketFactory(localTrustStore);
            sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();

//            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
//            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }

    // Cookies
    public String getNewCookie() throws JSONParserStatusCodeException {


        String url = "login";

        // if server is publish in a subfolder, fix url
        if (subfolder != null && !subfolder.equals("")) {
            url = subfolder + "/" + url;
        }

        String cookieString = null;

        HttpResponse httpResponse;
        DefaultHttpClient httpclient;

        HttpParams httpParameters = new BasicHttpParams();

        // Set the timeout in milliseconds until a connection is established.
        // The default value is zero, that means the timeout is not used.
        int timeoutConnection = connection_timeout * 1000;

        // Set the default socket timeout (SO_TIMEOUT)
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = data_timeout * 1000;

        // Set http parameters
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        HttpProtocolParams.setUserAgent(httpParameters, "qBittorrent for Android");
        HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(httpParameters, HTTP.UTF_8);

        // Making HTTP request
        HttpHost targetHost = new HttpHost(hostname, port, protocol);

        // httpclient = new DefaultHttpClient();
        httpclient = getNewHttpClient();

        httpclient.setParams(httpParameters);

        try {

//            AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
//
//            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);
//
//            httpclient.getCredentialsProvider().setCredentials(authScope, credentials);


            url = protocol + "://" + hostname + ":" + port + "/" + url;

            HttpPost httpget = new HttpPost(url);

//            // In order to pass the username and password we must set the pair name value

            List<NameValuePair> nvps = new ArrayList<NameValuePair>();


            nvps.add(new BasicNameValuePair("username", this.username));
            nvps.add(new BasicNameValuePair("password", this.password));

            // Fix for CSRF in API requests
            httpget.setHeader("Referer", this.protocol + "://" + this.hostname + ":" + this.port);
            httpget.setHeader("Host", this.hostname + ":" + this.port);

//            Header h[] = httpget.getAllHeaders();
//            for(int i=0; i< h.length; i++){
//
//                Log.d("Debug", h[i].getName() + ":" + h[i].getValue());
//            }

            httpget.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));


            HttpResponse response = httpclient.execute(targetHost, httpget);
            HttpEntity entity = response.getEntity();

            StatusLine statusLine = response.getStatusLine();

            int mStatusCode = statusLine.getStatusCode();

            if (mStatusCode == 200) {

                // Save cookie
                List<Cookie> cookies = httpclient.getCookieStore().getCookies();

                if (!cookies.isEmpty()) {
                    cookieString = cookies.get(0).getName() + "=" + cookies.get(0).getValue() + "; domain=" + cookies.get(0).getDomain();
                    cookieString = cookies.get(0).getName() + "=" + cookies.get(0).getValue();
                }

            }

            if (entity != null) {
                entity.consumeContent();
            }


            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();

        } catch (Exception e) {

            Log.e("Debug", "Exception " + e.toString());
        }

        if (cookieString == null) {
            cookieString = "";
        }
        return cookieString;


    }

    // Encode Url
    private String encodeHash(String hash) {

        URI uri = null;
        String encodedHash = null;

        try {

            try {
                URL url = null;

                url = new URL(hash);

//                Log.d("Debug", "Protocol: " + url.getProtocol());
//                Log.d("Debug", "UserInfo: " + url.getUserInfo());
//                Log.d("Debug", "Host: " + url.getHost());
//                Log.d("Debug", "Port: " + url.getPort());
//                Log.d("Debug", "Path: " + url.getPath());
//                Log.d("Debug", "Query: " + url.getQuery());
//                Log.d("Debug", "Ref: " + url.getRef());

                uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());


            } catch (MalformedURLException e) {
                uri = new URI(hash);

            }


            encodedHash = uri.toString();


        } catch (Exception e) {
            Log.e("Debug", "encodeHash: " + e.toString());
        }

        return (encodedHash);

    }


}
