package org.radrails.rails.internal.ui;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
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

import com.aptana.git.core.GitPlugin;
import com.aptana.git.core.model.GitExecutable;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.core.model.IGitRepositoryManager;

public class GithubComposite extends Composite
{

	private static final int TEXT_FIELD_WIDTH = 250;
	private Text username;
	private Text password;
	private Composite signupLoginComp;
	private Composite publishComp;
	private WizardPage page;
	private Text repoName;
	private Button publishToGithub;
	private Button privateRepo;

	private GithubAPI api;

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
		String githubUser = GithubAPI.getConfiguredUsername();
		String githubToken = GithubAPI.getConfiguredToken();

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
				GithubAPI newAPI = new GithubAPI(username.getText(), password.getText());
				IStatus status = newAPI.authenticate();
				if (status.isOK())
				{
					// TODO Store them in git by setting them in config?
					api = newAPI;
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

		publishToGithub = new Button(publishComp, SWT.CHECK);
		publishToGithub.setText("Publish this project on my github account as:");
		publishToGithub.setSelection(true);

		Composite githubProjectComp = new Composite(publishComp, SWT.NONE);
		githubProjectComp.setLayout(new GridLayout(2, false));
		githubProjectComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		repoName = new Text(githubProjectComp, SWT.BORDER | SWT.SINGLE);

		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = TEXT_FIELD_WIDTH;
		repoName.setLayoutData(data);

		// TODO Need to enable/disable based on whether user can even create one!
		privateRepo = new Button(githubProjectComp, SWT.CHECK);
		privateRepo.setText("Private");

		// TODO Do this async so we don't hang the UI!
		api = new GithubAPI(githubUser, githubToken);
		if (api.authenticate().isOK())
		{
			hideLogin();
		}
		else
		{
			hidePublish();
		}
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
		publishToGithub.setSelection(false);
		publishComp.setVisible(false);
		// need to exclude
		GridData gd = (GridData) publishComp.getLayoutData();
		gd.exclude = true;
	}

	protected void setErrorMessage(String message)
	{
		page.setErrorMessage(message);
	}

	public void updateProjectName(String projectName)
	{
		// TODO Don't update if user entered something manually.
		repoName.setText(projectName);
	}

	public void updateUsername(String username)
	{
		// TODO Don't update if user entered something manually.
		this.username.setText(username);
	}

	public boolean shouldCreateRepo()
	{
		return publishToGithub.getSelection();
	}

	public Job createRepo(final IProject project)
	{
		final String repoName = this.repoName.getText();
		final boolean privateRepo = this.privateRepo.getSelection();
		Job job = new Job("Creating Github Repo")
		{
			@Override
			public IStatus run(IProgressMonitor monitor)
			{
				SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
				if (subMonitor.isCanceled())
					return Status.CANCEL_STATUS;

				try
				{
					String repoAddress = api.createRepo(repoName, privateRepo, subMonitor.newChild(30));

					// Initialize a git repo...
					final GitRepository repo = getGitRepositoryManager().createOrAttach(project,
							subMonitor.newChild(20));

					// Stage everything
					repo.index().stageFiles(repo.index().changedFiles());
					subMonitor.worked(5);

					// Commit
					repo.index().commit("Initial commit");
					subMonitor.worked(5);

					final String remoteName = "origin"; //$NON-NLS-1$
					final String localBranchName = "master"; //$NON-NLS-1$

					// Add Github remote, TODO Add to model
					Map<Integer, String> result = GitExecutable.instance().runInBackground(repo.workingDirectory(),
							"remote", "add", remoteName, repoAddress); //$NON-NLS-1$ //$NON-NLS-2$
					if (result == null)
					{
						throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(),
								"Failed to add github remote to git repository"));
					}
					// Non-zero exit code!
					if (result.keySet().iterator().next() != 0)
					{
						throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), result
								.values().iterator().next()));
					}
					subMonitor.worked(5);

					// push origin master TODO Add to model?
					result = GitExecutable.instance().runInBackground(repo.workingDirectory(),
							"push", remoteName, localBranchName); //$NON-NLS-1$
					if (result == null)
					{
						throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(),
								"Failed to push to github remote"));
					}
					// Non-zero exit code!
					if (result.keySet().iterator().next() != 0)
					{
						throw new CoreException(new Status(IStatus.ERROR, RailsUIPlugin.getPluginIdentifier(), result
								.values().iterator().next()));
					}
					repo.firePushEvent();
					subMonitor.worked(25);

					repo.addRemoteTrackingBranch(localBranchName, remoteName);
					subMonitor.worked(10);
				}
				catch (CoreException e)
				{
					return e.getStatus();
				}
				finally
				{
					subMonitor.done();
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.setPriority(Job.SHORT);
		return job;
	}

	protected IGitRepositoryManager getGitRepositoryManager()
	{
		return GitPlugin.getDefault().getGitRepositoryManager();
	}
}
