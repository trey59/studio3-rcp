package com.aptana.github;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.preferences.Base64;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.json.JSONObject;

import com.aptana.core.util.IOUtil;
import com.aptana.git.core.model.GitExecutable;

@SuppressWarnings("restriction")
public class GithubAPI
{

	private static final String ENCODING = "UTF-8"; //$NON-NLS-1$
	private static final String BASE_URL = "https://github.com/api/v2/json/"; //$NON-NLS-1$

	/**
	 * Special config keys.
	 */
	private static final String GITHUB_USER = "github.user"; //$NON-NLS-1$
	private static final String GITHUB_PASSWORD = "password"; //$NON-NLS-1$
	private static final String GITHUB_NODE = "Github"; //$NON-NLS-1$

	private String username;
	private String password;

	public GithubAPI(String username, String password)
	{
		this.username = username;
		this.password = password;
	}

	@SuppressWarnings("nls")
	public static String getConfiguredUsername()
	{
		return GitExecutable.instance().outputForCommand(null, "config", "--global", GITHUB_USER);
	}

	public static String getStoredPassword()
	{
		try
		{
			ISecurePreferences prefs = SecurePreferencesFactory.getDefault();
			prefs = prefs.node(GITHUB_NODE);
			return prefs.get(GITHUB_PASSWORD, null);
		}
		catch (StorageException e)
		{
			log(e);
		}
		return null;
	}

	private static void log(Exception e)
	{
		Activator.getDefault().getLog()
				.log(new Status(IStatus.ERROR, Activator.getPluginIdentifier(), e.getMessage(), e));
	}

	/**
	 * Check auth against github.
	 * 
	 * @return
	 */
	public IStatus authenticate()
	{
		HttpURLConnection connection = null;
		try
		{
			connection = getUserDetails();
			int responseCode = connection.getResponseCode();

			if (responseCode == HttpURLConnection.HTTP_OK)
				return Status.OK_STATUS;

			if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAUTHORIZED)
				return new Status(IStatus.ERROR, Activator.getPluginIdentifier(), "Authentication failed");
		}
		catch (Exception e)
		{
			return new Status(IStatus.ERROR, Activator.getPluginIdentifier(), e.getMessage(), e);
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		return new Status(IStatus.ERROR, Activator.getPluginIdentifier(), "Unknown error");
	}

	// TODO Listen for projects with github remotes and possibly delete the repo at github too?

	public boolean userCanCreatePrivateRepo()
	{
		HttpURLConnection connection = null;
		try
		{
			connection = getUserDetails();
			int responseCode = connection.getResponseCode();

			if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN)
				return false;
			if (responseCode == HttpURLConnection.HTTP_OK)
			{
				String contents = IOUtil.read(connection.getInputStream());
				JSONObject object = new JSONObject(contents);
				JSONObject user = object.getJSONObject("user"); //$NON-NLS-1$
				int privateRepos = user.getInt("owned_private_repo_count"); //$NON-NLS-1$
				JSONObject plan = user.getJSONObject("plan"); //$NON-NLS-1$
				int allowedPrivateRepos = plan.getInt("private_repos"); //$NON-NLS-1$
				return privateRepos < allowedPrivateRepos;
			}
		}
		catch (Exception e)
		{
			log(e);
			return false;
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		return false;
	}

	protected HttpURLConnection getUserDetails() throws UnsupportedEncodingException
	{
		HttpURLConnection connection;
		String userURL = MessageFormat.format(BASE_URL + "user/show/{0}", username); //$NON-NLS-1$

		connection = excutePost(userURL, null);
		return connection;
	}

	public String createRepo(String repoName, boolean makePrivate, IProgressMonitor monitor) throws CoreException
	{
		// TODO Send along some form parameter to let github know user created through aptana!
		HttpURLConnection connection = null;
		try
		{
			String userURL = BASE_URL + "repos/create"; //$NON-NLS-1$

			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("public", makePrivate ? "0" : "1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			parameters.put("name", repoName); //$NON-NLS-1$

			connection = excutePost(userURL, parameters);
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK)
			{
				// Build the generated repo by grabbing name in the response! (Just grab and return the url value?)
				String contents = IOUtil.read(connection.getInputStream());
				JSONObject object = new JSONObject(contents);
				JSONObject repository = object.getJSONObject("repository"); //$NON-NLS-1$
				String newRepoName = (String) repository.get("name"); //$NON-NLS-1$
				return MessageFormat.format("git@github.com:{0}/{1}.git", username, newRepoName); //$NON-NLS-1$
			}

			if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN)
				throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(),
						"Authentication failed"));
		}
		catch (CoreException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(), e.getMessage(), e));
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(), "Unknown error"));
	}

	@SuppressWarnings("nls")
	private HttpURLConnection excutePost(String targetURL, Map<String, String> parameters)
	{

		String urlParameters = toURLParameters(parameters);
		URL url;
		HttpURLConnection connection = null;
		DataOutputStream wr = null;
		try
		{
			// Create connection
			url = new URL(targetURL);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setRequestProperty("Content-Language", "en-US");

			String authorizationString = "Basic " + new String(Base64.encode((username + ":" + password).getBytes()));
			connection.setRequestProperty("Authorization", authorizationString);

			connection.setUseCaches(false);
			connection.setDoInput(true);
			connection.setDoOutput(true);

			// Send request
			wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			return connection;
		}
		catch (Exception e)
		{
			log(e);
			return null;
		}
		finally
		{
			try
			{
				if (wr != null)
				{
					wr.close();
				}
			}
			catch (IOException e)
			{
				// ignore
			}
		}
	}

	private static String toURLParameters(Map<String, String> parameters)
	{
		if (parameters == null)
			return ""; //$NON-NLS-1$
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> entry : parameters.entrySet())
		{
			builder.append(entry.getKey()).append("="); //$NON-NLS-1$
			try
			{
				builder.append(URLEncoder.encode(entry.getValue(), ENCODING));
			}
			catch (UnsupportedEncodingException e)
			{
				builder.append(entry.getValue());
			}
			builder.append("&"); //$NON-NLS-1$
		}
		if (builder.length() > 0)
			builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}

	public void storeCredentials()
	{
		// TODO check exit code/output?
		GitExecutable.instance().runInBackground(null, "config", "--global", GITHUB_USER, username); //$NON-NLS-1$ //$NON-NLS-2$

		try
		{
			ISecurePreferences prefs = SecurePreferencesFactory.getDefault();
			prefs = prefs.node(GITHUB_NODE);
			prefs.put(GITHUB_PASSWORD, password, true);
			prefs.flush();
		}
		catch (Exception e)
		{
			log(e);
		}
	}
}
