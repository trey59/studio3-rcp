package com.aptana.github;

import java.util.List;
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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.aptana.git.core.GitPlugin;
import com.aptana.git.core.model.ChangedFile;
import com.aptana.git.core.model.GitExecutable;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.core.model.IGitRepositoryManager;
import com.aptana.usage.PingStartup;

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
	/**
	 * Track auto-inserted repo and usernames, so if user edits the values at all we don't overwrite and insert new auto
	 * values.
	 */
	private String autoRepoName = ""; //$NON-NLS-1$
	private String autoUsername = ""; //$NON-NLS-1$

	public GithubComposite(WizardPage page, Composite parent, int style)
	{
		super(parent, style);
		this.page = page;
		createControls();
	}

	private void createControls()
	{
		this.setLayout(new GridLayout(1, false));

		Button manageSourceWithGit = new Button(this, SWT.CHECK);
		manageSourceWithGit.setText(Messages.GithubComposite_ManageSourceLabel);
		manageSourceWithGit.setSelection(true); // TODO Only show the following items if this is checked, we need to
												// listen for selection
		String githubUser = GithubAPI.getConfiguredUsername();
		String githubPassword = GithubAPI.getStoredPassword();

		// Login/Sign up
		signupLoginComp = new Composite(this, SWT.NONE);
		signupLoginComp.setLayout(new GridLayout(1, false));
		signupLoginComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Browser browser = new Browser(signupLoginComp, SWT.NONE);
		browser.setUrl(getSignupURL());

		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = TEXT_FIELD_WIDTH + 100;
		gd.heightHint = 150;
		browser.setLayoutData(gd);

		// Add way for user to enter existing credentials
		Label useExistingLabel = new Label(signupLoginComp, SWT.NONE);
		useExistingLabel.setText(Messages.GithubComposite_LoginLabel);

		Composite credentialsComp = new Composite(signupLoginComp, SWT.NONE);
		credentialsComp.setLayout(new GridLayout(2, false));
		credentialsComp.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
		// username
		Label userNameLabel = new Label(credentialsComp, SWT.NONE);
		userNameLabel.setText(Messages.GithubComposite_UsernameLabel);

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
		passwordLabel.setText(Messages.GithubComposite_TokenLabel);

		password = new Text(credentialsComp, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
		password.setLayoutData(data);

		if (githubPassword != null && githubPassword.trim().length() > 0)
		{
			password.setText(githubPassword);
		}

		Button credentialsButton = new Button(credentialsComp, SWT.PUSH);
		credentialsButton.setText(Messages.GithubComposite_SubmitButtonLabel);

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
					api = newAPI;
					api.storeCredentials();
					// Yay everything's hunky-dory
					setErrorMessage(null);
					setLoginVisible(false);
					setPublishControlsVisible(true);
				}
				else
				{
					setErrorMessage(status.getMessage());
				}
			}
		});

		// Signed in, publish to github
		publishComp = new Composite(this, SWT.NONE);
		publishComp.setLayout(new GridLayout(1, false));
		publishComp.setLayoutData(new GridData(GridData.FILL_BOTH));

		publishToGithub = new Button(publishComp, SWT.CHECK);
		publishToGithub.setText(Messages.GithubComposite_PublishLabel);
		publishToGithub.setSelection(true);

		Composite githubProjectComp = new Composite(publishComp, SWT.NONE);
		githubProjectComp.setLayout(new GridLayout(2, false));
		githubProjectComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		repoName = new Text(githubProjectComp, SWT.BORDER | SWT.SINGLE);

		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = TEXT_FIELD_WIDTH;
		repoName.setLayoutData(data);

		privateRepo = new Button(githubProjectComp, SWT.CHECK);
		privateRepo.setText(Messages.GithubComposite_PrivateLabel);

		// Do this async so we don't hang the UI!
		api = new GithubAPI(githubUser, githubPassword);
		setPublishControlsVisible(false);
		// setLoginVisible(false);
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
		{
			@Override
			public void run()
			{
				if (api.authenticate().isOK())
				{
					setPublishControlsVisible(true);
					setLoginVisible(false);
				}
				else
				{
					setPublishControlsVisible(false);
					setLoginVisible(true);
				}
			}
		});
	}

	private String getSignupURL()
	{
		// TODO URL encode the app id?
		return "http://github.com/plans?referral_code=aptana&request_id=" + PingStartup.getApplicationId(); //$NON-NLS-1$
	}

	protected void setPublishControlsVisible(boolean visible)
	{
		publishComp.setVisible(visible);
		publishToGithub.setSelection(visible);
		((GridData) publishComp.getLayoutData()).exclude = !visible;
		resize();
		updatePrivateEnablement();
	}

	private void updatePrivateEnablement()
	{
		if (api.userCanCreatePrivateRepo())
		{
			privateRepo.setEnabled(true);
		}
		else
		{
			privateRepo.setEnabled(false);
			privateRepo.setSelection(false);
		}
	}

	protected void setLoginVisible(boolean visible)
	{
		signupLoginComp.setVisible(visible);
		((GridData) signupLoginComp.getLayoutData()).exclude = !visible;
		resize();
	}

	private void resize()
	{
		Composite parent = getParent().getParent();
		int width = parent.getSize().x;
		Point p = parent.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		parent.setSize(width, p.y);
	}

	protected void setErrorMessage(String message)
	{
		page.setErrorMessage(message);
	}

	public void updateProjectName(String projectName)
	{
		if (this.repoName != null && !this.repoName.isDisposed())
		{
			if (repoName.getText().equals(autoRepoName))
			{
				repoName.setText(projectName);
				autoRepoName = projectName;
			}
		}
	}

	public void updateUsername(String username)
	{
		if (this.username != null && !this.username.isDisposed())
		{
			if (this.username.getText().equals(autoUsername))
			{
				this.username.setText(username);
				autoUsername = username;
			}
		}
	}

	public boolean shouldCreateRepo()
	{
		return publishToGithub.getSelection();
	}

	public Job createRepo(final IProject project)
	{
		final String repoName = this.repoName.getText();
		final boolean privateRepo = this.privateRepo.getSelection();
		Job job = new Job(Messages.GithubComposite_CreatingGithubRepositoryJobName)
		{
			@Override
			public IStatus run(IProgressMonitor monitor)
			{
				SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
				if (subMonitor.isCanceled())
					return Status.CANCEL_STATUS;
				subMonitor.setTaskName("Creating Github repository");

				try
				{
					String repoAddress = api.createRepo(repoName, privateRepo, subMonitor.newChild(30));

					// Initialize a git repo...
					subMonitor.subTask("Initializing local git repository on project");
					final GitRepository repo = getGitRepositoryManager().createOrAttach(project,
							subMonitor.newChild(20));

					// Stage everything
					List<ChangedFile> changedFiles = repo.index().changedFiles();
					if (changedFiles.isEmpty())
					{
						// FIXME this seems to all run too fast sometimes and we don't end up committing everything!
						subMonitor.subTask("Refreshing git index");
						repo.index().refresh(subMonitor.newChild(5));
						changedFiles = repo.index().changedFiles();
					}
					subMonitor.setWorkRemaining(45);
					subMonitor.subTask("Staging all changed files");
					repo.index().stageFiles(changedFiles);
					subMonitor.worked(5);

					// Commit
					subMonitor.subTask("Comitting files to local git repository");
					repo.index().commit("Initial commit"); //$NON-NLS-1$
					subMonitor.worked(5);

					final String remoteName = "origin"; //$NON-NLS-1$
					final String localBranchName = "master"; //$NON-NLS-1$

					// Add Github remote, TODO Add to model
					subMonitor.subTask("Adding Github as a remote");
					Map<Integer, String> result = GitExecutable.instance().runInBackground(repo.workingDirectory(),
							"remote", "add", remoteName, repoAddress); //$NON-NLS-1$ //$NON-NLS-2$
					if (result == null)
					{
						throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(),
								Messages.GithubComposite_FailedToAddRemote_Error));
					}
					// Non-zero exit code!
					if (result.keySet().iterator().next() != 0)
					{
						throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(), result
								.values().iterator().next()));
					}
					subMonitor.worked(5);

					// push origin master TODO Add to model?
					subMonitor.subTask("Pushing to GitHub");
					result = GitExecutable.instance().runInBackground(repo.workingDirectory(),
							"push", remoteName, localBranchName); //$NON-NLS-1$
					if (result == null)
					{
						throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(),
								Messages.GithubComposite_FailedToPush_Error));
					}
					// Non-zero exit code!
					if (result.keySet().iterator().next() != 0)
					{
						throw new CoreException(new Status(IStatus.ERROR, Activator.getPluginIdentifier(), result
								.values().iterator().next()));
					}
					repo.firePushEvent();
					subMonitor.worked(25);

					subMonitor.subTask("Setting up GitHub remote as tracking branch for master");
					repo.addRemoteTrackingBranch(localBranchName, remoteName);
					subMonitor.worked(5);
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
