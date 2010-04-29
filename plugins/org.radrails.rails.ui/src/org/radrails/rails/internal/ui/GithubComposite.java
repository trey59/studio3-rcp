package org.radrails.rails.internal.ui;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.radrails.rails.ui.RailsUIPlugin;

import com.aptana.git.core.model.GitExecutable;

public class GithubComposite extends Composite
{

	private static final int TEXT_FIELD_WIDTH = 250;
	private Text username;
	private Text password;
	private Composite signupLoginComp;
	private Composite publishComp;
	private WizardPage page;

	public GithubComposite(WizardPage page, Composite parent, int style)
	{
		super(parent, style);
		this.page = page;
		createControls();
	}

	private void createControls()
	{
		Composite githubControls = this;
		githubControls.setLayout(new GridLayout(1, false));
		githubControls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Button manageSourceWithGit = new Button(githubControls, SWT.CHECK);
		manageSourceWithGit.setText("Manage my source code with git");
		manageSourceWithGit.setSelection(true); // TODO Only show the following items if this is checked, we need to
												// listen for selection

		String githubUser = GitExecutable.instance().outputForCommand(null, "config", "--global", "github.user");
		String githubToken = GitExecutable.instance().outputForCommand(null, "config", "--global", "github.token");

		// Login/Sign up
		signupLoginComp = new Composite(githubControls, SWT.NONE);
		signupLoginComp.setLayout(new GridLayout(1, false));
		signupLoginComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Browser browser = new Browser(signupLoginComp, SWT.NONE);
		browser.setUrl("http://github.com/plans?referral_code=aptana"); // FIXME Get the right URL to point to for
																		// the ad
		// TODO Force links to open in a separate embedded browser/dialog?

		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint = TEXT_FIELD_WIDTH;
		gd.heightHint = 150;
		browser.setLayoutData(gd);

		// Add way for user to enter existing credentials
		Label useExistingLabel = new Label(signupLoginComp, SWT.NONE);
		useExistingLabel.setText("Or login using your existing github credentials:");

		Composite credentialsComp = new Composite(signupLoginComp, SWT.NONE);
		credentialsComp.setLayout(new GridLayout(2, false));
		credentialsComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		// username
		Label userNameLabel = new Label(credentialsComp, SWT.NONE);
		userNameLabel.setText("Username: ");

		username = new Text(credentialsComp, SWT.BORDER | SWT.SINGLE);
		if (githubUser != null && githubUser.trim().length() > 0)
		{
			username.setText(githubUser);
		}
		else
		{
			// username.setText(githubUser); // TODO Grab current username from system?
		}

		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = TEXT_FIELD_WIDTH;
		username.setLayoutData(data);

		// password/token
		Label passwordLabel = new Label(credentialsComp, SWT.NONE);
		passwordLabel.setText("Token: ");

		password = new Text(credentialsComp, SWT.BORDER | SWT.SINGLE /* | SWT.PASSWORD */);
		password.setLayoutData(data);

		if (githubToken != null && githubToken.trim().length() > 0)
		{
			password.setText(githubToken);
		}

		Button credentialsButton = new Button(credentialsComp, SWT.PUSH);
		credentialsButton.setText("Submit");

		data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 2;
		data.horizontalAlignment = SWT.RIGHT;
		credentialsButton.setLayoutData(data);

		credentialsButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				IStatus status = authenticate(username.getText(), password.getText());
				if (status.isOK())
				{
					// Yay everything's hunky-dory
					setErrorMessage(null);
					hideLogin();
					showPublishControls();
				}
				else
				{
					setErrorMessage(status.getMessage());
				}
			}
		});

		// Signed in, publish to github
		publishComp = new Composite(githubControls, SWT.NONE);
		publishComp.setLayout(new GridLayout(1, false));
		publishComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// TODO Indent this stuff
		Button publishToGithub = new Button(publishComp, SWT.CHECK);
		publishToGithub.setText("Publish this project on my github account as:");
		publishToGithub.setSelection(true);

		Composite githubProjectComp = new Composite(publishComp, SWT.NONE);
		githubProjectComp.setLayout(new GridLayout(2, false));
		githubProjectComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Text githubProjectName = new Text(githubProjectComp, SWT.BORDER | SWT.SINGLE);
		// githubProjectName.setText(); // TODO Grab project name and convert it?

		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = TEXT_FIELD_WIDTH;
		githubProjectName.setLayoutData(data);

		Button privateRepo = new Button(githubProjectComp, SWT.CHECK);
		privateRepo.setText("Private");

		if (authenticate(githubUser, githubToken).isOK())
		{
			hideLogin();
		}
		else
		{
			hidePublish();
		}
	}

	/**
	 * Check auth against github.
	 * 
	 * @param username
	 * @param token
	 * @return
	 */
	private IStatus authenticate(String username, String token)
	{
		HttpURLConnection connection = null;
		try
		{
			String userURL = MessageFormat.format("https://github.com/api/v2/json/user/show/{0}", username); //$NON-NLS-1$
			String urlParameters = "login=" + URLEncoder.encode(username, "UTF-8") + "&token="
					+ URLEncoder.encode(token, "UTF-8");
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

	protected void showPublishControls()
	{
		publishComp.setVisible(true);
	}

	protected void hideLogin()
	{
		signupLoginComp.setVisible(false);
		// need to exclude
		GridData gd = (GridData) signupLoginComp.getLayoutData();
		gd.exclude = true;
	}

	private void hidePublish()
	{
		publishComp.setVisible(false);
		// need to exclude
		GridData gd = (GridData) publishComp.getLayoutData();
		gd.exclude = true;
	}

	protected void setErrorMessage(String message)
	{
		page.setErrorMessage(message);
	}

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
