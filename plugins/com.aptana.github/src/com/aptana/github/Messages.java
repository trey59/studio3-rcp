package com.aptana.github;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
	private static final String BUNDLE_NAME = "com.aptana.github.messages"; //$NON-NLS-1$
	public static String GithubComposite_CreatingGithubRepositoryJobName;
	public static String GithubComposite_FailedToAddRemote_Error;
	public static String GithubComposite_FailedToPush_Error;
	public static String GithubComposite_LoginLabel;
	public static String GithubComposite_ManageSourceLabel;
	public static String GithubComposite_PrivateLabel;
	public static String GithubComposite_PublishLabel;
	public static String GithubComposite_SubmitButtonLabel;
	public static String GithubComposite_TokenLabel;
	public static String GithubComposite_UsernameLabel;
	static
	{
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages()
	{
	}
}
