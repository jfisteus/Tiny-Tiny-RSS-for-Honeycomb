package org.fox.ttrss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class ApiRequest extends AsyncTask<HashMap<String,String>, Integer, JsonElement> {
	private final String TAG = this.getClass().getSimpleName();

	protected static final int STATUS_LOGIN_FAILED = 0;
	protected static final int STATUS_OK = 1;
	protected static final int STATUS_API_DISABLED = 2;
	protected static final int STATUS_OTHER_ERROR = 3;
	
	private String m_api;
	private boolean m_trustAny = false;
	private boolean m_transportDebugging = false;
	private Context m_context;

	public ApiRequest(Context context) {
		m_context = context;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(m_context);
		
		m_api = prefs.getString("ttrss_url", null);
		m_trustAny = prefs.getBoolean("ssl_trust_any", false);
		m_transportDebugging = prefs.getBoolean("transport_debugging", false);
	}
	
	@Override
	protected JsonElement doInBackground(HashMap<String, String>... params) {

		Gson gson = new Gson();
		
		String requestStr = gson.toJson(new HashMap<String,String>(params[0]));
		
		if (m_transportDebugging) Log.d(TAG, ">>> (" + requestStr + ") " + m_api);
		
		DefaultHttpClient client;
		
		if (m_trustAny) {
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
        
			HttpParams httpParams = new BasicHttpParams();

			client = new DefaultHttpClient(new ThreadSafeClientConnManager(httpParams, schemeRegistry), httpParams);
		} else {
			client = new DefaultHttpClient();
		}

		HttpPost httpPost = new HttpPost(m_api + "/api/");
		
		try {
			httpPost.setEntity(new StringEntity(requestStr, "utf-8"));
			HttpResponse execute = client.execute(httpPost);
			
			InputStream content = execute.getEntity().getContent();

			BufferedReader buffer = new BufferedReader(
					new InputStreamReader(content));

			String s = "";				
			String response = "";

			while ((s = buffer.readLine()) != null) {
				response += s;
			}

			if (m_transportDebugging) Log.d(TAG, "<<< " + response);

			JsonParser parser = new JsonParser();
			
			return parser.parse(response);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}


	/* protected String m_sessionId;
	protected String m_apiEndpoint;
	protected String m_login;
	protected String m_password;
	protected int m_authStatus;
	protected Gson m_gson = new Gson();

	protected static final int STATUS_LOGIN_FAILED = 0;
	protected static final int STATUS_OK = 1;
	protected static final int STATUS_API_DISABLED = 2;
	protected static final int STATUS_OTHER_ERROR = 3;
	
	protected ApiRequest(String sessionId, String apiEndpoint, String login, String password) {
		super();
		m_sessionId = sessionId;
		m_apiEndpoint = apiEndpoint;
		m_authStatus = STATUS_OK;
		m_login = login;
		m_password = password;
		
		//Log.d(TAG, "initial SID=" + sessionId);
	}
	
	protected int tryAuthenticate() {
		JsonElement result = _sendRequest(new HashMap<String,String>() {   
			{
				put("op", "login");
				put("user", m_login);
				put("password", m_password);
			}			 
		});
		
		if (result != null) {
			try {			
				JsonObject rv = result.getAsJsonObject();

				int status = rv.get("status").getAsInt();
				
				if (status == 0) {
					JsonObject content = rv.get("content").getAsJsonObject();
					if (content != null) {
						m_sessionId = content.get("session_id").getAsString();
						
						Log.d(TAG, "<<< Authentified, sessionId=" + m_sessionId);
						
						return STATUS_OK;
					}
				} else {
					JsonObject content = rv.get("content").getAsJsonObject();
					
					if (content != null) {
						String error = content.get("error").getAsString();

						if (error.equals("LOGIN_ERROR")) {
							m_sessionId = null;
							return STATUS_LOGIN_FAILED;
						} else if (error.equals("API_DISABLED")) {
							m_sessionId = null;
							return STATUS_API_DISABLED;
						}								
					}							
				}
			} catch (Exception e) {
				e.printStackTrace();						
			}
		}
		m_sessionId = null;
		return STATUS_OTHER_ERROR;
	}
	
	protected String getSessionId() {
		return m_sessionId;
	}
	
	protected int getAuthStatus() {
		return m_authStatus;
	}
	
	protected JsonElement _sendRequest(HashMap<String,String> param) {

		HashMap<String,String> tmp = new HashMap<String,String>(param);

		if (m_sessionId != null)
			tmp.put("sid", m_sessionId);

		String requestStr = m_gson.toJson(tmp);
		
		Log.d(TAG, ">>> (" + requestStr + ") " + m_apiEndpoint);
		
		DefaultHttpClient client = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(m_apiEndpoint + "/api/");
		
		try {
			httpPost.setEntity(new StringEntity(requestStr, "utf-8"));
			HttpResponse execute = client.execute(httpPost);
			
			InputStream content = execute.getEntity().getContent();

			BufferedReader buffer = new BufferedReader(
					new InputStreamReader(content));

			String s = "";				
			String response = "";

			while ((s = buffer.readLine()) != null) {
				response += s;
			}

			Log.d(TAG, "<<< " + response);

			JsonParser parser = new JsonParser();
			
			return parser.parse(response);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	public JsonElement sendRequest(HashMap<String, String> params) {
		
		JsonElement result = _sendRequest(params);

		try {
			JsonElement content = result.getAsJsonObject().get("content");
			int status = result.getAsJsonObject().get("status").getAsInt();

			if (status == 1) {
				String error = content.getAsJsonObject().get("error").getAsString();

				if (error.equals("NOT_LOGGED_IN")) {
					Log.d(TAG, "<<< Session invalid, trying to authenticate...");
					
					m_sessionId = null;
					m_authStatus = tryAuthenticate();
					
					if (m_authStatus == STATUS_OK) {
						result = _sendRequest(params);
						
						return result.getAsJsonObject().get("content");						
					}
				}
			} else {
				return content;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	} */ 
}
