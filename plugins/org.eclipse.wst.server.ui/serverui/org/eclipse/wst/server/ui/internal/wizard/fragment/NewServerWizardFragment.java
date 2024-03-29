/*******************************************************************************
 * Copyright (c) 2003, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.server.ui.internal.wizard.fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.wst.server.core.*;
import org.eclipse.wst.server.core.internal.ServerWorkingCopy;
import org.eclipse.wst.server.ui.internal.ServerUIPlugin;
import org.eclipse.wst.server.ui.internal.wizard.WizardTaskUtil;
import org.eclipse.wst.server.ui.internal.wizard.page.NewServerComposite;
import org.eclipse.wst.server.ui.internal.wizard.page.NewManualServerComposite;
import org.eclipse.wst.server.ui.wizard.WizardFragment;
import org.eclipse.wst.server.ui.wizard.IWizardHandle;
import org.eclipse.wst.server.core.util.SocketUtil;
/**
 * 
 */
public class NewServerWizardFragment extends WizardFragment {
	public static final byte MODE_EXISTING = WizardTaskUtil.MODE_EXISTING;
	public static final byte MODE_DETECT = WizardTaskUtil.MODE_DETECT;
	public static final byte MODE_MANUAL = WizardTaskUtil.MODE_MANUAL;

	protected IModule module;
	protected IModuleType moduleType;
	protected String serverTypeId;
	protected NewServerComposite comp;

	protected Map<String, WizardFragment> fragmentMap = new HashMap<String, WizardFragment>();
	protected IPath runtimeLocation = null;

	public NewServerWizardFragment() {
		// do nothing
	}

	public NewServerWizardFragment(IModuleType moduleType, String serverTypeId) {
		this.moduleType = moduleType;
		this.serverTypeId = serverTypeId;
	}

	public NewServerWizardFragment(IModule module) {
		this.module = module;
	}

	public boolean hasComposite() {
		return true;
	}

	public void enter() {
		super.enter();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.wst.server.ui.internal.task.WizardTask#getWizardPage()
	 */
	public Composite createComposite(Composite parent, IWizardHandle wizard) {
		String launchMode = (String) getTaskModel().getObject(TaskModel.TASK_LAUNCH_MODE);

		if (moduleType != null || serverTypeId != null)
			comp = new NewServerComposite(parent, wizard, moduleType, serverTypeId, launchMode);
		else
			comp = new NewServerComposite(parent, wizard, module, launchMode);
		if (getTaskModel() != null)
			comp.setTaskModel(getTaskModel());
		return comp;
	}

	protected WizardFragment getWizardFragment(String typeId) {
		try {
			WizardFragment fragment = fragmentMap.get(typeId);
			if (fragment != null)
				return fragment;
		} catch (Exception e) {
			// ignore
		}
		
		WizardFragment fragment = ServerUIPlugin.getWizardFragment(typeId);
		if (fragment != null)
			fragmentMap.put(typeId, fragment);
		return fragment;
	}

	public List getChildFragments() {
		List<WizardFragment> listImpl = new ArrayList<WizardFragment>();
		createChildFragments(listImpl);
		return listImpl;
	}

	protected void createChildFragments(List<WizardFragment> list) {
		if (getTaskModel() == null)
			return;
		
		Byte b = getMode();
		if (b != null && b.byteValue() == MODE_MANUAL) {
			IRuntime runtime = (IRuntime) getTaskModel().getObject(TaskModel.TASK_RUNTIME);
			if (runtime != null && runtime instanceof IRuntimeWorkingCopy) {
				WizardFragment sub = getWizardFragment(runtime.getRuntimeType().getId());
				if (sub != null)
					list.add(sub);
			}
			
			IServerAttributes server = (IServerAttributes) getTaskModel().getObject(TaskModel.TASK_SERVER);
			if (server != null) {
				if (server.getServerType().hasServerConfiguration() && server instanceof ServerWorkingCopy) {
					ServerWorkingCopy swc = (ServerWorkingCopy) server;
					if (runtime != null && runtime.getLocation() != null && !runtime.getLocation().isEmpty()) {
						if (runtimeLocation == null || !runtimeLocation.equals(runtime.getLocation()))
							try {
								swc.importRuntimeConfiguration(runtime, null);
							} catch (CoreException ce) {
								// ignore
							}
						runtimeLocation = runtime.getLocation();
					} else
						runtimeLocation = null;
				}
				WizardFragment sub = getWizardFragment(server.getServerType().getId());
				if (sub != null)
					list.add(sub);
			}
		} else if (b != null && b.byteValue() == MODE_EXISTING) {
			/*if (comp != null) {
				IServer server = comp.getServer();
				if (server != null)
					list.add(new TasksWizardFragment());
			}*/
		}
	}

	private Byte getMode() {
		try {
			return (Byte) getTaskModel().getObject(WizardTaskUtil.TASK_MODE);
		} catch (Exception e) {
			return null;
		}
	}

	public boolean isComplete() {
		if(getServer() == null)
			return false;
		
		if(getServer().getServerType() == null)
			return false;
		
		return checkValidInput();
	}
	
	/*
	 * Checks for valid host name, server type, and server name
	 * 
	 * @return true if input is valid, false otherwise
	 */
	private boolean checkValidInput(){
		boolean isComplete = false;

		boolean supportsRemote = getServer().getServerType().supportsRemoteHosts();
		
		if(comp != null){
			Composite composite = comp.getNewManualServerComposite();
			if(composite != null && composite instanceof NewManualServerComposite){
				NewManualServerComposite manualComp = (NewManualServerComposite) composite;
				if (manualComp.isTimerRunning() || manualComp.isTimerScheduled()) {
					return false;
				}
				
				if (manualComp.isServerNameInUse()){
					return false;
				}
				
				if (manualComp.getCurrentHostname().trim().length() == 0){
					isComplete = false;
				} else if(!supportsRemote && !SocketUtil.isLocalhost(manualComp.getCurrentHostname())){
					isComplete = false;
				} else if (!manualComp.canSupportModule() ){
					isComplete = false;
				}
				else
					isComplete = true;
			}
		}
		return isComplete;
	}	

	private IServerWorkingCopy getServer() {
		try {
			return (IServerWorkingCopy) getTaskModel().getObject(TaskModel.TASK_SERVER);
		} catch (Exception e) {
			return null;
		}
	}
	
	public void performCancel(IProgressMonitor monitor) throws CoreException {
		if(comp != null) {
			comp.getNewManualServerComposite().dispose();
		}
		super.performCancel(monitor);
	}
	
	public void performFinish(IProgressMonitor monitor) throws CoreException {
		if(comp != null) {
			comp.getNewManualServerComposite().dispose();
		}
		super.performFinish(monitor);
	}
}