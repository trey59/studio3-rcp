package org.radrails.rails.internal.ui;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.radrails.rails.ui.RailsUIPlugin;

import com.aptana.git.core.model.GitExecutable;
import com.aptana.util.IOUtil;

public class GithubAPI
{

	private static final String ENCODING = "UTF-8"; //$NON-NLS-1$
	private static final String BASE_URL = "https://github.com/api/v2/json/"; //$NON-NLS-1$

	private String username;
	private String token;

	public GithubAPI(String username, String token)
	{
		this.username = username;
		this.token = token;
	}

	@SuppressWarnings("nls")
	public static String getConfiguredUsername()
	{
		return GitExecutable.instance().outputForCommand(null, "config", "--global", "github.user");
	}

	@SuppressWarnings("nls")
	public static String getConfiguredToken()
	{
		return GitExecutable.instance().outputForCommand(null, "config", "--global", "github.token");
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
			String userURL = MessageFormat.format(BASE_URL + "user/show/{0}", username); //$NON-NLS-1$
			String urlParameters = "login=" + URLEncoder.encode(username, ENCODING) + "&token=" //$NON-NLS-1$ //$NON-NLS-2$
					+ URLEncoder.encode(token, ENCODING);
			connection = excutePost(userURL, urlParameters);
			int responseCode = connection.getResponseCode();

			if (responseCode == 200)
				return Status.OK_STATUS;

			if (responseCode == 401)
				return new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), "Authentication failed");
		}
		catch (Exception e)
		{
			return new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), e.getMessage(), e);
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		return new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), "Unknown error");
	}

	public String createRepo(String repoName, boolean makePrivate, IProgressMonitor monitor) throws CoreException
	{
		HttpURLConnection connection = null;
		try
		{
			String userURL = BASE_URL + "repos/create"; //$NON-NLS-1$
			StringBuilder builder = new StringBuilder();
			builder.append("name="); //$NON-NLS-1$
			builder.append(URLEncoder.encode(repoName, ENCODING));
			builder.append("&public="); //$NON-NLS-1$
			builder.append(makePrivate ? 0 : 1);
			builder.append("&login="); //$NON-NLS-1$
			builder.append(URLEncoder.encode(username, ENCODING));
			builder.append("&token="); //$NON-NLS-1$
			builder.append(URLEncoder.encode(token, ENCODING));

			connection = excutePost(userURL, builder.toString());
			int responseCode = connection.getResponseCode();
			if (responseCode == 200)
			{
				// TODO Grab the generated repo url, name and owner are in the response!
				String contents = IOUtil.read(connection.getInputStream());
				return MessageFormat.format("git@github.com:{0}/{1}.git", username, repoName); //$NON-NLS-1$
			}

			if (responseCode == 401)
				throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), "Authentication failed"));
		}
		catch (CoreException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), e.getMessage(), e));
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
		throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), "Unknown error"));
	}

	@SuppressWarnings("nls")
	private static HttpURLConnection excutePost(String targetURL, String urlParameters)
	{
		URL url;
		HttpURLConnection connection = null;
		try
		{
			// Create connection
			url = new URL(targetURL);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setRequestProperty("Content-Language", "en-US");

			connection.setUseCaches(false);
			connection.setDoInput(true);
			connection.setDoOutput(true);

			// Send request
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			return connection;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
