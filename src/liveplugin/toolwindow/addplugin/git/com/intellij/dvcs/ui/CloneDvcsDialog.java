package liveplugin.toolwindow.addplugin.git.com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.ArrayUtil;
import liveplugin.LivePluginAppComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * This is a fork of {@link com.intellij.dvcs.ui.CloneDvcsDialog}.
 * The reason for fork is to disable "parent directory" and "directory name" text fields.
 */
public abstract class CloneDvcsDialog extends DialogWrapper {
	/**
	 * The pattern for SSH URL-s in form [user@]host:path
	 */
	private static final Pattern SSH_URL_PATTERN;

	static {
		// TODO make real URL pattern
		@NonNls final String ch = "[\\p{ASCII}&&[\\p{Graph}]&&[^@:/]]";
		@NonNls final String host = ch + "+(?:\\." + ch + "+)*";
		@NonNls final String path = "/?" + ch + "+(?:/" + ch + "+)*/?";
		@NonNls final String all = "(?:" + ch + "+@)?" + host + ":" + path;
		SSH_URL_PATTERN = Pattern.compile(all);
	}

	private JPanel myRootPanel;
	private EditorComboBox myRepositoryURL;
	private TextFieldWithBrowseButton myParentDirectory;
	private JButton myTestButton; // test repository
	private JTextField myDirectoryName;
	private JLabel myRepositoryUrlLabel;

	@NotNull private String myTestURL; // the repository URL at the time of the last test
	@Nullable private Boolean myTestResult; // the test result of the last test or null if not tested
	@NotNull private String myDefaultDirectoryName = "";
	@NotNull protected final Project myProject;
	@NotNull protected final String myVcsDirectoryName;
	@Nullable private final String myDefaultRepoUrl;

	public CloneDvcsDialog(@NotNull Project project, @NotNull String displayName, @NotNull String vcsDirectoryName) {
		this(project, displayName, vcsDirectoryName, null);
	}

	public CloneDvcsDialog(@NotNull Project project, @NotNull String displayName, @NotNull String vcsDirectoryName, @Nullable String defaultUrl) {
		super(project, true);
		myDefaultRepoUrl = defaultUrl;
		myProject = project;
		myVcsDirectoryName = vcsDirectoryName;
		init();
		initListeners();
		setTitle(DvcsBundle.getString("clone.title"));
		myRepositoryUrlLabel.setText("Clone Repository URL"); // FORK DIFF
		myRepositoryUrlLabel.setDisplayedMnemonic('R');
		setOKButtonText(DvcsBundle.getString("clone.button"));

		FrameStateManager.getInstance().addListener(new FrameStateListener.Adapter() {
			@Override
			public void onFrameActivated() {
				updateButtons();
			}
		}, getDisposable());
	}

	@Override
	protected void doOKAction() {
		File parent = new File(getParentDirectory());
		if (parent.exists() && parent.isDirectory() && parent.canWrite() || parent.mkdirs()) {
			super.doOKAction();
			return;
		}
		setErrorText("Couldn't create " + parent + "<br/>Check your access rights");
		setOKActionEnabled(false);
	}

	@NotNull
	public String getSourceRepositoryURL() {
		return getCurrentUrlText();
	}

	public String getParentDirectory() {
		return myParentDirectory.getText();
	}

	public String getDirectoryName() {
		return myDirectoryName.getText();
	}

	/**
	 * Init components
	 */
	private void initListeners() {
		FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
		fcd.setShowFileSystemRoots(true);
		fcd.setTitle(DvcsBundle.getString("clone.destination.directory.title"));
		fcd.setDescription(DvcsBundle.getString("clone.destination.directory.description"));
		fcd.setHideIgnored(false);
		myParentDirectory.addActionListener(
				new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(fcd.getTitle(), fcd.getDescription(), myParentDirectory,
						myProject, fcd, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
					@Override
					protected VirtualFile getInitialFile() {
						// suggest project base directory only if nothing is typed in the component.
						String text = getComponentText();
						if (text.length() == 0) {
							VirtualFile file = myProject.getBaseDir();
							if (file != null) {
								return file;
							}
						}
						return super.getInitialFile();
					}
				}
		);

		final DocumentListener updateOkButtonListener = new DocumentAdapter() {
			@Override
			protected void textChanged(DocumentEvent e) {
				updateButtons();
			}
		};
		myParentDirectory.getChildComponent().getDocument().addDocumentListener(updateOkButtonListener);
		// FORK DIFF ↓↓↓
//		String parentDir = getRememberedInputs().getCloneParentDir();
//		if (StringUtil.isEmptyOrSpaces(parentDir)) {
//			parentDir = ProjectUtil.getBaseDir();
//		}
	  //noinspection ConstantIfStatement
	  myParentDirectory.setText(LivePluginAppComponent.pluginsRootPath());
		// FORK DIFF ↑↑↑

		myDirectoryName.getDocument().addDocumentListener(updateOkButtonListener);

		myTestButton.addActionListener(e -> test());

		setOKActionEnabled(false);
		myTestButton.setEnabled(false);
	}

	private void test() {
		myTestURL = getCurrentUrlText();
		TestResult testResult = ProgressManager.getInstance().runProcessWithProgressSynchronously(
				() -> test(myTestURL), DvcsBundle.message("clone.testing", myTestURL), true, myProject);
		if (testResult.isSuccess()) {
			Messages.showInfoMessage(myTestButton, DvcsBundle.message("clone.test.success.message", myTestURL),
					DvcsBundle.getString("clone.test.connection.title"));
			myTestResult = Boolean.TRUE;
		}
		else {
			Messages.showErrorDialog(myProject, assertNotNull(testResult.getError()), "Repository Test Failed");
			myTestResult = Boolean.FALSE;
		}
		updateButtons();
	}

	@NotNull
	protected abstract TestResult test(@NotNull String url);

	@NotNull
	protected abstract DvcsRememberedInputs getRememberedInputs();

	/**
	 * Check fields and display error in the wrapper if there is a problem
	 */
	private void updateButtons() {
		if (!checkRepositoryURL()) {
			return;
		}
		if (!checkDestination()) {
			return;
		}
		setErrorText(null);
		setOKActionEnabled(true);
	}

	/**
	 * Check destination directory and set appropriate error text if there are problems
	 *
	 * @return true if destination components are OK.
	 */
	private boolean checkDestination() {
		if (myParentDirectory.getText().length() == 0 || myDirectoryName.getText().length() == 0) {
			setErrorText(null);
			setOKActionEnabled(false);
			return false;
		}
		File file = new File(myParentDirectory.getText(), myDirectoryName.getText());
		if (file.exists() && (!file.isDirectory()) || !ArrayUtil.isEmpty(file.list())) {
			setErrorText(DvcsBundle.message("clone.destination.exists.error", file));
			setOKActionEnabled(false);
			return false;
		}
		return true;
	}

	/**
	 * Check repository URL and set appropriate error text if there are problems
	 *
	 * @return true if repository URL is OK.
	 */
	private boolean checkRepositoryURL() {
		String repository = getCurrentUrlText();
		if (repository.length() == 0) {
			setErrorText(null);
			setOKActionEnabled(false);
			return false;
		}
		if (myTestResult != null && repository.equals(myTestURL)) {
			if (!myTestResult.booleanValue()) {
				setErrorText(DvcsBundle.getString("clone.test.failed.error"));
				setOKActionEnabled(false);
				return false;
			}
			else {
				return true;
			}
		}
		try {
			if (new URI(repository).isAbsolute()) {
				return true;
			}
		}
		catch (URISyntaxException urlExp) {
			// do nothing
		}
		// check if ssh url pattern
		if (SSH_URL_PATTERN.matcher(repository).matches()) {
			return true;
		}
		try {
			File file = new File(repository);
			if (file.exists()) {
				if (!file.isDirectory()) {
					setErrorText(DvcsBundle.getString("clone.url.is.not.directory.error"));
					setOKActionEnabled(false);
				}
				return true;
			}
		}
		catch (Exception fileExp) {
			// do nothing
		}
		setErrorText(DvcsBundle.getString("clone.invalid.url"));
		setOKActionEnabled(false);
		return false;
	}

	@NotNull
	private String getCurrentUrlText() {
		return FileUtil.expandUserHome(myRepositoryURL.getText().trim());
	}

	private void createUIComponents() {
		myRepositoryURL = new EditorComboBox("");
		final DvcsRememberedInputs rememberedInputs = getRememberedInputs();
		List<String> urls = new ArrayList<>(rememberedInputs.getVisitedUrls());
		if (myDefaultRepoUrl != null) {
			urls.add(0, myDefaultRepoUrl);
		}
		myRepositoryURL.setHistory(ArrayUtil.toObjectArray(urls, String.class));
		myRepositoryURL.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
			@Override
			public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
				// enable test button only if something is entered in repository URL
				final String url = getCurrentUrlText();
				myTestButton.setEnabled(url.length() != 0);
				if (myDefaultDirectoryName.equals(myDirectoryName.getText()) || myDirectoryName.getText().length() == 0) {
					// modify field if it was unmodified or blank
					myDefaultDirectoryName = defaultDirectoryName(url, myVcsDirectoryName);
					myDirectoryName.setText(myDefaultDirectoryName);
				}
				updateButtons();
			}
		});
	}

	public void prependToHistory(@NotNull final String item) {
		myRepositoryURL.prependItem(item);
	}

	public void rememberSettings() {
		final DvcsRememberedInputs rememberedInputs = getRememberedInputs();
		rememberedInputs.addUrl(getSourceRepositoryURL());
		rememberedInputs.setCloneParentDir(getParentDirectory());
	}

	/**
	 * Get default name for checked out directory
	 *
	 * @param url an URL to checkout
	 * @return a default repository name
	 */
	@NotNull
	private static String defaultDirectoryName(@NotNull final String url, @NotNull final String vcsDirName) {
		String nonSystemName;
		if (url.endsWith("/" + vcsDirName) || url.endsWith(File.separator + vcsDirName)) {
			nonSystemName = url.substring(0, url.length() - vcsDirName.length() - 1);
		}
		else {
			if (url.endsWith(vcsDirName)) {
				nonSystemName = url.substring(0, url.length() - vcsDirName.length());
			}
			else {
				nonSystemName = url;
			}
		}
		int i = nonSystemName.lastIndexOf('/');
		if (i == -1 && File.separatorChar != '/') {
			i = nonSystemName.lastIndexOf(File.separatorChar);
		}
		return i >= 0 ? nonSystemName.substring(i + 1) : "";
	}

	@Nullable
	@Override
	public JComponent getPreferredFocusedComponent() {
		return myRepositoryURL;
	}

	protected JComponent createCenterPanel() {
		return myRootPanel;
	}

	protected static class TestResult {
		@NotNull public static final TestResult SUCCESS = new TestResult(null);
		@Nullable private final String myErrorMessage;

		public TestResult(@Nullable String errorMessage) {
			myErrorMessage = errorMessage;
		}

		public boolean isSuccess() {
			return myErrorMessage == null;
		}

		@Nullable
		public String getError() {
			return myErrorMessage;
		}
	}
}
