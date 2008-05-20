/*******************************************************************************
 * Copyright (c) 2006, 2008 PalmSource, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Ewa Matejska (PalmSource) - initial API and implementation
 * Martin Oberhuber (Wind River) - [186773] split ISystemRegistryUI from ISystemRegistry
 * Martin Oberhuber (Wind River) - [196934] hide disabled system types in remotecdt combo
 * Yu-Fen Kuo (MontaVista) - [190613] Fix NPE in Remotecdt when RSEUIPlugin has not been loaded
 * Martin Oberhuber (Wind River) - [cleanup] Avoid using SystemStartHere in production code
 * Johann Draschwandtner (Wind River) - [231827][remotecdt]Auto-compute default for Remote path
 *******************************************************************************/

package org.eclipse.rse.internal.remotecdt;

import org.eclipse.cdt.launch.ui.CMainTab;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.window.Window;
import org.eclipse.rse.core.IRSESystemType;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.IPropertySet;
import org.eclipse.rse.files.ui.dialogs.SystemRemoteFileDialog;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.eclipse.rse.ui.actions.SystemNewConnectionAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;


public class RemoteCMainTab extends CMainTab {

	/* Labels and Error Messages */
	private static final String REMOTE_PROG_LABEL_TEXT = Messages.RemoteCMainTab_Program;
	private static final String SKIP_DOWNLOAD_BUTTON_TEXT = Messages.RemoteCMainTab_SkipDownload;
	private static final String REMOTE_PROG_TEXT_ERROR = Messages.RemoteCMainTab_ErrorNoProgram;
	private static final String CONNECTION_TEXT_ERROR = Messages.RemoteCMainTab_ErrorNoConnection;

	/* Defaults */
	private static final String REMOTE_PATH_DEFAULT = EMPTY_STRING;
	private static final boolean SKIP_DOWNLOAD_TO_REMOTE_DEFAULT = false;

	protected Button newRemoteConnectionButton;
	protected Button remoteConnectionPropertiesButton;
	protected Button remoteBrowseButton;
	protected Label  connectionLabel;
	protected Combo  connectionCombo;
	protected Label remoteProgLabel;
	protected Text remoteProgText;
	protected Button skipDownloadButton;
	protected Button useLocalPathButton;

	private static int initializedRSE = 0;  //0=not initialized; -1=initializing; 1=initialized

	SystemNewConnectionAction action = null;

	public RemoteCMainTab(boolean terminalOption) {
		super(terminalOption);
	}

	/*
     * createControl
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl
     */
    public void createControl(Composite parent) {
    	Composite comp = new Composite(parent, SWT.NONE);
		GridLayout topLayout = new GridLayout();
	    setControl(comp);
		comp.setLayout(topLayout);

		/* The RSE Connection dropdown with New button. */
		createVerticalSpacer(comp, 1);
		createRemoteConnectionGroup(comp, 4);

		/* The Project and local binary location */
		createVerticalSpacer(comp, 1);
		createProjectGroup(comp, 1);
		createExeFileGroup(comp, 1);

		/* The remote binary location and skip download option */
		createVerticalSpacer(comp, 1);
		createTargetExePathGroup(comp);
		createDownloadOption(comp);

		/* If the local binary path changes, modify the remote binary location */
		fProgText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent evt) {
				setLocalPathForRemotePath();
			}
		});

		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(),
				"org.eclipse.rse.internal.remotecdt.launchgroup"); //$NON-NLS-1$

		////No more needed according to https://bugs.eclipse.org/bugs/show_bug.cgi?id=178832
		//LaunchUIPlugin.setDialogShell(parent.getShell());
    }

	/*
	 * isValid
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#isValid
	 */
	public boolean isValid(ILaunchConfiguration config) {
		boolean retVal =  super.isValid(config);
		if(retVal == true) {
			setErrorMessage(null);
			int currentSelection = connectionCombo.getSelectionIndex();
			String connection_name = currentSelection >= 0 ? connectionCombo.getItem(currentSelection) : ""; //$NON-NLS-1$
			if(connection_name.equals("")) { //$NON-NLS-1$
				setErrorMessage(CONNECTION_TEXT_ERROR);
				retVal = false;
			}
			if(retVal) {
				String name = remoteProgText.getText().trim();
				if (name.length() == 0) {
					setErrorMessage(REMOTE_PROG_TEXT_ERROR);
					retVal = false;
				}
			}
		}
		return retVal;
	}

	protected void createRemoteConnectionGroup(Composite parent, int colSpan) {
		Composite projComp = new Composite(parent, SWT.NONE);
		GridLayout projLayout = new GridLayout();
		projLayout.numColumns = 4;
		projLayout.marginHeight = 0;
		projLayout.marginWidth = 0;
		projComp.setLayout(projLayout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = colSpan;
		projComp.setLayoutData(gd);

		connectionLabel = new Label(projComp, SWT.NONE);
		connectionLabel.setText(Messages.RemoteCMainTab_Connection);
		gd = new GridData();
		gd.horizontalSpan = 1;
		connectionLabel.setLayoutData(gd);

		connectionCombo = new Combo(projComp, SWT.DROP_DOWN | SWT.READ_ONLY);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 1;
		connectionCombo.setLayoutData(gd);
		connectionCombo.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				setDirty(true);
				updateLaunchConfigurationDialog();
				useDefaultsFromConnection();
			}
		});
		updateConnectionPulldown();

		newRemoteConnectionButton = createPushButton(projComp, Messages.RemoteCMainTab_New, null);
		newRemoteConnectionButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent evt) {
				handleNewRemoteConnectionSelected();
				updateLaunchConfigurationDialog();
				updateConnectionPulldown();
			}
		});

		remoteConnectionPropertiesButton = createPushButton(projComp, Messages.RemoteCMainTab_Properties, null);
		remoteConnectionPropertiesButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent evt) {
				handleRemoteConnectionPropertiesSelected();
			}
		});

	}

	/*
	 * createTargetExePath
	 * This creates the remote path user-editable textfield on the Main Tab.
	 */
	protected void createTargetExePathGroup(Composite parent) {
		Composite mainComp = new Composite(parent, SWT.NONE);
		GridLayout mainLayout = new GridLayout();
		mainLayout.numColumns = 2;
		mainLayout.marginHeight = 0;
		mainLayout.marginWidth = 0;
		mainComp.setLayout(mainLayout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		mainComp.setLayoutData(gd);

		remoteProgLabel = new Label(mainComp, SWT.NONE);
		remoteProgLabel.setText(REMOTE_PROG_LABEL_TEXT);
		gd = new GridData();
		gd.horizontalSpan = 2;
		remoteProgLabel.setLayoutData(gd);

		remoteProgText = new Text(mainComp, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 1;
		remoteProgText.setLayoutData(gd);
		remoteProgText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent evt) {
				updateLaunchConfigurationDialog();
			}
		});

		remoteBrowseButton = createPushButton(mainComp, Messages.RemoteCMainTab_Remote_Path_Browse_Button, null);
		remoteBrowseButton.addSelectionListener( new SelectionAdapter()  {

			public void widgetSelected(SelectionEvent evt) {
				handleRemoteBrowseSelected();
				updateLaunchConfigurationDialog();
			}
		});
	}

	/*
	 * createDownloadOption
	 * This creates the skip download check button.
	 */
	protected void createDownloadOption(Composite parent) {
		Composite mainComp = new Composite(parent, SWT.NONE);
		GridLayout mainLayout = new GridLayout();
		mainLayout.marginHeight = 0;
		mainLayout.marginWidth = 0;
		mainComp.setLayout(mainLayout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		mainComp.setLayoutData(gd);

		skipDownloadButton = createCheckButton(mainComp, SKIP_DOWNLOAD_BUTTON_TEXT);
		skipDownloadButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent evt) {
				updateLaunchConfigurationDialog();
			}
		});
		skipDownloadButton.setEnabled(true);
	}

	/*
	 * performApply
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply
	 */
	public void performApply(ILaunchConfigurationWorkingCopy config) {

		int currentSelection = connectionCombo.getSelectionIndex();
		config.setAttribute(IRemoteConnectionConfigurationConstants.ATTR_REMOTE_CONNECTION,
				 currentSelection >= 0 ? connectionCombo.getItem(currentSelection) : null);
		config.setAttribute(IRemoteConnectionConfigurationConstants.ATTR_REMOTE_PATH,
				remoteProgText.getText());
		config.setAttribute(IRemoteConnectionConfigurationConstants.ATTR_SKIP_DOWNLOAD_TO_TARGET,
				skipDownloadButton.getSelection());
		super.performApply(config);
	}

	/*
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom
	 */
	public void initializeFrom(ILaunchConfiguration config) {
		String remoteConnection = null;
		try {
			remoteConnection = config.getAttribute(IRemoteConnectionConfigurationConstants.ATTR_REMOTE_CONNECTION,
						 ""); //$NON-NLS-1$
		} catch (CoreException ce) {
			// Ignore
		}

		String[] items = connectionCombo.getItems();
		int i = 0;
		for(i = 0; i < items.length; i++)
			if(items[i].equals(remoteConnection))
				break;
		/* Select the last used connection in the connecion pulldown if it still exists. */
		if(i < items.length)
			connectionCombo.select(i);
		else if(items.length > 0)
			connectionCombo.select(0);

		super.initializeFrom(config);

		updateTargetProgFromConfig(config);
		updateSkipDownloadFromConfig(config);
	}

	protected void handleNewRemoteConnectionSelected() {
    	if (action == null)
		{
		  action = new SystemNewConnectionAction(getControl().getShell(), false, false, null);
		}

		try
		{
		  action.run();
		} catch (Exception e)
		{
			// Ignore
		}
	}
	protected IHost getCurrentConnection() {
		int currentSelection = connectionCombo.getSelectionIndex();
		String remoteConnection =  currentSelection >= 0 ? connectionCombo.getItem(currentSelection) : null;
		if(remoteConnection == null)
			return null;
		IHost[] connections = RSECorePlugin.getTheSystemRegistry().getHosts();
		int i = 0;
		for(i = 0; i < connections.length; i++)
			if(connections[i].getAliasName().equals(remoteConnection))
				break;
		return connections[i];
	}

	protected void handleRemoteBrowseSelected() {
		IHost currentConnectionSelected = getCurrentConnection();
		SystemRemoteFileDialog dlg = new SystemRemoteFileDialog(getControl().getShell(),
					Messages.RemoteCMainTab_Remote_Path_Browse_Button_Title, currentConnectionSelected);
		dlg.setBlockOnOpen(true);
		if(dlg.open() == Window.OK) {
			Object retObj = dlg.getSelectedObject();
			if(retObj instanceof IRemoteFile) {
				IRemoteFile selectedFile = (IRemoteFile) retObj;
				remoteProgText.setText(selectedFile.getAbsolutePath());
			}

		}
	}

	protected void handleRemoteConnectionPropertiesSelected() {
		class RemoteConnectionPropertyDialog extends Dialog {
			private IHost fHost;
			boolean fbLocalHost;
			private Button fSkipDownloadBtn;
			private Text fWSRoot;

	    	public RemoteConnectionPropertyDialog(Shell parentShell, String dialogTitle, IHost host) {
	    	    super(parentShell);
	    	    parentShell.setText(dialogTitle);
	    	    fHost = host;
	    	    fbLocalHost = fHost.getSystemType().isLocal();
		    }

			protected Control createDialogArea(Composite parent) {
		        // create composite
		        Composite composite = (Composite) super.createDialogArea(parent);

	            Label label = new Label(composite, SWT.WRAP);
	            label.setText(Messages.RemoteCMainTab_Properties_Location);
	            GridData data = new GridData(GridData.GRAB_HORIZONTAL
	                    | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL
	                    | GridData.VERTICAL_ALIGN_CENTER);
	            data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
	            label.setLayoutData(data);
	            label.setFont(parent.getFont());
	            fWSRoot = new Text(composite, SWT.SINGLE | SWT.BORDER);
	            fWSRoot.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
		                | GridData.HORIZONTAL_ALIGN_FILL));

	    	    fSkipDownloadBtn = new Button(composite, SWT.CHECK);
	    	    fSkipDownloadBtn.setText(Messages.RemoteCMainTab_Properties_Skip_default);
	    	    if(!fbLocalHost) {
	    	    	IPropertySet propertySet = fHost.getPropertySet(IRemoteConnectionHostConstants.PI_REMOTE_CDT);
	    	    	if (propertySet != null) {
						String value = propertySet.getPropertyValue(IRemoteConnectionHostConstants.REMOTE_WS_ROOT);
						if(value != null) {
							fWSRoot.setText(value);
						}
						fSkipDownloadBtn.setSelection(Boolean.valueOf(propertySet.getPropertyValue(IRemoteConnectionHostConstants.DEFAULT_SKIP_DOWNLOAD))
								.booleanValue());
					}
	    	    } else {
		    	    fSkipDownloadBtn.setEnabled(false);
		    	    fWSRoot.setEnabled(false);
	    	    }
		        applyDialogFont(composite);
	    	    return composite;
	    	}

		    protected void buttonPressed(int buttonId) {
		        if(!fbLocalHost && (buttonId == IDialogConstants.OK_ID)) {
					IPropertySet propertySet = fHost.getPropertySet(IRemoteConnectionHostConstants.PI_REMOTE_CDT);
					if (propertySet == null) {
						propertySet = fHost.createPropertySet(IRemoteConnectionHostConstants.PI_REMOTE_CDT);
					}
					propertySet.addProperty(IRemoteConnectionHostConstants.REMOTE_WS_ROOT, fWSRoot.getText());
					propertySet.addProperty(IRemoteConnectionHostConstants.DEFAULT_SKIP_DOWNLOAD, Boolean.toString(fSkipDownloadBtn.getSelection()));
					fHost.commit();
		        }
		        super.buttonPressed(buttonId);
		    }

		}
		IHost currentConnectionSelected = getCurrentConnection();
		RemoteConnectionPropertyDialog dlg = new RemoteConnectionPropertyDialog(getControl().getShell(),
				Messages.RemoteCMainTab_Properties_title, currentConnectionSelected);
		dlg.setBlockOnOpen(true);
		dlg.open();
	}

	private void waitForRSEInit(final Runnable callback) {
		Job initRSEJob = null;
		Job[] jobs = Job.getJobManager().find(null);
		for(int i=0; i<jobs.length; i++) {
		    if ("Initialize RSE".equals(jobs[i].getName())) { //$NON-NLS-1$
		    	initRSEJob = jobs[i];
		        break;
		    }
		}
		if (initRSEJob == null) {
			//Already initialized - we can continue right away
			callback.run();
		} else {
			//Wait until model fully restored, then fire a callback to restore state.
			//Remember current display, since we're definitely on the display thread here
			final Display display = Display.getCurrent();
			final Job fInitRSEJob = initRSEJob;
			Job waitForRestoreCompleteJob = new Job("WaitForRestoreComplete") { //$NON-NLS-1$
				protected IStatus run(IProgressMonitor monitor) {
					try {
						fInitRSEJob.join();
						display.asyncExec(callback);
					} catch(InterruptedException e) {
						return Status.CANCEL_STATUS;
					}
					return Status.OK_STATUS;
				}
			};
			waitForRestoreCompleteJob.setSystem(true);
			waitForRestoreCompleteJob.schedule();
		}
	}

	protected void updateConnectionPulldown() {
		if (initializedRSE==0) {
		    // start RSEUIPlugin to make sure the SystemRegistry is initialized.
		    boolean isRegistryActive = RSECorePlugin.isTheSystemRegistryActive();
		    if (isRegistryActive) {
				initializedRSE = 1;
		        waitForRSEInit(new Runnable() {
		        	public void run() {
		        		initializedRSE = 2;
		        		updateConnectionPulldown();
		        	}
		        });
		    }
		} else if (initializedRSE<0) {
			//initializing: nothing to do, callback will come soon
		} else {
			//already initialized
		    connectionCombo.removeAll();
			IHost[] connections = RSECorePlugin.getTheSystemRegistry().getHostsBySubSystemConfigurationCategory("shells"); //$NON-NLS-1$
			for(int i = 0; i < connections.length; i++) {
				IRSESystemType sysType = connections[i].getSystemType();
				if (sysType!=null && sysType.isEnabled()) {
					connectionCombo.add(connections[i].getAliasName());
				}
			}

			if(connections.length > 0)
				connectionCombo.select(connections.length - 1);
		}
	}

	protected void updateTargetProgFromConfig(ILaunchConfiguration config) {
		String targetPath = null;
		try {
			targetPath = config.getAttribute(IRemoteConnectionConfigurationConstants.ATTR_REMOTE_PATH,
						 REMOTE_PATH_DEFAULT);
		} catch (CoreException e) {
			// Ignore
		}
		remoteProgText.setText(targetPath);
	}

	protected void updateSkipDownloadFromConfig(ILaunchConfiguration config) {
		boolean downloadToTarget = true;
		try {
			downloadToTarget = config.getAttribute(IRemoteConnectionConfigurationConstants.ATTR_SKIP_DOWNLOAD_TO_TARGET,
							   SKIP_DOWNLOAD_TO_REMOTE_DEFAULT);
		} catch (CoreException e) {
			// Ignore for now
		}
		skipDownloadButton.setSelection(downloadToTarget);
	}

	/*
	 * setLocalPathForRemotePath
	 * This function sets the remote path text field with the value of the
	 * local executable path.
	 */
	private void setLocalPathForRemotePath() {
		String programName = fProgText.getText().trim();
		boolean bUpdateRemote = false;

		String remoteName = remoteProgText.getText().trim();
		String remoteWsRoot = getRemoteWSRoot();
		if(remoteName.length() == 0) {
			bUpdateRemote = true;
		} else if(remoteWsRoot.length() != 0){
			bUpdateRemote = remoteName.equals(remoteWsRoot);
		}

		if (programName.length() != 0 && bUpdateRemote) {
			IProject project = getCProject().getProject();
			IPath exePath = new Path(programName);
			if (!exePath.isAbsolute()) {
				exePath = project.getFile(programName).getLocation();

				IPath wsRoot = project.getWorkspace().getRoot().getLocation();
				exePath = makeRelativeToWSRootLocation(exePath, remoteWsRoot, wsRoot);
			}
			String path = exePath.toString();
			remoteProgText.setText(path);
		}
	}

	private void useDefaultsFromConnection() {
		if((remoteProgText != null) && !remoteProgText.isDisposed()) {
			String remoteName = remoteProgText.getText().trim();
			String remoteWsRoot = getRemoteWSRoot();
			if(remoteName.length() == 0) {
				remoteProgText.setText(remoteWsRoot);
			} else {
				// try to use remote path
				IPath wsRoot = getCProject().getProject().getWorkspace().getRoot().getLocation();
				IPath remotePath = makeRelativeToWSRootLocation(new Path(remoteName), remoteWsRoot, wsRoot);
				remoteProgText.setText(remotePath.toString());
			}
		}
		if((skipDownloadButton != null) && !skipDownloadButton.isDisposed()) {
			skipDownloadButton.setSelection(getDefaultSkipDownload());
		}
	}

	private IPath makeRelativeToWSRootLocation(IPath exePath, String remoteWsRoot, IPath wsRoot) {
		if(remoteWsRoot.length() != 0) {
			// use remoteWSRoot instead of Workspace Root
			if(wsRoot.isPrefixOf(exePath)) {
				return new Path(remoteWsRoot).append(exePath.removeFirstSegments(wsRoot.segmentCount()).setDevice(null));
			}
		}
		return exePath;
	}

	private String getRemoteWSRoot() {
		IHost host = getCurrentConnection();
		if(host != null) {
			IPropertySet propertySet = host.getPropertySet(IRemoteConnectionHostConstants.PI_REMOTE_CDT);
			if (propertySet != null) {
				String value = propertySet.getPropertyValue(IRemoteConnectionHostConstants.REMOTE_WS_ROOT);
				if(value != null) {
					return value;
				}
			}
		}
		return ""; //$NON-NLS-1$
	}

	private boolean getDefaultSkipDownload() {
		IHost host = getCurrentConnection();
		if(host != null) {
			IPropertySet propertySet = host.getPropertySet(IRemoteConnectionHostConstants.PI_REMOTE_CDT);
			if (propertySet != null) {
				return Boolean.valueOf(propertySet.getPropertyValue(IRemoteConnectionHostConstants.DEFAULT_SKIP_DOWNLOAD)).booleanValue();
			}
		}
		return SKIP_DOWNLOAD_TO_REMOTE_DEFAULT;
	}
}
